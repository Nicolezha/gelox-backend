package com.gelox.backend.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record ConfirmarVentaResponse(
        UUID                    ventaId,
        String                  canal,
        LocalDateTime           fecha,
        String                  estado,
        List<ItemVentaResponseDTO> items,
        BigDecimal              total
) {}
