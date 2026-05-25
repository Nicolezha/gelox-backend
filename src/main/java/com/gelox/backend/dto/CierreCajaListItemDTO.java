package com.gelox.backend.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record CierreCajaListItemDTO(
        UUID id,
        LocalDate fecha,
        BigDecimal montoCalculadoTotal,
        BigDecimal montoFisicoTotal,
        BigDecimal diferenciaTotal
) {}
