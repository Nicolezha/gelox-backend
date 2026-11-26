package com.gelox.backend.credito.dto;

import com.gelox.backend.entities.PoliticaVencimiento;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

/** RF65 — PUT /api/credito/politica y PUT /api/comerciantes/{id}/credito. */
public record PoliticaCreditoRequest(
        @NotNull @PositiveOrZero BigDecimal cupo,
        @NotNull @PositiveOrZero Integer plazoDias,
        @NotNull PoliticaVencimiento politica
) {}
