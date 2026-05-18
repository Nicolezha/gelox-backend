package com.gelox.backend.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

public record CierreCajaDTO(
        @NotNull @PositiveOrZero BigDecimal montoFisicoVentanilla,
        @NotNull @PositiveOrZero BigDecimal montoFisicoRural,
        @NotNull @PositiveOrZero BigDecimal montoFisicoComerciantes
) {}