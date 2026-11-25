package com.gelox.backend.dto;

import java.math.BigDecimal;

/** RF65 — advertencia devuelta al despachar cuando el comerciante excede el crédito bajo política SOLO_ALERTA. */
public record AdvertenciaCreditoDTO(
        String mensaje,
        BigDecimal deuda,
        BigDecimal cupo,
        long planillasVencidas
) {}
