package com.gelox.backend.dto;

/** RF65 — advertencia devuelta al despachar cuando el comerciante excede el crédito bajo política SOLO_ALERTA. */
public record AdvertenciaCreditoDTO(
        String codigo,      // "CUPO_SUPERADO" | "PLANILLA_VENCIDA"
        String mensaje
) {}
