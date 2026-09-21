package com.gelox.backend.voz.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/** Body de POST /api/voz/confirmar. */
public record VozConfirmarRequest(
        @NotNull(message = "El comandoId es obligatorio") UUID comandoId,
        @NotNull(message = "Debe indicar si confirma o cancela") Boolean confirmar
) {}
