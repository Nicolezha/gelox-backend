package com.gelox.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Payload para crear o registrar un nuevo comerciante (RF35).
 */
public record CrearComercianteRequest(

        @NotBlank(message = "El nombre es obligatorio")
        @Size(max = 255)
        String nombre,

        @Size(max = 100)
        String municipio,

        String direccion,

        @Size(max = 50)
        String telefono,

        /** Nombre completo del contacto de emergencia. */
        @Size(max = 255)
        String contactoEmergenciaNombre,

        /** Parentesco del contacto de emergencia (madre, padre, cónyuge…). */
        @Size(max = 100)
        String contactoEmergenciaParentesco,

        /** Talla de uniforme: XS, S, M, L, XL, XXL. */
        @Size(max = 10)
        String tallaUniforme,

        /** Placa del carrito de helados. */
        @Size(max = 20)
        String placa,

        /** Número de documento de identidad. */
        @Size(max = 50)
        String documento,

        /** Tipo de documento: CC o PPT. */
        @Size(max = 10)
        String tipoDocumento,

        /** EPS a la que pertenece el comerciante. */
        @Size(max = 255)
        String eps,

        /** URL pública de la fotografía de perfil. */
        String fotoUrl
) {}
