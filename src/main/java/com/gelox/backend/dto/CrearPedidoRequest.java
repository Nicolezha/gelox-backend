package com.gelox.backend.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/**
 * Body de POST /api/inventario/pedidos (RF21).
 * El sistema registra el pedido con estado PENDIENTE y genera el Excel Nutresa.
 */
public record CrearPedidoRequest(

        @NotEmpty(message = "Debe incluir al menos un producto en el pedido")
        @Valid
        List<ItemPedidoRequest> items,

        /** Observaciones opcionales que se incluyen en el Excel y en la BD. */
        String notas
) {}
