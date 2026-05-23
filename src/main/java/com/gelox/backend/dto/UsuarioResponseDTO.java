package com.gelox.backend.dto;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
public class UsuarioResponseDTO {
    private UUID id;
    private String nombre;
    private String correo;
    private String rol;
    private boolean activo;
    private LocalDateTime ultimoAcceso;
}
