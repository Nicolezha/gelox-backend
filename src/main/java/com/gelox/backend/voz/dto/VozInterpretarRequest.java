package com.gelox.backend.voz.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Body de POST /api/voz/interpretar: el texto ya transcrito por el STT y la
 * confianza que reportó ese servicio.
 */
public record VozInterpretarRequest(

        @NotBlank(message = "El texto transcrito es obligatorio")
        @Size(max = 500, message = "El texto no puede superar los 500 caracteres")
        String texto,

        @NotNull(message = "La confianza es obligatoria")
        @DecimalMin(value = "0.0", message = "La confianza debe estar entre 0 y 1")
        @DecimalMax(value = "1.0", message = "La confianza debe estar entre 0 y 1")
        Double confianza
) {}
