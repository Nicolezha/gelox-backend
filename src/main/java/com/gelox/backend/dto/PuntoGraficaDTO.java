package com.gelox.backend.dto;

import java.math.BigDecimal;

public record PuntoGraficaDTO(
        String etiqueta,
        BigDecimal inversion,
        BigDecimal ingresos
) {}
