package com.gelox.backend.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record ItemDespachoResponseDTO(
        UUID productoId,
        String nombreProducto,
        int cajas,
        int unidades,
        int totalUnidades,
        BigDecimal precioUnitario,
        BigDecimal subtotal
) {}
