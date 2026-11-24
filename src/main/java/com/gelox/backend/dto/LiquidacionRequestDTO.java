package com.gelox.backend.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.util.List;

public record LiquidacionRequestDTO(
        @NotEmpty List<@Valid DevolucionItemDTO> devoluciones,
        @PositiveOrZero BigDecimal efectivoRecibido   // RF65 — opcional; si no se indica, se asume pago completo
) {}
