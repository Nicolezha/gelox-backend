package com.gelox.backend.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record CrearDespachoResponse(
        UUID planillaId,
        UUID comercianteId,
        LocalDate fecha,
        List<ItemDespachoResponseDTO> items,
        BigDecimal totalValorDespachado
) {}
