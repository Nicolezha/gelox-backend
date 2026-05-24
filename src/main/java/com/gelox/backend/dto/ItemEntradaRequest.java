package com.gelox.backend.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Un producto recibido físicamente en la bodega (RF22/RF23).
 */
public record ItemEntradaRequest(

        @NotNull(message = "El ID del producto es obligatorio")
        UUID productoId,

        @NotNull @Min(value = 1, message = "La cantidad recibida debe ser al menos 1")
        Integer cantidadRecibida,

        /**
         * Precio negociado con el proveedor.
         * Opcional: si se omite o es null, se usa el precio_costo del catálogo.
         */
        BigDecimal precioUnitario
) {}
