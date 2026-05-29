package com.gelox.backend.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Resumen de una planilla (abierta o cerrada) de un comerciante (RF35).
 *
 * <ul>
 *   <li>{@code totalDespachado}  — unidades totales enviadas al comerciante</li>
 *   <li>{@code totalDevuelto}    — unidades que el comerciante devolvió</li>
 *   <li>{@code unidadesVendidas} — totalDespachado − totalDevuelto</li>
 *   <li>{@code ganancia}         — ingreso neto de la planilla (almacenado en {@code total_ganancia})</li>
 *   <li>{@code cerrada}          — false = abierta/pendiente, true = liquidada</li>
 * </ul>
 */
public record PlanillaResumenDTO(

        UUID       planillaId,
        LocalDate  fecha,
        int        totalDespachado,
        int        totalDevuelto,
        int        unidadesVendidas,
        BigDecimal ganancia,
        boolean    cerrada
) {
    /**
     * Construye el DTO a partir de la fila nativa devuelta por
     * {@code ComercianteRepository#findPlanillasResumen}.
     * Orden de columnas: planillaId, fecha, totalDespachado, totalDevuelto, unidadesVendidas, ganancia, cerrada.
     */
    public static PlanillaResumenDTO fromRow(Object[] row) {
        return new PlanillaResumenDTO(
                UUID.fromString(row[0].toString()),
                toLocalDate(row[1]),
                toInt(row[2]),
                toInt(row[3]),
                toInt(row[4]),
                toBigDecimal(row[5]),
                toBoolean(row[6])
        );
    }

    // ── helpers de conversión ──────────────────────────────────────────────

    private static LocalDate toLocalDate(Object value) {
        if (value instanceof java.sql.Date d)      return d.toLocalDate();
        if (value instanceof LocalDate   ld)       return ld;
        return LocalDate.parse(value.toString());
    }

    private static int toInt(Object value) {
        if (value == null) return 0;
        return ((Number) value).intValue();
    }

    private static BigDecimal toBigDecimal(Object value) {
        if (value == null) return BigDecimal.ZERO;
        if (value instanceof BigDecimal bd) return bd;
        return new BigDecimal(value.toString());
    }

    private static boolean toBoolean(Object value) {
        if (value == null) return false;
        if (value instanceof Boolean b) return b;
        return Boolean.parseBoolean(value.toString());
    }
}
