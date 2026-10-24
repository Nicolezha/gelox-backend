package com.gelox.backend.recomendacion.dto;

import com.gelox.backend.entities.EstadoRecomendacion;
import com.gelox.backend.entities.OrigenRecomendacion;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Respuesta de POST /api/recomendaciones (RF54): la recomendación guardada, con sus ítems
 * ordenados por prioridad, más los productos excluidos del cálculo.
 */
public record RecomendacionGeneradaDTO(
        UUID id,
        EstadoRecomendacion estado,
        OrigenRecomendacion origen,
        BigDecimal presupuesto,
        BigDecimal costoTotal,
        BigDecimal costoAprobado,
        Boolean confianzaBaja,
        Integer diasHistorial,
        Integer diasConVentas,
        LocalDateTime createdAt,
        List<ItemDTO> items,
        List<ProductoExcluidoDTO> productosExcluidos
) {}
