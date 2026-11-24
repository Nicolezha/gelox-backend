package com.gelox.backend.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record ItemCalculoRequest(
        @NotNull(message = "El productoId es obligatorio") UUID productoId,
        @NotNull @Min(value = 0, message = "Las cajas deben ser >= 0") Integer cajas,
        @NotNull @Min(value = 0, message = "Las unidades deben ser >= 0") Integer unidades,
        /** RF64 — otra unidad del producto (p. ej. BOL) y su cantidad; se pasan a la unidad base con factor_conversion. */
        String unidad,
        @Min(value = 0, message = "La cantidad de la unidad debe ser >= 0") Integer cantidadUnidad
) {
    /** Solo cajas y unidades, como hasta ahora. */
    public ItemCalculoRequest(UUID productoId, Integer cajas, Integer unidades) {
        this(productoId, cajas, unidades, null, null);
    }
}
