package com.gelox.backend.recomendacion.dto;

import com.gelox.backend.services.PedidoProveedorService.PedidoCreado;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Respuesta de POST /api/recomendaciones/{id}/pedido (RF56, RF63).
 *
 * <p>Se genera un pedido por proveedor. {@code pedidoId} y {@code exportUrl} son los del primero,
 * para los clientes anteriores a T194; {@code pedidos} trae todos.</p>
 */
public record PedidoGeneradoDTO(UUID pedidoId, String exportUrl, List<PedidoDeProveedorDTO> pedidos) {

    /**
     * Un pedido de la respuesta. {@code total} es el costo estimado; {@code advertenciaMontoMinimo}
     * es null si el total alcanza el monto mínimo del proveedor (el pedido se crea de todos modos).
     */
    public record PedidoDeProveedorDTO(UUID pedidoId, UUID proveedorId, String proveedorNombre,
                                       BigDecimal total, String exportUrl, String advertenciaMontoMinimo) {

        public static PedidoDeProveedorDTO de(PedidoCreado p) {
            return new PedidoDeProveedorDTO(p.pedidoId(), p.proveedorId(), p.proveedorNombre(),
                    p.total(), p.exportUrl(), p.advertenciaMontoMinimo());
        }
    }

    public static PedidoGeneradoDTO de(List<PedidoCreado> creados) {
        List<PedidoDeProveedorDTO> pedidos = creados.stream().map(PedidoDeProveedorDTO::de).toList();
        PedidoDeProveedorDTO primero = pedidos.get(0);
        return new PedidoGeneradoDTO(primero.pedidoId(), primero.exportUrl(), pedidos);
    }
}
