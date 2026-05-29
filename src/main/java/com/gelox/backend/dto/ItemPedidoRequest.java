package com.gelox.backend.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Un producto y las cantidades por tipo de unidad a pedir al proveedor (RF21).
 */
public record ItemPedidoRequest(

        @NotNull(message = "El ID del producto es obligatorio")
        UUID productoId,

        @NotNull @Min(value = 0, message = "Las cajas no pueden ser negativas")
        Integer cantidadCajas,

        @NotNull @Min(value = 0, message = "Las unidades no pueden ser negativas")
        Integer cantidadUnidades
) {}
