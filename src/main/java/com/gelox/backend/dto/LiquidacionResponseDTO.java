package com.gelox.backend.dto;

import com.gelox.backend.entities.TipoEsquemaLiquidacion;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/** RF66 — comprobante de liquidación. Nombres de campo del contrato de creditoService.js del frontend. */
public record LiquidacionResponseDTO(
        UUID planillaId,
        LocalDateTime fechaCierre,
        TipoEsquemaLiquidacion esquemaAplicado,
        BigDecimal valorEsquema,
        BigDecimal totalVendido,          // Σ vendidas × precio al público (o al comerciante si no hay precio al público)
        BigDecimal gananciaComerciante,   // lo que se queda el comerciante
        BigDecimal montoCobrado,          // lo que factura la distribuidora
        BigDecimal efectivoRecibido,
        BigDecimal saldo,                 // montoCobrado − efectivoRecibido
        LocalDate fechaLimitePago         // null si saldo = 0
) {}
