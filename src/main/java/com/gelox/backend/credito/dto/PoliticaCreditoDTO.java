package com.gelox.backend.credito.dto;

import com.gelox.backend.entities.PoliticaCredito;
import com.gelox.backend.entities.PoliticaVencimiento;

import java.math.BigDecimal;

/** RF65 — política de crédito por defecto de la distribuidora. */
public record PoliticaCreditoDTO(
        BigDecimal cupo,
        Integer plazoDias,
        PoliticaVencimiento politica
) {
    public static PoliticaCreditoDTO from(PoliticaCredito p) {
        return new PoliticaCreditoDTO(p.getCupo(), p.getPlazoDias(), p.getPoliticaVencimiento());
    }
}
