package com.gelox.backend.credito;

import com.gelox.backend.entities.TipoEsquemaLiquidacion;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * TARIFA_FIJA — la distribuidora factura un monto fijo por unidad vendida
 * (valorEsquema), sin importar el precio. La ganancia del comerciante es la
 * venta al público menos esa tarifa (null si no se registró precio al público).
 */
@Component
public class TarifaFijaCalculo implements CalculoLiquidacion {

    @Override
    public TipoEsquemaLiquidacion tipo() {
        return TipoEsquemaLiquidacion.TARIFA_FIJA;
    }

    @Override
    public Linea calcular(BigDecimal precioVenta, BigDecimal precioPublico, int unidadesVendidas, BigDecimal valorEsquema) {
        BigDecimal unidades = BigDecimal.valueOf(unidadesVendidas);
        BigDecimal montoCobrado = valorEsquema.multiply(unidades);
        BigDecimal gananciaComerciante = precioPublico == null
                ? null
                : precioPublico.multiply(unidades).subtract(montoCobrado);
        return new Linea(montoCobrado, gananciaComerciante);
    }
}
