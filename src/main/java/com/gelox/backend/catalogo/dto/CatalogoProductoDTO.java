package com.gelox.backend.catalogo.dto;

import java.math.BigDecimal;

public record CatalogoProductoDTO(
        String id,
        String codigoTecnico,
        String nombre,
        String categoria,
        BigDecimal precioVenta,
        BigDecimal precioCosto,   // null si el usuario no es ADMINISTRADOR
        String descripcion,
        Integer stockMinimo,
        Integer stockMedio,
        Integer stockActual,
        String imagenUrl,
        String unidadMedida,
        Integer unidadesPorCaja
) {}
