package com.gelox.backend.credito.dto;

import com.gelox.backend.entities.TipoEsquemaLiquidacion;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

/** RF66 — Cuerpo de PUT /api/liquidacion/esquema. valor: % para COMISION, monto para TARIFA_FIJA. */
public record EsquemaLiquidacionRequest(
        @NotNull TipoEsquemaLiquidacion tipo,
        @NotNull @PositiveOrZero BigDecimal valor
) {}
