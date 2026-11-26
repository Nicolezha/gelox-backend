package com.gelox.backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/** RF66 — aviso de una liquidación que no impide cerrarla (FP-15 §2.17.2). */
public record AdvertenciaLiquidacionDTO(
        String codigo,      // "SIN_PRECIO_PUBLICO"
        String mensaje
) {
    /** Mismo formato que las advertencias de crédito: se envía también como «tipo». */
    @JsonProperty("tipo")
    public String tipo() {
        return codigo;
    }
}
