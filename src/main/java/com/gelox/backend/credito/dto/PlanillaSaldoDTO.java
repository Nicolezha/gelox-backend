package com.gelox.backend.credito.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** RF65 — una planilla cerrada con saldo pendiente (FP-15 §2.17.1). */
public record PlanillaSaldoDTO(
        UUID planillaId,
        LocalDate fecha,
        LocalDate fechaLimitePago,
        BigDecimal valor,
        BigDecimal saldo,
        boolean vencida,
        Long diasMora
) {}
