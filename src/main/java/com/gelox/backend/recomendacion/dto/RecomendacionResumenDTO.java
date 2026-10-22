package com.gelox.backend.recomendacion.dto;

import com.gelox.backend.entities.EstadoRecomendacion;
import com.gelox.backend.entities.OrigenRecomendacion;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Fila del historial de recomendaciones (RF57).
 */
public record RecomendacionResumenDTO(
        UUID id,
        LocalDateTime createdAt,
        EstadoRecomendacion estado,
        OrigenRecomendacion origen,
        BigDecimal presupuesto,
        BigDecimal costoTotal,
        BigDecimal costoAprobado,
        Boolean confianzaBaja,
        UUID pedidoProveedorId,
        int totalItems,
        long aprobados,
        long modificados,
        long rechazados,
        long pendientes
) {}
