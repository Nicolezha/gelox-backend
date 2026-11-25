package com.gelox.backend.credito;

import com.gelox.backend.entities.TipoEsquemaLiquidacion;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * MARGEN — comportamiento histórico: la distribuidora factura el precio al
 * comerciante (igual que antes de RF66); la ganancia del comerciante es la
 * diferencia con el precio al público.
 */
@Component
public class MargenCalculo implements CalculoLiquidacion {

    @Override
    public TipoEsquemaLiquidacion tipo() {
        return TipoEsquemaLiquidacion.MARGEN;
    }

    @Override
    public Resultado calcular(BigDecimal totalPublico, BigDecimal totalComerciante, int unidades, BigDecimal valorEsquema) {
        BigDecimal ganancia = clamp(totalPublico.subtract(totalComerciante), totalPublico);
        return new Resultado(totalPublico.subtract(ganancia), ganancia);
    }

    static BigDecimal clamp(BigDecimal ganancia, BigDecimal totalPublico) {
        if (ganancia.signum() < 0) return BigDecimal.ZERO;
        return ganancia.min(totalPublico);
    }
}
