package com.gelox.backend.dto;

import com.gelox.backend.entities.ItemPedidoProveedor;

import java.util.UUID;

/**
 * Ítem dentro del detalle de un pedido (GET /api/inventario/pedidos/{id}).
 */
public record ItemPedidoDetalleDTO(

        UUID   productoId,
        String codigoTecnico,
        String nombre,
        int    cantidadCajas,
        int    cantidadUnidades,
        int    cantidadRecibida
) {
    public static ItemPedidoDetalleDTO from(ItemPedidoProveedor item) {
        return new ItemPedidoDetalleDTO(
                item.getProducto().getId(),
                item.getProducto().getCodigoTecnico(),
                item.getProducto().getNombre(),
                item.getCantidadCajas()    != null ? item.getCantidadCajas()    : 0,
                item.getCantidadUnidades() != null ? item.getCantidadUnidades() : 0,
                item.getCantidadRecibida() != null ? item.getCantidadRecibida() : 0
        );
    }
}
