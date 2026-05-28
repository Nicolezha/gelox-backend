package com.gelox.backend.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record ItemDespachoResponseDTO(
        UUID productoId,
        String nombreProducto,
        int unidades,
        BigDecimal precioUnitario,
        BigDecimal subtotal
) {}
