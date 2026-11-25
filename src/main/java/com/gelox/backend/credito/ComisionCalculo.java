package com.gelox.backend.credito;

import com.gelox.backend.entities.TipoEsquemaLiquidacion;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * COMISION — el comerciante gana un porcentaje fijo (valorEsquema, 0-100) del
 * total vendido al público en la jornada; el resto lo factura la distribuidora.
 */
@Component
public class ComisionCalculo implements CalculoLiquidacion {

    @Override
    public TipoEsquemaLiquidacion tipo() {
        return TipoEsquemaLiquidacion.COMISION;
    }

    @Override
    public Resultado calcular(BigDecimal totalPublico, BigDecimal totalComerciante, int unidades, BigDecimal valorEsquema) {
        BigDecimal ganancia = MargenCalculo.clamp(
                totalPublico.multiply(valorEsquema).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP),
                totalPublico);
        return new Resultado(totalPublico.subtract(ganancia), ganancia);
    }
}
