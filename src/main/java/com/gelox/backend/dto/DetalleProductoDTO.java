package com.gelox.backend.dto;

public record DetalleProductoDTO(
        String nombre,
        int    cantidad,
        String tipoUnidad
) {}
