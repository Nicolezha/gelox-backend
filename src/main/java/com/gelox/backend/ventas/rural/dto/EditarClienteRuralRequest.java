package com.gelox.backend.ventas.rural.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * RF33 — Request para editar los datos de un cliente rural existente.
 */
public record EditarClienteRuralRequest(
        @NotBlank(message = "El nombre del cliente es obligatorio")
        String nombre,

        @NotBlank(message = "El teléfono del cliente es obligatorio")
        String telefono,
        String correo,
        String direccion,
        String corregimiento
) {}
