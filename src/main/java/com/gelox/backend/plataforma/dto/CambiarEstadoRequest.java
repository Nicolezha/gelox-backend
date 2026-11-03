package com.gelox.backend.plataforma.dto;

import com.gelox.backend.entities.EstadoDistribuidora;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** RF59 — Cambio de estado del ciclo de vida de una distribuidora; el motivo es obligatorio. */
public record CambiarEstadoRequest(
        @NotNull(message = "El estado es obligatorio")
        EstadoDistribuidora estado,

        @NotBlank(message = "El motivo es obligatorio")
        String motivo
) {}
