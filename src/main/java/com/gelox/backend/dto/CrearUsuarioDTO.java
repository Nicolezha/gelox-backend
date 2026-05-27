package com.gelox.backend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CrearUsuarioDTO {

    @NotBlank(message = "El nombre es obligatorio")
    private String nombre;

    @NotBlank(message = "El correo es obligatorio")
    @Email(message = "El correo debe tener un formato válido")
    private String correo;

    @NotBlank(message = "La contraseña temporal es obligatoria")
    private String contrasenaTemporal;

    @NotNull(message = "El rol es obligatorio")
    private String rol;

    /** URL pública de la foto de perfil (opcional). */
    private String fotoUrl;
}
