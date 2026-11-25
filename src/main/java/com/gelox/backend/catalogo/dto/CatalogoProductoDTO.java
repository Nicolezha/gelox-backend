package com.gelox.backend.catalogo.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

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
        Integer unidadesPorCaja,
        BigDecimal precioComerciente,  // precio diferenciado para planillas de comerciantes
        // RF63/RF64
        String sku,
        String marca,
        UUID proveedorId,
        String proveedorNombre,
        UUID categoriaId,
        UUID unidadBaseId,
        String unidadBaseCodigo,
        List<FactorConversionDTO> factores
) {}
