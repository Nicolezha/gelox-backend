package com.gelox.backend.gobierno.dto;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * RF71 — Fila del log para el panel (contrato del mock de T193).
 * distribuidoraNombre: la del tenant o, en operaciones del Superadministrador sobre una
 * distribuidora (tenantId null), la de la entidad afectada; null = operación de plataforma.
 * usuarioRol: el rol guardado en la fila (el que tenía al operar). detalle: el JSON tal como se guardó.
 */
public record AuditoriaFilaDTO(
        UUID id,
        LocalDateTime fechaHora,
        String ip,
        UUID tenantId,
        String distribuidoraNombre,
        UUID usuarioId,
        String usuarioNombre,
        String usuarioCorreo,
        String usuarioRol,
        String accion,
        String entidad,
        UUID entidadId,
        String detalle
) {
}
