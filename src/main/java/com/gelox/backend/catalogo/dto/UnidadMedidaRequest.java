package com.gelox.backend.catalogo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UnidadMedidaRequest(
        @NotBlank(message = "El código es obligatorio")
        @Size(max = 10, message = "El código no puede superar 10 caracteres")
        String codigo,

        @NotBlank(message = "El nombre es obligatorio")
        @Size(max = 50, message = "El nombre no puede superar 50 caracteres")
        String nombre
) {}
