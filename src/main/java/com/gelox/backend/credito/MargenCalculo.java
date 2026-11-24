package com.gelox.backend.credito;

import com.gelox.backend.entities.TipoEsquemaLiquidacion;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * MARGEN — comportamiento histórico: la distribuidora factura
 * precioVenta * unidadesVendidas (el valor del esquema no se usa). La
 * ganancia del comerciante es la diferencia con el precio al público.
 */
@Component
public class MargenCalculo implements CalculoLiquidacion {

    @Override
    public TipoEsquemaLiquidacion tipo() {
        return TipoEsquemaLiquidacion.MARGEN;
    }

    @Override
    public Linea calcular(BigDecimal precioVenta, BigDecimal precioPublico, int unidadesVendidas, BigDecimal valorEsquema) {
        BigDecimal unidades = BigDecimal.valueOf(unidadesVendidas);
        BigDecimal montoCobrado = precioVenta.multiply(unidades);
        BigDecimal gananciaComerciante = precioPublico == null
                ? null
                : precioPublico.subtract(precioVenta).multiply(unidades);
        return new Linea(montoCobrado, gananciaComerciante);
    }
}
