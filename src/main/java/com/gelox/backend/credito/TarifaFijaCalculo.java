package com.gelox.backend.credito;

import com.gelox.backend.entities.TipoEsquemaLiquidacion;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * TARIFA_FIJA — el comerciante gana un valor fijo por planilla liquidada
 * (valorEsquema), sin importar cuánto vendió; el resto lo factura la distribuidora.
 */
@Component
public class TarifaFijaCalculo implements CalculoLiquidacion {

    @Override
    public TipoEsquemaLiquidacion tipo() {
        return TipoEsquemaLiquidacion.TARIFA_FIJA;
    }

    @Override
    public Resultado calcular(BigDecimal totalPublico, BigDecimal totalComerciante, int unidades, BigDecimal valorEsquema) {
        BigDecimal ganancia = unidades > 0 ? MargenCalculo.clamp(valorEsquema, totalPublico) : BigDecimal.ZERO;
        return new Resultado(totalPublico.subtract(ganancia), ganancia);
    }
}
