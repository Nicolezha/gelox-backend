package com.gelox.backend.dto;

import com.gelox.backend.entities.ItemPlanilla;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Desglose por producto de una planilla cerrada (RF36-39).
 */
public record ItemPlanillaDetalleDTO(
        UUID       id,
        UUID       productoId,
        String     productoNombre,
        String     codigoTecnico,
        String     categoria,
        String     imagenUrl,
        int        unidadesDespachadas,
        int        unidadesDevueltas,
        int        unidadesVendidas,
        BigDecimal precioVenta,
        BigDecimal ganancia
) {
    public static ItemPlanillaDetalleDTO from(ItemPlanilla item) {
        var prod     = item.getProducto();
        int vendidas = item.getUnidadesDespachadas() - item.getUnidadesDevueltas();
        return new ItemPlanillaDetalleDTO(
                item.getId(),
                prod.getId(),
                prod.getNombre(),
                prod.getCodigoTecnico(),
                prod.getCategoria() != null ? prod.getCategoria().name() : null,
                prod.getImagenUrl(),
                item.getUnidadesDespachadas(),
                item.getUnidadesDevueltas(),
                vendidas,
                item.getPrecioVenta(),
                item.getGanancia()
        );
    }
}
