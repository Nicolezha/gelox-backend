package com.gelox.backend.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.util.UUID;

public record ActualizarDespachoItemRequest(
        @NotNull UUID productoId,
        @PositiveOrZero int unidades
) {}
