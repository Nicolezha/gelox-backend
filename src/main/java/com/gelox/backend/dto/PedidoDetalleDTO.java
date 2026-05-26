package com.gelox.backend.dto;

import com.gelox.backend.entities.PedidoProveedor;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Detalle completo de un pedido al proveedor, incluyendo todos sus ítems
 * (GET /api/inventario/pedidos/{id}).
 * Usado en DetallePedido.jsx para la comparación pedido vs recibido (RF22).
 */
public record PedidoDetalleDTO(

        UUID                     id,
        LocalDate                fecha,
        String                   estado,
        String                   notas,
        List<ItemPedidoDetalleDTO> items
) {
    public static PedidoDetalleDTO from(PedidoProveedor p) {
        List<ItemPedidoDetalleDTO> itemsDTO = p.getItems()
                .stream()
                .map(ItemPedidoDetalleDTO::from)
                .toList();

        return new PedidoDetalleDTO(
                p.getId(),
                p.getFecha(),
                p.getEstado() != null ? p.getEstado().name() : null,
                p.getNotas(),
                itemsDTO
        );
    }
}
