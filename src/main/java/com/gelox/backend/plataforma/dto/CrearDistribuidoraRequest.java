package com.gelox.backend.plataforma.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.util.UUID;

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

        /** RF70 — plan_suscripcion.id (antes: el nombre del plan en texto). */
        @NotNull(message = "El plan es obligatorio")
        UUID planId
) {}
