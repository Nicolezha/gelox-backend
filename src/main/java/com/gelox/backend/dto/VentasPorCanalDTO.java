package com.gelox.backend.dto;

import java.math.BigDecimal;

public record VentasPorCanalDTO(
        BigDecimal porcentajeVentanilla,
        BigDecimal porcentajeRural,
        BigDecimal porcentajeComerciantes
) {}