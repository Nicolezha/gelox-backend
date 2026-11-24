package com.gelox.backend.gobierno.dto;

import java.math.BigDecimal;

/**
 * RF70 — POST/PUT /api/plataforma/planes. Un máximo null = sin límite.
 * PlanService valida (nombre obligatorio y único, precio y cuotas no negativos) y responde
 * {error, campo} con 400 o 409.
 */
public record PlanRequest(
        String nombre,
        String descripcion,
        BigDecimal precioMensual,
        Integer maxUsuarios,
        Integer maxProductos,
        Integer maxComerciantes,
        Integer maxAlmacenamientoMb,
        Integer maxLlamadasIaMes
) {
}
