package com.gelox.backend.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record CatalogoVentaDTO(
        UUID    id,
        String  codigo,
        String  nombre,
        String  imagen,
        BigDecimal precioUnitario,
        Integer stock,
        boolean disponible
) {}
