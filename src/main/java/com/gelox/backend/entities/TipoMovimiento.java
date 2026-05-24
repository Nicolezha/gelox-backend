package com.gelox.backend.entities;

/** Mapea el tipo PostgreSQL {@code tipo_movimiento}. */
public enum TipoMovimiento {
    ENTRADA,
    SALIDA_VENTA,
    SALIDA_DESPACHO,
    PERDIDA,
    DEVOLUCION
}
