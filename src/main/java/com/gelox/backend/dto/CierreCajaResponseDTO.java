package com.gelox.backend.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record CierreCajaResponseDTO(
        UUID id,
        LocalDate fecha,
        BigDecimal montoCalculadoVentanilla,
        BigDecimal montoCalculadoRural,
        BigDecimal montoCalculadoComerciantes,
        BigDecimal montoCalculadoTotal,
        BigDecimal montoFisicoVentanilla,
        BigDecimal montoFisicoRural,
        BigDecimal montoFisicoComerciantes,
        BigDecimal montoFisicoTotal,
        BigDecimal diferenciaVentanilla,
        BigDecimal diferenciaRural,
        BigDecimal diferenciaComerciantes,
        BigDecimal diferenciaTotal,
        boolean tieneDiferencias,
        LocalDateTime createdAt,
        String responsable
) {}