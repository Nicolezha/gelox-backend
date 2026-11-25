package com.gelox.backend.catalogo.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record FactorConversionDTO(UUID unidadOrigenId, String unidadOrigenCodigo, BigDecimal factor) {}
