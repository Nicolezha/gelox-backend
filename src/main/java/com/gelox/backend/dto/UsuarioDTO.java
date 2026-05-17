package com.gelox.backend.dto;

import com.gelox.backend.entities.RolUsuario;

import java.util.UUID;

public record UsuarioDTO(
        UUID id,
        String nombre,
        RolUsuario rol,
        String fotoUrl
) {}