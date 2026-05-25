package com.gelox.backend.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Un producto y la cantidad que se desea pedir al proveedor (RF21).
 */
public record ItemPedidoRequest(

        @NotNull(message = "El ID del producto es obligatorio")
        UUID productoId,

        @NotNull @Min(value = 1, message = "La cantidad solicitada debe ser al menos 1")
        Integer cantidadSolicitada
) {}
