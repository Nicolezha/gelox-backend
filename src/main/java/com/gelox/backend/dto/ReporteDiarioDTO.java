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
        long totalTransacciones
) {}