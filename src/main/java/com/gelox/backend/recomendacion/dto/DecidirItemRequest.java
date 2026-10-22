package com.gelox.backend.recomendacion.dto;

import com.gelox.backend.entities.DecisionItem;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Body de PATCH /api/recomendaciones/{id}/items/{itemId} (RF55).
 *
 * <ul>
 *   <li>APROBADO: acepta las cajas sugeridas.</li>
 *   <li>MODIFICADO: {@code cajas} es obligatorio; 0 equivale a rechazar.</li>
 *   <li>RECHAZADO: {@code motivo} es opcional.</li>
 * </ul>
 */
public record DecidirItemRequest(

        @NotNull(message = "Indica la decisión")
        DecisionItem decision,

        @Min(value = 0, message = "Las cajas no pueden ser negativas")
        Integer cajas,

        String motivo
) {}
