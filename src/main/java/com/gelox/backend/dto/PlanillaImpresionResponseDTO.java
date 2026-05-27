package com.gelox.backend.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Todos los datos necesarios para renderizar la vista de impresión de una planilla (RF39).
 * Funciona tanto para planillas abiertas como cerradas.
 */
public record PlanillaImpresionResponseDTO(
        UUID          id,
        LocalDate     fecha,
        boolean       cerrada,
        LocalDateTime timestampCierre,
        LocalDateTime createdAt,

        UUID   comercianteId,
        String comercianteNombre,
        String comercianteTelefono,
        String comercianteMunicipio,
        String comercianteDireccion,

        UUID   usuarioId,
        String usuarioNombre,

        List<ItemImpresionPlanillaDTO> items,

        int        totalUnidadesDespachadas,
        int        totalUnidadesDevueltas,
        int        totalUnidadesVendidas,
        BigDecimal totalGanancia,
        BigDecimal efectivoRecibido
) {}
