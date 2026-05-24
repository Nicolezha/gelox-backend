package com.gelox.backend.dto;

import java.util.List;
import java.util.UUID;

/**
 * Respuesta de POST /api/inventario/entradas (RF22 + RF23).
 */
public record EntradaResponseDTO(

        String mensaje,

        /** ID del pedido cerrado, o null si la entrada fue sin pedido. */
        UUID pedidoId,

        /**
         * Comparación por producto.
         * Null si no se proporcionó pedidoId en la request.
         */
        List<ComparacionItemDTO> comparacion,

        /** Resumen del stock actualizado por producto tras la entrada. */
        List<StockActualizadoDTO> stockActualizado
) {
    /** Par simple: producto + nuevo stock. */
    public record StockActualizadoDTO(UUID productoId, String nombre, int nuevoStock) {}
}
