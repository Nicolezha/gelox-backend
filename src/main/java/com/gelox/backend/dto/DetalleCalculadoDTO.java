package com.gelox.backend.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record DetalleCalculadoDTO(
        UUID detalleId,
        int unidadesDespachadas,
        int unidadesDevueltas,
        int unidadesVendidas,
        BigDecimal precioDeVenta,
        BigDecimal ganancia,
        BigDecimal gananciaComerciante   // RF66 — null si el ítem no tiene precio al público
) {}
