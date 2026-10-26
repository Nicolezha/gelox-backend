package com.gelox.backend.voz;

import com.gelox.backend.entities.ComandoVoz;
import com.gelox.backend.entities.EstadoComandoVoz;
import com.gelox.backend.entities.MetodoClasificacion;
import com.gelox.backend.entities.TipoIntencionVoz;
import com.gelox.backend.entities.Usuario;
import com.gelox.backend.recomendacion.RecomendacionCompraService;
import com.gelox.backend.repositories.ComandoVozRepository;
import com.gelox.backend.security.RequiereRol;
import com.gelox.backend.voz.dto.VozConfirmarRequest;
import com.gelox.backend.voz.handlers.PendienteAclaracion;
import com.gelox.backend.voz.handlers.RecomendacionVozHandler;
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
    /** El LLM (ProcesadorLenguajeLlm, @Primary) con respaldo en el clasificador de la Iteración 1. */
    private final ProcesadorLenguaje procesador;
    private final ClasificadorIntencion clasificador;
    private final ComandoVozRepository comandoVozRepository;
    private final VozPendienteStore pendienteStore;
    private final RecomendacionCompraService recomendacionService;

    private Map<TipoIntencionVoz, IntencionHandler> handlersPorTipo;

    @PostConstruct
    void indexarHandlers() {
        handlersPorTipo = handlers.stream()
                .collect(Collectors.toMap(IntencionHandler::tipo, h -> h));
    }

    /**
     * Lo que hace falta recordar entre interpretar() y confirmar(): el payload es del handler, tal cual.
     * probabilidadIntencion y metodo son los de la interpretación original (FP-15 §2.1).
     */
    private record DatosPendientes(String texto, Double confianza, Object payloadHandler,
                                   Double probabilidadIntencion, MetodoClasificacion metodo) {}

    @RequiereRol({"ADMINISTRADOR", "ENCARGADO_VENTAS", "ENCARGADO_INVENTARIO"})
    public VozInterpretarResponse interpretar(VozInterpretarRequest request, Usuario usuario) {
        ClasificadorIntencion.ResultadoClasificacion resultado = procesador.procesar(request.texto());
        Map<String, String> slots = resultado.slots();

        // T41-BE5 — "agrega/añade/también ..." continúa la venta que espera confirmación.
        TipoIntencionVoz intencion = resultado.intencion();
        // PLN — un agregado sin la palabra "pedido" no lo reinterpreta el modelo (podría tomarlo como
        // MODIFICAR_PEDIDO o como una venta nueva): se decide con las reglas, como antes del modelo.
        String normalizadoAgregado = NormalizadorVoz.normalizar(request.texto());
        if ((resultado.metodo() == MetodoClasificacion.MODELO || resultado.metodo() == MetodoClasificacion.LLM)
                && ES_AGREGADO.matcher(normalizadoAgregado).find()
                && !normalizadoAgregado.contains("pedido")) {
            intencion = clasificador.intencionPorReglas(request.texto());
        }
        // T115-BE3 — "genera el pedido" sin productos: con el LLM lo resuelve la regla 4 del prompt;
        // con el clasificador de respaldo se redirige a la recomendación de hoy si ya tiene aprobados.
        if (intencion == TipoIntencionVoz.GENERAR_PEDIDO
                && resultado.metodo() != MetodoClasificacion.LLM
                && !slots.containsKey("cantidad")
                && handlersPorTipo.containsKey(TipoIntencionVoz.DECIDIR_RECOMENDACION)
                && hayAprobadosHoy()) {
            intencion = TipoIntencionVoz.DECIDIR_RECOMENDACION;
            slots = Map.of("accion", RecomendacionVozHandler.Accion.GENERAR_PEDIDO.name());
        }
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
                    null, EstadoComandoVoz.ERROR, mensaje,
                    resultado.probabilidadIntencion(), resultado.metodo());
            return new VozInterpretarResponse(comando.getId(), false, null, false, null, mensaje, Map.of());
        }

        IntencionHandler handler = handlersPorTipo.get(intencion);
        if (handler == null) {
            ComandoVoz comando = guardarComando(usuario, request.texto(), request.confianza(),
                    intencion, EstadoComandoVoz.ERROR, MENSAJE_SIN_HANDLER,
                    resultado.probabilidadIntencion(), resultado.metodo());
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
            ctx = new VozContexto(request.texto(), slots, request.confianza(), usuario, hoy, paraHandler);
        } else {
            ctx = new VozContexto(request.texto(), slots, request.confianza(), usuario, hoy);
        }
        VozResultado interpretado = handler.interpretar(ctx);

        if (!interpretado.ok()) {
            ComandoVoz comando = guardarComando(usuario, request.texto(), request.confianza(),
                    intencion, EstadoComandoVoz.ERROR, interpretado.textoRespuesta(),
                    resultado.probabilidadIntencion(), resultado.metodo());
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
                        Math.min(datosPrevios.confianza(), request.confianza()), interpretado.payload(),
                        datosPrevios.probabilidadIntencion(), datosPrevios.metodo());
            } else {
                comandoId = UUID.randomUUID();
                datos = new DatosPendientes(request.texto(), request.confianza(), interpretado.payload(),
                        resultado.probabilidadIntencion(), resultado.metodo());
            }
            pendienteStore.put(comandoId, usuario.getId(), intencion, datos);

            if (!handler.requiereConfirmacion()) {
                // Consulta pendiente solo por baja confianza (RNF-5): se confirma lo entendido
                // y la respuesta llega con la confirmación, no antes.
                return new VozInterpretarResponse(comandoId, true, intencion.name(), true,
                        VozPendienteStore.ttlSegundos(intencion), "Entendí: \"" + request.texto() + "\". ¿Confirmas?", Map.of());
            }
            return new VozInterpretarResponse(comandoId, true, intencion.name(), true,
                    VozPendienteStore.ttlSegundos(intencion), interpretado.textoRespuesta(), interpretado.datos());
        }

        ComandoVoz comando = guardarComando(usuario, request.texto(), request.confianza(),
                intencion, EstadoComandoVoz.PROCESADO, interpretado.textoRespuesta(),
                    resultado.probabilidadIntencion(), resultado.metodo());
        return new VozInterpretarResponse(comando.getId(), true, intencion.name(), false, null,
                interpretado.textoRespuesta(), interpretado.datos());
    }

    private boolean hayAprobadosHoy() {
        return recomendacionService.recomendacionDelDia()
                .map(r -> !RecomendacionVozHandler.aprobados(r).isEmpty())
                .orElse(false);
    }

    private static boolean esperaDestinatario(VozPendiente pendiente) {
        return pendiente.payload() instanceof DatosPendientes datos
                && datos.payloadHandler() instanceof PendienteAclaracion;
    }

    @RequiereRol({"ADMINISTRADOR", "ENCARGADO_VENTAS", "ENCARGADO_INVENTARIO"})
    public VozConfirmarResponse confirmar(VozConfirmarRequest request, Usuario usuario) {
        VozPendiente pendiente = pendienteStore.take(request.comandoId(), usuario.getId());
        DatosPendientes datos = (DatosPendientes) pendiente.payload();

        if (!request.confirmar()) {
            guardarComando(usuario, datos.texto(), datos.confianza(), pendiente.intencion(),
                    EstadoComandoVoz.CANCELADO, MENSAJE_CANCELADO,
                    datos.probabilidadIntencion(), datos.metodo());
            return new VozConfirmarResponse(EstadoComandoVoz.CANCELADO.name(), MENSAJE_CANCELADO, Map.of());
        }

        IntencionHandler handler = handlersPorTipo.get(pendiente.intencion());
        VozPendiente paraHandler = new VozPendiente(
                pendiente.comandoId(), pendiente.usuarioId(), pendiente.intencion(),
                datos.payloadHandler(), pendiente.expiraEn());

        try {
            VozResultado resultado = handler.ejecutar(paraHandler, usuario);
            guardarComando(usuario, datos.texto(), datos.confianza(), pendiente.intencion(),
                    EstadoComandoVoz.PROCESADO, resultado.textoRespuesta(),
                    datos.probabilidadIntencion(), datos.metodo());
            return new VozConfirmarResponse(EstadoComandoVoz.PROCESADO.name(), resultado.textoRespuesta(), resultado.datos());
        } catch (RuntimeException e) {
            guardarComando(usuario, datos.texto(), datos.confianza(), pendiente.intencion(),
                    EstadoComandoVoz.ERROR, e.getMessage(),
                    datos.probabilidadIntencion(), datos.metodo());
            throw e;
        }
    }

    private ComandoVoz guardarComando(Usuario usuario, String texto, double confianza, TipoIntencionVoz intencion,
                                       EstadoComandoVoz estado, String respuesta,
                                       Double probabilidadIntencion, MetodoClasificacion metodo) {
        ComandoVoz comando = ComandoVoz.builder()
                .usuario(usuario)
                .textoTranscrito(texto)
                .confianza(BigDecimal.valueOf(confianza).setScale(3, RoundingMode.HALF_UP))
                .intencion(intencion)
                .estado(estado)
                .respuesta(respuesta)
                .probabilidadIntencion(probabilidadIntencion == null ? null
                        : BigDecimal.valueOf(probabilidadIntencion).setScale(3, RoundingMode.HALF_UP))
                .metodoClasificacion(metodo)
                .build();
        return comandoVozRepository.save(comando);
    }
}
