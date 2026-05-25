package com.gelox.backend.dto;

import jakarta.validation.constraints.NotNull;

/**
 * Payload para PATCH /api/comerciantes/{id}/estado (RF34).
 */
public record CambiarEstadoComercianteRequest(

        @NotNull(message = "El campo 'activo' es obligatorio")
        Boolean activo
) {}
