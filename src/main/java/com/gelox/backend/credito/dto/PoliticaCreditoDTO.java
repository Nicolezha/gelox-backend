package com.gelox.backend.credito.dto;

import com.gelox.backend.entities.PoliticaCredito;
import com.gelox.backend.entities.PoliticaVencimiento;

import java.math.BigDecimal;

/** RF65 — una política de crédito (por defecto o excepción). */
public record PoliticaCreditoDTO(
        BigDecimal cupo,
        Integer plazoDias,
        PoliticaVencimiento politicaVencimiento,
        boolean esExcepcion
) {
    public static PoliticaCreditoDTO from(PoliticaCredito p) {
        return new PoliticaCreditoDTO(p.getCupo(), p.getPlazoDias(), p.getPoliticaVencimiento(),
                p.getComerciante() != null);
    }
}
