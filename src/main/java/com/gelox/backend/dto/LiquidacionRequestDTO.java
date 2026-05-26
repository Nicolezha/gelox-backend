package com.gelox.backend.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record LiquidacionRequestDTO(
        @NotEmpty List<@Valid DevolucionItemDTO> devoluciones
) {}
