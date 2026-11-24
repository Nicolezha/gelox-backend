package com.gelox.backend.credito;

import com.gelox.backend.entities.TipoEsquemaLiquidacion;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * COMISION — la distribuidora se queda un porcentaje (valorEsquema, 0-100)
 * de la venta al público; el resto es la ganancia del comerciante. Si no se
 * registró precio al público, se factura el mismo porcentaje sobre
 * precioVenta y no se calcula ganancia del comerciante.
 */
@Component
public class ComisionCalculo implements CalculoLiquidacion {

    @Override
    public TipoEsquemaLiquidacion tipo() {
        return TipoEsquemaLiquidacion.COMISION;
    }

    @Override
    public Linea calcular(BigDecimal precioVenta, BigDecimal precioPublico, int unidadesVendidas, BigDecimal valorEsquema) {
        BigDecimal unidades = BigDecimal.valueOf(unidadesVendidas);
        BigDecimal baseUnitaria = precioPublico != null ? precioPublico : precioVenta;
        BigDecimal ventaTotal = baseUnitaria.multiply(unidades);
        BigDecimal montoCobrado = ventaTotal.multiply(valorEsquema)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        BigDecimal gananciaComerciante = precioPublico == null ? null : ventaTotal.subtract(montoCobrado);
        return new Linea(montoCobrado, gananciaComerciante);
    }
}
