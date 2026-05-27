package com.gelox.backend.dto;

import com.gelox.backend.entities.ItemPlanilla;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Ítem de planilla enriquecido con datos del producto para la vista de impresión (RF39).
 */
public record ItemImpresionPlanillaDTO(
        UUID       id,
        UUID       productoId,
        String     productoNombre,
        String     codigoTecnico,
        String     categoria,
        int        unidadesDespachadas,
        int        unidadesDevueltas,
        int        unidadesVendidas,
        BigDecimal precioVenta,
        BigDecimal subtotal
) {
    public static ItemImpresionPlanillaDTO from(ItemPlanilla item) {
        var prod     = item.getProducto();
        int vendidas = item.getUnidadesDespachadas() - item.getUnidadesDevueltas();
        BigDecimal subtotal = item.getGanancia() != null ? item.getGanancia() : BigDecimal.ZERO;
        return new ItemImpresionPlanillaDTO(
                item.getId(),
                prod.getId(),
                prod.getNombre(),
                prod.getCodigoTecnico(),
                prod.getCategoria() != null ? prod.getCategoria().name() : null,
                item.getUnidadesDespachadas(),
                item.getUnidadesDevueltas(),
                vendidas,
                item.getPrecioVenta(),
                subtotal
        );
    }
}
