package com.gelox.backend.dto;

import java.time.LocalDate;

public record PeriodoFiltroDTO(
        LocalDate fechaInicio,
        LocalDate fechaFin
) {}
