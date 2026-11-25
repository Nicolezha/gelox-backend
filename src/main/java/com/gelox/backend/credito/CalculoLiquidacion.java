package com.gelox.backend.credito;

import com.gelox.backend.entities.TipoEsquemaLiquidacion;

import java.math.BigDecimal;

/**
 * RF66 — calcula, para TODA la planilla, cuánto gana el comerciante y cuánto
 * factura la distribuidora. Espejo exacto de calcularLiquidacion() en
 * creditoUtils.js del frontend: COMISION y TARIFA_FIJA son agregados de toda
 * la planilla (no se pueden calcular ítem por ítem); solo MARGEN es
 * naturalmente aditivo por ítem.
 */
public interface CalculoLiquidacion {

    TipoEsquemaLiquidacion tipo();

    /**
     * @param totalPublico     Σ vendidas × (precioPublico ?? precioVenta) de toda la planilla.
     * @param totalComerciante Σ vendidas × precioVenta (precio al comerciante) de toda la planilla.
     * @param unidades         total de unidades vendidas en la planilla.
     * @param valorEsquema     valor del esquema vigente (no se usa en MARGEN).
     */
    Resultado calcular(BigDecimal totalPublico, BigDecimal totalComerciante, int unidades, BigDecimal valorEsquema);

    /** gananciaComerciante: lo que se queda el comerciante. montoCobrado: lo que factura la distribuidora. */
    record Resultado(BigDecimal montoCobrado, BigDecimal gananciaComerciante) {}
}
