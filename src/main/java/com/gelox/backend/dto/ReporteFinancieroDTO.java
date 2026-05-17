package com.gelox.backend.dto;

import java.math.BigDecimal;

public record ReporteFinancieroDTO(
        BigDecimal totalInversion,
        BigDecimal ingresosVentanilla,
        BigDecimal ingresosRural,
        BigDecimal ingresosComerciantes,
        BigDecimal ingresosTotales,
        BigDecimal utilidadNeta,
        BigDecimal margenGanancia  // porcentaje: utilidad/inversion*100, null si inversion=0
) {}
