package com.gelox.backend.dto;

import java.time.LocalDate;
import java.util.List;

public record ReporteFinancieroCompletoDTO(
        TipoPeriodo tipoPeriodo,
        LocalDate fechaInicio,
        LocalDate fechaFin,
        ReporteFinancieroDTO resumen,
        List<PuntoGraficaDTO> grafica,
        ReporteRentabilidadDTO rentabilidad
) {}