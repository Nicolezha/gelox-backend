package com.gelox.backend.credito.dto;

import com.gelox.backend.entities.PoliticaVencimiento;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * RF65 — vista de crédito de un comerciante: política efectiva (por defecto o
 * excepción), deuda, planillas con saldo y abonos. Contrato consumido por
 * SeccionCredito.jsx / creditoService.js del frontend.
 */
public record CreditoVistaDTO(
        UUID comercianteId,
        String origen,                         // "DEFECTO" | "EXCEPCION"
        BigDecimal cupo,
        Integer plazoDias,
        PoliticaVencimiento politica,
        PoliticaDefectoDTO politicaDefecto,
        BigDecimal deudaActual,
        BigDecimal cupoDisponible,
        List<PlanillaSaldoDTO> planillas,
        List<AbonoDTO> abonos
) {
    public record PoliticaDefectoDTO(BigDecimal cupo, Integer plazoDias, PoliticaVencimiento politica) {}
}
