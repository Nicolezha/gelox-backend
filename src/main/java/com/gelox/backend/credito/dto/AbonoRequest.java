package com.gelox.backend.credito.dto;

import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.UUID;

/** RF65 — POST /api/comerciantes/{id}/abonos. planillaId es opcional (abono general). */
public record AbonoRequest(
        @Positive BigDecimal valor,
        UUID planillaId,
        String observacion
) {}
