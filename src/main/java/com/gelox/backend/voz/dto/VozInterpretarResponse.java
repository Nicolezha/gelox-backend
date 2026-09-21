package com.gelox.backend.voz.dto;

import java.util.Map;
import java.util.UUID;

/**
 * Respuesta de POST /api/voz/interpretar. Cuando {@code requiereConfirmacion}
 * es true, {@code comandoId} debe reenviarse en {@link VozConfirmarRequest}
 * antes de que pasen {@code expiraEnSegundos} segundos.
 */
public record VozInterpretarResponse(
        UUID comandoId,
        String intencion,
        boolean requiereConfirmacion,
        Integer expiraEnSegundos,
        String textoRespuesta,
        Map<String, Object> datos
) {}
