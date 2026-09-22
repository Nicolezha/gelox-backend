package com.gelox.backend.dto;

import java.util.UUID;

/**
 * RF53 — cambio a aplicar sobre un ítem de un pedido PENDIENTE.
 * AGREGAR suma cantidadCajas/cantidadUnidades al ítem existente (o crea uno nuevo);
 * ACTUALIZAR los fija como valores absolutos; ELIMINAR los ignora.
 */
public record ModificarPedidoRequest(
        AccionPedido accion,
        UUID productoId,
        Integer cantidadCajas,
        Integer cantidadUnidades
) {}
