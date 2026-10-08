package com.gelox.backend.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Producto con stock que no ha tenido salidas (ventas ni despachos) en los últimos N días.
 * {@code valorInmovilizado} = stock × costo unitario; solo se expone al ADMINISTRADOR.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record InventarioMuertoDTO(
        String        id,
        String        codigoTecnico,
        String        nombre,
        Integer       stockActual,
        LocalDateTime ultimaSalida,        // null si nunca ha tenido salidas
        Long          diasSinMovimiento,
        BigDecimal    valorInmovilizado
) {}
