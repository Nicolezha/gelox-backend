package com.gelox.backend.catalogo.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CategoriaRequest(
        @NotBlank(message = "El nombre es obligatorio")
        @Size(max = 80, message = "El nombre no puede superar 80 caracteres")
        String nombre,

        @Min(value = 0, message = "El orden no puede ser negativo")
        Integer orden
) {}
