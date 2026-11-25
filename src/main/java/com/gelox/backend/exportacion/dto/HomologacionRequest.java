package com.gelox.backend.exportacion.dto;

import java.util.UUID;

/** RF67 — un código vacío o nulo quita la homologación del producto. */
public record HomologacionRequest(
        UUID productoId,
        String codigo
) {
}
