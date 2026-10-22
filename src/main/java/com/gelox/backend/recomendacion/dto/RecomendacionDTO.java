package com.gelox.backend.recomendacion.dto;

import com.gelox.backend.entities.EstadoRecomendacion;
import com.gelox.backend.entities.OrigenRecomendacion;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Detalle de una recomendación con sus ítems ordenados por prioridad (RF55).
 */
public record RecomendacionDTO(
        UUID id,
        EstadoRecomendacion estado,
        OrigenRecomendacion origen,
        BigDecimal presupuesto,
        BigDecimal costoTotal,
        BigDecimal costoAprobado,
        Boolean confianzaBaja,
        Integer diasHistorial,
        Integer diasConVentas,
        UUID pedidoProveedorId,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        List<ItemDTO> items
) {}
