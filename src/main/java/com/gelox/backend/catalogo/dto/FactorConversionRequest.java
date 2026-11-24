package com.gelox.backend.catalogo.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * RF64 — 1 {@code unidadOrigenId} = {@code factor} unidades base del producto.
 * No se declara CJ: CJ → UN sale de unidadesPorCaja.
 */
public record FactorConversionRequest(
        @NotNull(message = "La unidad de origen es obligatoria")
        UUID unidadOrigenId,

        @NotNull(message = "El factor es obligatorio")
        @DecimalMin(value = "0", inclusive = false, message = "El factor debe ser mayor a 0")
        BigDecimal factor
) {}
