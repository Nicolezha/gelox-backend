package com.gelox.backend.dto;

import java.math.BigDecimal;

public record InversionVsIngresosDTO(
        String semana,
        BigDecimal totalInversion,
        BigDecimal totalIngresos
) {}