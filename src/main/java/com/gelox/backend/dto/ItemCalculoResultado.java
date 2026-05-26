package com.gelox.backend.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record ItemCalculoResultado(
        UUID       productoId,
        Integer    cajas,
        Integer    unidades,
        BigDecimal precioUnitario,
        BigDecimal subtotal
) {}
