package com.gelox.backend.dto;

import java.math.BigDecimal;

public record KpiDTO(
        BigDecimal ingresosDia,
        BigDecimal gananciaNeta,
        Long comerciantesActivos,
        Long totalComerciantes,
        BigDecimal variacionIngresos,   // % vs día anterior; null si no hay dato previo
        BigDecimal variacionGanancia    // % vs día anterior; null si no hay dato previo
) {}