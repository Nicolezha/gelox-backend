package com.gelox.backend.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record ProductoResponseDTO(
        UUID id,
        String codigoTecnico,
        String nombre,
        BigDecimal precioVenta,
        BigDecimal precioCosto,
        Integer stockActual,
        Integer stockMinimo,
        Integer stockMedio,
        String imagenUrl,
        String descripcion,
        String categoria,
        Boolean activo,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        Integer unidadesPorCaja
) {}