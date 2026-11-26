package com.gelox.backend.credito;

import com.gelox.backend.entities.TipoEsquemaLiquidacion;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class CalculoLiquidacionResolver {

    private final Map<TipoEsquemaLiquidacion, CalculoLiquidacion> estrategias;

    public CalculoLiquidacionResolver(List<CalculoLiquidacion> estrategias) {
        this.estrategias = estrategias.stream()
                .collect(Collectors.toMap(CalculoLiquidacion::tipo, Function.identity()));
    }

    public CalculoLiquidacion paraTipo(TipoEsquemaLiquidacion tipo) {
        CalculoLiquidacion estrategia = estrategias.get(tipo);
        if (estrategia == null) {
            throw new IllegalStateException("No hay estrategia de liquidación para " + tipo);
        }
        return estrategia;
    }
}
