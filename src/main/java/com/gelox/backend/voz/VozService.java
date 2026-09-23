package com.gelox.backend.voz;

import com.gelox.backend.entities.ComandoVoz;
import com.gelox.backend.entities.EstadoComandoVoz;
import com.gelox.backend.entities.TipoIntencionVoz;
import com.gelox.backend.entities.Usuario;
import com.gelox.backend.repositories.ComandoVozRepository;
import com.gelox.backend.voz.dto.VozConfirmarRequest;
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
            "No entendí el comando. Intenta algo como: 'vende 2 festival', "
                    + "'cuántas paletas de festival quedan' o 'cuánto ganamos hoy'.";

    private static final String MENSAJE_SIN_HANDLER = "Esa función de voz todavía no está disponible.";

    private static final String MENSAJE_CANCELADO = "Comando cancelado.";

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

        if (resultado.intencion() == null) {
            ComandoVoz comando = guardarComando(usuario, request.texto(), request.confianza(),
                    null, EstadoComandoVoz.ERROR, MENSAJE_NO_ENTENDIDO);
            return new VozInterpretarResponse(comando.getId(), false, null, false, null, MENSAJE_NO_ENTENDIDO, Map.of());
        }

        IntencionHandler handler = handlersPorTipo.get(resultado.intencion());
        if (handler == null) {
            ComandoVoz comando = guardarComando(usuario, request.texto(), request.confianza(),
                    resultado.intencion(), EstadoComandoVoz.ERROR, MENSAJE_SIN_HANDLER);
            return new VozInterpretarResponse(
                    comando.getId(), false, resultado.intencion().name(), false, null, MENSAJE_SIN_HANDLER, Map.of());
        }

        LocalDate hoy = LocalDate.now(ZONA_BOGOTA);
        VozContexto ctx = new VozContexto(request.texto(), resultado.slots(), request.confianza(), usuario, hoy);
        VozResultado interpretado = handler.interpretar(ctx);

        if (!interpretado.ok()) {
            ComandoVoz comando = guardarComando(usuario, request.texto(), request.confianza(),
                    resultado.intencion(), EstadoComandoVoz.ERROR, interpretado.textoRespuesta());
            return new VozInterpretarResponse(comando.getId(), false, resultado.intencion().name(), false, null,
                    interpretado.textoRespuesta(), interpretado.datos());
        }

        boolean necesitaConfirmacion = handler.requiereConfirmacion() || request.confianza() < UMBRAL_CONFIANZA;

        if (necesitaConfirmacion) {
            UUID comandoId = UUID.randomUUID();
            DatosPendientes datos = new DatosPendientes(request.texto(), request.confianza(), interpretado.payload());
            pendienteStore.put(comandoId, usuario.getId(), resultado.intencion(), datos);

            return new VozInterpretarResponse(comandoId, true, resultado.intencion().name(), true,
                    VozPendienteStore.TTL_SEGUNDOS, interpretado.textoRespuesta(), interpretado.datos());
        }

        ComandoVoz comando = guardarComando(usuario, request.texto(), request.confianza(),
                resultado.intencion(), EstadoComandoVoz.PROCESADO, interpretado.textoRespuesta());
        return new VozInterpretarResponse(comando.getId(), true, resultado.intencion().name(), false, null,
                interpretado.textoRespuesta(), interpretado.datos());
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
