package com.gelox.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Payload para editar los datos de un comerciante existente (RF34).
 * Mismos campos que {@link CrearComercianteRequest}.
 */
public record EditarComercianteRequest(

        @NotBlank(message = "El nombre es obligatorio")
        @Size(max = 255)
        String nombre,

        @Size(max = 100)
        String municipio,

        String direccion,

        @Size(max = 50)
        String telefono,

        @Size(max = 255)
        String contactoEmergenciaNombre,

        @Size(max = 100)
        String contactoEmergenciaParentesco,

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

        String fotoUrl
) {}
