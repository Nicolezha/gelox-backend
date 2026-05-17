package com.gelox.backend.dto;

import java.math.BigDecimal;

public record RentabilidadCanalDTO(
        String canal,
        BigDecimal totalIngresos,
        BigDecimal totalCostos,
        BigDecimal margen  // porcentaje
) {}
