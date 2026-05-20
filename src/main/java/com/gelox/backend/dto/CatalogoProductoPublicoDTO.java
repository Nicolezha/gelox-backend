package com.gelox.backend.dto;

import java.math.BigDecimal;

public record CatalogoProductoPublicoDTO(
        String nombre,
        String imagenUrl,
        BigDecimal precioVenta,
        String descripcion,
        String categoria
) {}
