package com.gelox.backend.dto;

import com.gelox.backend.entities.TipoEsquemaLiquidacion;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record LiquidacionResponseDTO(
        UUID planillaId,
        LocalDateTime fechaCierre,
        List<DetalleCalculadoDTO> detalles,
        BigDecimal gananciaTotal,
        TipoEsquemaLiquidacion esquemaAplicado,       // RF66
        BigDecimal gananciaComercianteTotal,          // RF66 — null si ningún ítem tenía precio al público
        BigDecimal efectivoRecibido,                  // RF65
        LocalDate fechaLimitePago                     // RF65
) {}
