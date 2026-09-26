package com.gelox.backend.voz;

import com.gelox.backend.entities.ComandoVoz;
import com.gelox.backend.entities.EstadoComandoVoz;
import com.gelox.backend.entities.TipoIntencionVoz;
import com.gelox.backend.entities.Usuario;
import com.gelox.backend.repositories.ComandoVozRepository;
import com.gelox.backend.voz.dto.VozConfirmarRequest;
import com.gelox.backend.voz.handlers.PendienteAclaracion;
import com.gelox.backend.voz.dto.VozConfirmarResponse;
import com.gelox.backend.voz.dto.VozInterpretarRequest;
import com.gelox.backend.voz.dto.VozInterpretarResponse;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Orquesta el flujo de voz: clasifica el texto, delega en el
 * {@link IntencionHandler} correspondiente y resuelve la confirmación.
 * <p>
 * Sin {@code @Transactional}: si {@code ejecutar} falla, igual se debe poder
 * guardar el comando con estado ERROR en lugar de perderlo por un rollback.
 */
@Service
@RequiredArgsConstructor
public class VozService {

    /** RNF-5 — por debajo de este umbral se pide confirmación aunque el handler no la exija. */
    private static final double UMBRAL_CONFIANZA = 0.70;

    private static final ZoneId ZONA_BOGOTA = ZoneId.of("America/Bogota");

    private static final String MENSAJE_NO_ENTENDIDO =
            "No entendí el comando. Intenta algo como: 'vende 2 cajas de Aloha Mango Biche', "
                    + "'cuántas Aloha Paleta Limón quedan' o 'cuánto ganamos hoy'.";

    /** "agrega ..." sin una venta esperando confirmación: no hay a qué sumarle. */
    private static final String MENSAJE_AGREGADO_SIN_VENTA =
            "No hay una venta en curso a la que agregar. Primero di la venta completa, por ejemplo "
                    + "'registra 2 cajas de Aloha Mango Biche', y luego 'agrega ...' antes de confirmar.";

    private static final String MENSAJE_SIN_HANDLER = "Esa función de voz todavía no está disponible.";

    private static final String MENSAJE_CANCELADO = "Comando cancelado.";

    /** Sobre texto normalizado: "añade" → "anade", "también" → "tambien". */
    private static final Pattern ES_AGREGADO = Pattern.compile("^(agrega|anade|tambien)\\b");

    private final List<IntencionHandler> handlers;
    private final ClasificadorIntencion clasificador;
    private final ComandoVozRepository comandoVozRepository;
    private final VozPendienteStore pendienteStore;

    private Map<TipoIntencionVoz, IntencionHandler> handlersPorTipo;

    @PostConstruct
    void indexarHandlers() {
        handlersPorTipo = handlers.stream()
                .collect(Collectors.toMap(IntencionHandler::tipo, h -> h));
    }

    /** Lo que hace falta recordar entre interpretar() y confirmar(): el payload es del handler, tal cual. */
    private record DatosPendientes(String texto, Double confianza, Object payloadHandler) {}

