package com.gelox.backend.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record ItemVentaResponseDTO(
        UUID       productoId,
        String     nombre,
        Integer    cantidad,
        BigDecimal precioUnitario,
        BigDecimal subtotal
) {}
