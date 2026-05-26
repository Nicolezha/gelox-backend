package com.gelox.backend.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record ItemVentaRequest(
        @NotNull(message = "El productoId es obligatorio") UUID productoId,
        @NotNull @Min(value = 0, message = "Las cajas deben ser >= 0") Integer cajas,
        @NotNull @Min(value = 0, message = "Las unidades deben ser >= 0") Integer unidades
) {}
