package com.gelox.backend.credito.dto;

import com.gelox.backend.entities.TipoEsquemaLiquidacion;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

/** RF66 — Cuerpo de PUT /api/liquidacion/esquema. valor: % para COMISION, monto fijo para TARIFA_FIJA; null en MARGEN. */
public record EsquemaLiquidacionRequest(
        @NotNull TipoEsquemaLiquidacion tipo,
        @PositiveOrZero BigDecimal valor
) {}
