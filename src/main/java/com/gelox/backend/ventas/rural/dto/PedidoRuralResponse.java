package com.gelox.backend.ventas.rural.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * RF32 — Respuesta de creación de un pedido rural (POST /api/ventas/rural).
 * También se usa en el listado paginado (GET /api/ventas/rural).
 */
public record PedidoRuralResponse(
        UUID           ventaId,
        UUID           pedidoRuralId,
        LocalDateTime  fecha,
        BigDecimal     total,
        BigDecimal     costoEnvio,
        String         estadoEnvio,

        /** Datos del destinatario extraídos de datos_destinatario. */
        Map<String, String> destinatario,

        /** Presente solo en la respuesta de creación (POST). Null en el listado. */
        List<ItemPedidoRuralResponse> items
) {

    /** DTO anidado para cada ítem del pedido rural. */
    public record ItemPedidoRuralResponse(
            UUID       productoId,
            String     nombreProducto,
            int        cantidadCajas,
            int        cantidadUnidades,
            int        totalUnidades,
            BigDecimal precioUnitario,
            BigDecimal subtotal
    ) {}
}
