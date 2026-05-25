package com.gelox.backend.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record VentaResponseDTO(
        UUID id,
        String canal,
        String estado,
        LocalDateTime fecha,
        BigDecimal total
) {}
