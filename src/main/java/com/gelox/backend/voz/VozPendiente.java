package com.gelox.backend.voz;

import com.gelox.backend.entities.TipoIntencionVoz;

import java.time.Instant;
import java.util.UUID;

/**
 * Acción interpretada que quedó a la espera de confirmación del usuario
 * (ver {@link IntencionHandler#requiereConfirmacion()} y
 * {@link VozPendienteStore}). {@code payload} es exactamente lo que el
 * handler devolvió en {@code VozResultado.payload()} al interpretar, y es lo
 * que necesita para completar la ejecución al confirmarse.
 */
public record VozPendiente(UUID comandoId, UUID usuarioId, TipoIntencionVoz intencion, Object payload, Instant expiraEn) {
}
