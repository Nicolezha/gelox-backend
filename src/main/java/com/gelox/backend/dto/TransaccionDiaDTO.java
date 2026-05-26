package com.gelox.backend.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record TransaccionDiaDTO(
        String id,
        /** VENTANILLA | RURAL | PLANILLA */
        String tipo,
        /** Nombre del comerciante para planillas; null para ventas directas */
        String clienteOComerciante,
        /** Lista de productos separados por coma */
        String productos,
        int cantidad,
        BigDecimal total,
        /** Hora exacta para ventas; null para planillas (solo tienen fecha) */
        LocalDateTime hora
) {}