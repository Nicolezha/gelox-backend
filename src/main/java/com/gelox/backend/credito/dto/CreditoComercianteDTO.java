package com.gelox.backend.credito.dto;

import java.math.BigDecimal;
import java.util.UUID;

/** RF65 — GET /api/comerciantes/{id}/credito: política efectiva, deuda y planillas vencidas. */
public record CreditoComercianteDTO(
        UUID comercianteId,
        PoliticaCreditoDTO politicaEfectiva,
        BigDecimal deuda,
        long planillasVencidas,
        boolean bloqueado
) {}
