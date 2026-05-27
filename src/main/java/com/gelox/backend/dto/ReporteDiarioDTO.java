package com.gelox.backend.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ReporteDiarioDTO(
        LocalDate fecha,
        BigDecimal ingresoVentanilla,
        BigDecimal ingresoRural,
        BigDecimal ingresoComerciantes,
        BigDecimal totalIngresos,
        long transaccionesVentanilla,
        long transaccionesRural,
        long transaccionesComerciantes,
        long totalTransacciones,
        // Variación porcentual respecto al día anterior (null si no hay datos de ayer)
        BigDecimal variacionVentanilla,
        BigDecimal variacionRural,
        BigDecimal variacionComerciantes
) {}