package com.gelox.backend.voz.dto;

import java.util.Map;
import java.util.UUID;

/**
 * Respuesta de POST /api/voz/interpretar. {@code ok=false} indica que el
 * comando no se pudo interpretar (no entendido, producto no encontrado, sin
 * pedido pendiente…): {@code textoRespuesta} trae el mensaje para el usuario y
 * no hay nada que confirmar. Cuando {@code requiereConfirmacion} es true,
 * {@code comandoId} debe reenviarse en {@link VozConfirmarRequest} antes de
 * que pasen {@code expiraEnSegundos} segundos.
 */
public record VozInterpretarResponse(
        UUID comandoId,
        boolean ok,
        String intencion,
        boolean requiereConfirmacion,
        Integer expiraEnSegundos,
        String textoRespuesta,
        Map<String, Object> datos,
        String aviso                // RF70 — p. ej. cuota de IA agotada; null si no hay nada que avisar
) {
    public VozInterpretarResponse(UUID comandoId, boolean ok, String intencion, boolean requiereConfirmacion,
                                  Integer expiraEnSegundos, String textoRespuesta, Map<String, Object> datos) {
        this(comandoId, ok, intencion, requiereConfirmacion, expiraEnSegundos, textoRespuesta, datos, null);
    }

    public VozInterpretarResponse conAviso(String texto) {
        return new VozInterpretarResponse(comandoId, ok, intencion, requiereConfirmacion, expiraEnSegundos,
                textoRespuesta, datos, texto);
    }
}
