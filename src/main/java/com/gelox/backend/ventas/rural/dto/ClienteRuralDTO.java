package com.gelox.backend.ventas.rural.dto;

import java.util.UUID;

/**
 * RF33 — DTO de respuesta para un cliente rural.
 */
public record ClienteRuralDTO(
        UUID    id,
        String  nombre,
        String  telefono,
        String  direccion,
        String  corregimiento,
        Boolean recurrente
) {}
