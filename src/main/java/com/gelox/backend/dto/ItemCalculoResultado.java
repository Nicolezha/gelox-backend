package com.gelox.backend.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record ItemCalculoResultado(
        UUID       productoId,
        Integer    cantidad,
        BigDecimal precioUnitario,
        BigDecimal subtotal
) {}
