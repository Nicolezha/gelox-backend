package com.gelox.backend.gobierno.dto;

import java.util.UUID;

/** RF71 — Opción del filtro de usuario del log: { id, nombre, correo, rol, distribuidoraNombre }. */
public record AuditoriaUsuarioDTO(UUID id, String nombre, String correo, String rol, String distribuidoraNombre) {
}
