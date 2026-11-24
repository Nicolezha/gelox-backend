package com.gelox.backend.exportacion.dto;

import java.util.UUID;

/** RF67 — código con el que el proveedor conoce a un producto; {@code codigo} vacío = pendiente. */
public record HomologacionDTO(
        UUID productoId,
        String sku,
        String nombreProducto,
        String codigo
) {
}
