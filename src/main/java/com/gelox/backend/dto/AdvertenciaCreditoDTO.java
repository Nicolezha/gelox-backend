package com.gelox.backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/** RF65 — advertencia devuelta al despachar cuando el comerciante excede el crédito bajo política SOLO_ALERTA. */
public record AdvertenciaCreditoDTO(
        String codigo,      // "CUPO_SUPERADO" | "PLANILLA_VENCIDA"
        String mensaje
) {
    /** FP-15 §2.17.1 llama «tipo» a este campo y el frontend lee «codigo»: se envían los dos. */
    @JsonProperty("tipo")
    public String tipo() {
        return codigo;
    }
}