    public VozInterpretarResponse interpretar(VozInterpretarRequest request, Usuario usuario) {
        ClasificadorIntencion.ResultadoClasificacion resultado = clasificador.clasificar(request.texto());

        // T41-BE5 — "agrega/añade/también ..." continúa la venta que espera confirmación.
        TipoIntencionVoz intencion = resultado.intencion();
        VozPendiente ventaEnCurso = null;
        boolean agregado = false;
        // Si la venta quedó esperando una aclaración (destinatario o producto), cualquier frase sin
        // intención propia ("doña Marta", "fresa") la completa.
        if (intencion == null || intencion == TipoIntencionVoz.REGISTRAR_VENTA) {
            agregado = ES_AGREGADO.matcher(NormalizadorVoz.normalizar(request.texto())).find();
            final boolean esAgregado = agregado;
            final TipoIntencionVoz intencionClasificada = intencion;
            ventaEnCurso = pendienteStore.peekPorUsuario(usuario.getId())
                    .filter(p -> p.intencion() == TipoIntencionVoz.REGISTRAR_VENTA)
                    .filter(p -> esperaDestinatario(p) ? intencionClasificada == null : esAgregado)
                    .orElse(null);
            if (ventaEnCurso != null) intencion = TipoIntencionVoz.REGISTRAR_VENTA;
        }

        if (intencion == null) {
            String mensaje = agregado ? MENSAJE_AGREGADO_SIN_VENTA : MENSAJE_NO_ENTENDIDO;
            ComandoVoz comando = guardarComando(usuario, request.texto(), request.confianza(),
                    null, EstadoComandoVoz.ERROR, mensaje);
            return new VozInterpretarResponse(comando.getId(), false, null, false, null, mensaje, Map.of());
        }

        IntencionHandler handler = handlersPorTipo.get(intencion);
        if (handler == null) {
            ComandoVoz comando = guardarComando(usuario, request.texto(), request.confianza(),
                    intencion, EstadoComandoVoz.ERROR, MENSAJE_SIN_HANDLER);
            return new VozInterpretarResponse(
                    comando.getId(), false, intencion.name(), false, null, MENSAJE_SIN_HANDLER, Map.of());
        }

        LocalDate hoy = LocalDate.now(ZONA_BOGOTA);
        DatosPendientes datosPrevios = ventaEnCurso == null ? null : (DatosPendientes) ventaEnCurso.payload();
        VozContexto ctx;
        if (ventaEnCurso != null) {
            VozPendiente paraHandler = new VozPendiente(
                    ventaEnCurso.comandoId(), ventaEnCurso.usuarioId(), intencion,
                    datosPrevios.payloadHandler(), ventaEnCurso.expiraEn());
            ctx = new VozContexto(request.texto(), resultado.slots(), request.confianza(), usuario, hoy, paraHandler);
        } else {
            ctx = new VozContexto(request.texto(), resultado.slots(), request.confianza(), usuario, hoy);
        }
        VozResultado interpretado = handler.interpretar(ctx);

        if (!interpretado.ok()) {
            ComandoVoz comando = guardarComando(usuario, request.texto(), request.confianza(),
                    intencion, EstadoComandoVoz.ERROR, interpretado.textoRespuesta());
            return new VozInterpretarResponse(comando.getId(), false, intencion.name(), false, null,
                    interpretado.textoRespuesta(), interpretado.datos());
        }

        boolean necesitaConfirmacion = handler.requiereConfirmacion() || request.confianza() < UMBRAL_CONFIANZA;

        if (necesitaConfirmacion) {
            UUID comandoId;
            DatosPendientes datos;
            if (ventaEnCurso != null) {
                // Mismo id: put sobrescribe la entrada y reinicia el plazo.
                comandoId = ventaEnCurso.comandoId();
                datos = new DatosPendientes(datosPrevios.texto() + "; " + request.texto(),
                        Math.min(datosPrevios.confianza(), request.confianza()), interpretado.payload());
            } else {
                comandoId = UUID.randomUUID();
                datos = new DatosPendientes(request.texto(), request.confianza(), interpretado.payload());
            }
            pendienteStore.put(comandoId, usuario.getId(), intencion, datos);

            return new VozInterpretarResponse(comandoId, true, intencion.name(), true,
                    VozPendienteStore.TTL_SEGUNDOS, interpretado.textoRespuesta(), interpretado.datos());
        }

        ComandoVoz comando = guardarComando(usuario, request.texto(), request.confianza(),
                intencion, EstadoComandoVoz.PROCESADO, interpretado.textoRespuesta());
        return new VozInterpretarResponse(comando.getId(), true, intencion.name(), false, null,
                interpretado.textoRespuesta(), interpretado.datos());
    }

    private static boolean esperaDestinatario(VozPendiente pendiente) {
        return pendiente.payload() instanceof DatosPendientes datos
                && datos.payloadHandler() instanceof PendienteAclaracion;
    }

    public VozConfirmarResponse confirmar(VozConfirmarRequest request, Usuario usuario) {
        VozPendiente pendiente = pendienteStore.take(request.comandoId(), usuario.getId());
        DatosPendientes datos = (DatosPendientes) pendiente.payload();

        if (!request.confirmar()) {
            guardarComando(usuario, datos.texto(), datos.confianza(), pendiente.intencion(),
                    EstadoComandoVoz.CANCELADO, MENSAJE_CANCELADO);
            return new VozConfirmarResponse(EstadoComandoVoz.CANCELADO.name(), MENSAJE_CANCELADO, Map.of());
        }

        IntencionHandler handler = handlersPorTipo.get(pendiente.intencion());
        VozPendiente paraHandler = new VozPendiente(
                pendiente.comandoId(), pendiente.usuarioId(), pendiente.intencion(),
                datos.payloadHandler(), pendiente.expiraEn());

        try {
            VozResultado resultado = handler.ejecutar(paraHandler, usuario);
            guardarComando(usuario, datos.texto(), datos.confianza(), pendiente.intencion(),
                    EstadoComandoVoz.PROCESADO, resultado.textoRespuesta());
            return new VozConfirmarResponse(EstadoComandoVoz.PROCESADO.name(), resultado.textoRespuesta(), resultado.datos());
        } catch (RuntimeException e) {
            guardarComando(usuario, datos.texto(), datos.confianza(), pendiente.intencion(),
                    EstadoComandoVoz.ERROR, e.getMessage());
            throw e;
        }
    }

    private ComandoVoz guardarComando(Usuario usuario, String texto, double confianza, TipoIntencionVoz intencion,
                                       EstadoComandoVoz estado, String respuesta) {
        ComandoVoz comando = ComandoVoz.builder()
                .usuario(usuario)
                .textoTranscrito(texto)
                .confianza(BigDecimal.valueOf(confianza).setScale(3, RoundingMode.HALF_UP))
                .intencion(intencion)
                .estado(estado)
                .respuesta(respuesta)
                .build();
        return comandoVozRepository.save(comando);
    }
}
