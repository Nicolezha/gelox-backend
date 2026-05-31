package com.gelox.backend.dto;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Fila del historial de pedidos al proveedor (GET /api/inventario/pedidos).
 * Construida directamente desde la query nativa para evitar el problema N+1.
 */
public record PedidoResumenDTO(

        UUID      id,
        LocalDate fecha,
        String    estado,
        String    notas,
        int       totalCajas,
        int       totalUnidades
) {
    /** Suma total en valores brutos (backward-compat para KPI). */
    public int totalSolicitado() { return totalCajas + totalUnidades; }

    /**
     * Convierte una fila de la native query a DTO.
     * Orden de columnas: id::text, fecha, estado::text, notas, total_cajas::int, total_unidades::int
     */
    public static PedidoResumenDTO fromRow(Object[] row) {
        return new PedidoResumenDTO(
                UUID.fromString(row[0].toString()),
                toLocalDate(row[1]),
                row[2] != null ? row[2].toString() : null,
                row[3] != null ? row[3].toString() : null,
                row[4] != null ? ((Number) row[4]).intValue() : 0,
                row[5] != null ? ((Number) row[5]).intValue() : 0
        );
    }

    private static LocalDate toLocalDate(Object value) {
        if (value instanceof java.sql.Date d) return d.toLocalDate();
        if (value instanceof LocalDate   ld) return ld;
        return LocalDate.parse(value.toString());
    }
}
