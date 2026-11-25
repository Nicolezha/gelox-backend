package com.gelox.backend.credito.dto;

import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

/** RF65 — POST /api/comerciantes/{id}/abonos. Se aplica a las planillas más antiguas primero. */
public record AbonoRequest(
        @Positive BigDecimal valor,
        String observacion
) {}
