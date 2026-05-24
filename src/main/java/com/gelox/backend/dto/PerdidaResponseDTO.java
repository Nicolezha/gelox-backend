package com.gelox.backend.dto;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Respuesta de POST /api/inventario/perdidas (RF25).
 */
public record PerdidaResponseDTO(
        UUID      id,
        UUID      productoId,
        String    codigoTecnico,
        String    nombreProducto,
        int       cantidad,
        String    motivo,
        LocalDate fecha,
        String    observaciones,
        int       stockAntes,
        int       stockDespues
) {}
