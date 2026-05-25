package com.gelox.backend.catalogo.dto;

import java.util.List;

/**
 * Envuelve una respuesta paginada con los metadatos que espera el frontend.
 */
public record PagedResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {}
