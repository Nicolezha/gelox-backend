package com.gelox.backend.plataforma.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * RF58 — Datos del formulario de aprovisionamiento de una distribuidora nueva.
 */
public record CrearDistribuidoraRequest(

        @NotBlank(message = "La razón social es obligatoria")
        String razonSocial,

        @NotBlank(message = "El NIT es obligatorio")
        String nit,

        @NotBlank(message = "El nombre comercial es obligatorio")
        String nombreComercial,

        @NotBlank(message = "El subdominio es obligatorio")
        @Pattern(regexp = "^[a-z0-9-]{3,30}$", message = "El subdominio solo admite minúsculas, dígitos y guiones (3 a 30 caracteres)")
        String subdominio,

        @NotBlank(message = "El nombre del gerente es obligatorio")
        String nombreGerente,

        @NotBlank(message = "El correo del gerente es obligatorio")
        @Email(message = "El correo debe tener un formato válido")
        String correoGerente,

        String telefono,

        String direccion,

        @NotBlank(message = "El plan es obligatorio")
        String plan
) {}
