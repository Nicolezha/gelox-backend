package com.gelox.backend.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record DevolucionItemDTO(
        @NotNull UUID detalleId,
        @Min(0) int unidadesDevueltas
) {}
