package com.gelox.backend.dto;

import com.gelox.backend.entities.TipoEvento;

import java.time.OffsetDateTime;
import java.util.UUID;

public record EventoSistemaDTO(
        UUID id,
        TipoEvento tipo,
        String descripcion,
        UUID usuarioId,
        OffsetDateTime fecha
) {}
