package com.gelox.backend.dto;

import java.math.BigDecimal;
import java.util.List;

public record CalcularVentaResponse(
        List<ItemCalculoResultado> items,
        BigDecimal                 total
) {}
