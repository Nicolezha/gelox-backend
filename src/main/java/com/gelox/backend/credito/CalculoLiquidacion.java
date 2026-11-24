package com.gelox.backend.credito;

import com.gelox.backend.entities.TipoEsquemaLiquidacion;

import java.math.BigDecimal;

/**
 * RF66 — por cada ítem liquidado, calcula cuánto factura la distribuidora al
 * comerciante (montoCobrado) y, si se registró precio al público, cuánto le
 * queda al comerciante de su venta (gananciaComerciante = ventaAlPublico -
 * montoCobrado). Cada {@link com.gelox.backend.entities.TipoEsquemaLiquidacion}
 * tiene su propia implementación; {@link CalculoLiquidacionResolver} elige la
 * que corresponde al esquema vigente.
 */
public interface CalculoLiquidacion {

    TipoEsquemaLiquidacion tipo();

    /**
     * @param precioVenta      precio al comerciante (siempre presente).
     * @param precioPublico    precio al público registrado en el despacho (puede ser null).
     * @param unidadesVendidas despachadas - devueltas.
     * @param valorEsquema     valor del esquema vigente (0 para MARGEN, % para COMISION, monto por unidad para TARIFA_FIJA).
     */
    Linea calcular(BigDecimal precioVenta, BigDecimal precioPublico, int unidadesVendidas, BigDecimal valorEsquema);

    /** gananciaComerciante es null cuando el ítem no registró precio al público. */
    record Linea(BigDecimal montoCobrado, BigDecimal gananciaComerciante) {}
}
