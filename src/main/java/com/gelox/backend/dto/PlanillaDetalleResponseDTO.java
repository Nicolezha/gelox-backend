package com.gelox.backend.dto;

import com.gelox.backend.entities.PlanillaComerciante;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Detalle completo de una planilla cerrada: cabecera + items por producto (RF36-39).
 */
public record PlanillaDetalleResponseDTO(
        UUID          id,
        UUID          comercianteId,
        String        comercianteNombre,
        UUID          usuarioId,
        LocalDate     fecha,
        boolean       cerrada,
        BigDecimal    totalGanancia,
        BigDecimal    efectivoRecibido,
        LocalDateTime timestampCierre,
        LocalDateTime createdAt,
        List<ItemPlanillaDetalleDTO> items
) {
    public static PlanillaDetalleResponseDTO from(PlanillaComerciante p,
                                                   List<ItemPlanillaDetalleDTO> items) {
        return new PlanillaDetalleResponseDTO(
                p.getId(),
                p.getComerciante().getId(),
                p.getComerciante().getNombre(),
                p.getUsuario().getId(),
                p.getFecha(),
                Boolean.TRUE.equals(p.getCerrada()),
                p.getTotalGanancia(),
                p.getEfectivoRecibido(),
                p.getTimestampCierre(),
                p.getCreatedAt(),
                items
        );
    }
}
