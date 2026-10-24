package com.gelox.backend.recomendacion.dto;

import com.gelox.backend.entities.OrigenRecomendacion;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

/**
 * Body de POST /api/recomendaciones (RF54). Sin origen se asume PANTALLA.
 *
 * <p>El servicio repite la validación del presupuesto para que la voz, que llama a
 * {@code generar} sin pasar por el controlador, reciba el mismo error.</p>
 */
public record GenerarRecomendacionRequest(

        @NotNull(message = "El presupuesto debe ser mayor a cero")
        @Positive(message = "El presupuesto debe ser mayor a cero")
        BigDecimal presupuesto,

        OrigenRecomendacion origen
) {}
