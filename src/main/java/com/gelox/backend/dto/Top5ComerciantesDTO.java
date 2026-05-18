package com.gelox.backend.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record Top5ComerciantesDTO(List<ComercianteIngresoDTO> comerciantes) {

    public record ComercianteIngresoDTO(
            UUID comercianteId,
            String nombre,
            BigDecimal totalIngreso,
            int posicion
    ) {}
}
