package com.gelox.backend.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;
import java.util.UUID;

/**
 * Body de POST /api/inventario/entradas (RF22 + RF23).
 *
 * <ul>
 *   <li>Si {@code pedidoId} está presente → compara cantidades (RF22) y cierra el pedido.</li>
 *   <li>Siempre → actualiza stock y registra movimiento_inventario ENTRADA (RF23).</li>
 * </ul>
 */
public record RegistrarEntradaRequest(

        /** Pedido previo al que se asocia la entrada. Null = entrada directa sin pedido. */
        UUID pedidoId,

        @NotEmpty(message = "Debe incluir al menos un producto recibido")
        @Valid
        List<ItemEntradaRequest> items
) {}
