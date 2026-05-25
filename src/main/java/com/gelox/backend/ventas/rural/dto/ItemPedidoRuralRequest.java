package com.gelox.backend.ventas.rural.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * RF32 — Ítem individual dentro de un pedido rural.
 * Al menos uno de cantidadCajas o cantidadUnidades debe ser > 0 (validado en el service).
 */
public record ItemPedidoRuralRequest(
        @NotNull(message = "El producto es obligatorio")
        UUID productoId,

        @Min(value = 0, message = "La cantidad de cajas no puede ser negativa")
        Integer cantidadCajas,     // default 0

        @Min(value = 0, message = "La cantidad de unidades no puede ser negativa")
        Integer cantidadUnidades   // default 0
) {
    /** Valores por defecto para nulos. */
    public int cajas()    { return cantidadCajas    != null ? cantidadCajas    : 0; }
    public int unidades() { return cantidadUnidades != null ? cantidadUnidades : 0; }
}
