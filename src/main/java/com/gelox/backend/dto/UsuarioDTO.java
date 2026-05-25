package com.gelox.backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.gelox.backend.entities.RolUsuario;

import java.util.UUID;

public record UsuarioDTO(
        UUID id,
        String nombre,
        RolUsuario rol,
        @JsonProperty("foto_url") String fotoUrl,
        String correo,
        String telefono
) {}