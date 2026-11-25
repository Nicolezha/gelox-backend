package com.gelox.backend.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.util.UUID;

public record ItemDespachoRequest(
        @NotNull UUID productoId,
        @PositiveOrZero int unidades,
        @NotNull @PositiveOrZero BigDecimal precioUnitario,
        @PositiveOrZero int saldoAnterior   // unidades ya con el comerciante (devoluciones del día anterior)
) {}
