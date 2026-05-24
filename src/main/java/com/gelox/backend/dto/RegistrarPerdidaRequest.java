package com.gelox.backend.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Body de POST /api/inventario/perdidas (RF25).
 * La pérdida NO afecta ingresos ni utilidad; solo descuenta stock
 * y queda registrada en la tabla {@code perdida}.
 */
public record RegistrarPerdidaRequest(

        @NotNull(message = "El ID del producto es obligatorio")
        UUID productoId,

        @NotNull @Min(value = 1, message = "La cantidad perdida debe ser al menos 1")
        Integer cantidad,

        @NotBlank(message = "El motivo de la pérdida es obligatorio")
        String motivo,

        /**
         * Fecha de la pérdida. Si se omite, se usa la fecha actual.
         */
        LocalDate fecha,

        /** Detalles adicionales opcionales. */
        String observaciones
) {}
