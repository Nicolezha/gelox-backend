package com.gelox.backend.ventas.rural.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * RF33 — Request para registrar un nuevo cliente rural.
 * No incluye 'correo' porque la tabla cliente_rural no tiene esa columna en BD.
 */
public record CrearClienteRuralRequest(
        @NotBlank(message = "El nombre del cliente es obligatorio")
        String nombre,

        String telefono,       // opcional — usado para dedup
        String direccion,      // opcional
        String corregimiento   // opcional
) {}
