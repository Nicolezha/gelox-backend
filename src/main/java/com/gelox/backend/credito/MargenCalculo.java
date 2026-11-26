package com.gelox.backend.credito;

import com.gelox.backend.entities.TipoEsquemaLiquidacion;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * MARGEN — comportamiento histórico (FP-15 §2.17.2): la distribuidora factura
 * siempre el precio al comerciante (igual que antes de RF66); la ganancia del
 * comerciante es la diferencia con el precio al público, nunca negativa.
 */
@Component
public class MargenCalculo implements CalculoLiquidacion {

    @Override
    public TipoEsquemaLiquidacion tipo() {
        return TipoEsquemaLiquidacion.MARGEN;
    }

    @Override
    public Resultado calcular(BigDecimal totalPublico, BigDecimal totalComerciante, int unidades, BigDecimal valorEsquema) {
        BigDecimal ganancia = totalPublico.subtract(totalComerciante).max(BigDecimal.ZERO);
        return new Resultado(totalComerciante, ganancia);
    }

    static BigDecimal clamp(BigDecimal ganancia, BigDecimal totalPublico) {
        if (ganancia.signum() < 0) return BigDecimal.ZERO;
        return ganancia.min(totalPublico);
    }
}
