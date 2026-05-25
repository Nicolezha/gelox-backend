package com.gelox.backend.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record ItemCalculoRequest(
        @NotNull(message = "El productoId es obligatorio") UUID productoId,
        @NotNull @Min(value = 1, message = "La cantidad debe ser mayor a cero") Integer cantidad
) {}
