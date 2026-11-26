package com.gelox.backend.credito.dto;

import com.gelox.backend.entities.EsquemaLiquidacion;
import com.gelox.backend.entities.TipoEsquemaLiquidacion;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** RF66 — esquema de liquidación vigente de la distribuidora. valor es null para MARGEN (no lo usa). */
public record EsquemaLiquidacionDTO(
        TipoEsquemaLiquidacion tipo,
        BigDecimal valor,
        LocalDateTime vigenteDesde
) {
    public static EsquemaLiquidacionDTO from(EsquemaLiquidacion e) {
        BigDecimal valor = e.getTipo() == TipoEsquemaLiquidacion.MARGEN ? null : e.getValor();
        return new EsquemaLiquidacionDTO(e.getTipo(), valor, e.getVigenteDesde());
    }
}
