package com.gelox.backend.controllers;

import com.gelox.backend.dto.PeriodoFiltroDTO;
import com.gelox.backend.dto.PuntoGraficaDTO;
import com.gelox.backend.dto.ReporteFinancieroDTO;
import com.gelox.backend.dto.ReporteRentabilidadDTO;
import com.gelox.backend.services.ReporteFinancieroService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/reportes")
@RequiredArgsConstructor
public class ReporteFinancieroController {

    private final ReporteFinancieroService service;

    /** RF12 — Reporte financiero del período */
    @GetMapping("/financiero")
    public ResponseEntity<ReporteFinancieroDTO> getReporteFinanciero(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaInicio,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaFin) {

        if (fechaInicio.isAfter(fechaFin))
            return ResponseEntity.badRequest().build();

        return ResponseEntity.ok(service.generarReporte(new PeriodoFiltroDTO(fechaInicio, fechaFin)));
    }

    /** RF13 — Gráfica inversión vs ingresos por semana */
    @GetMapping("/grafica-inversion-ingresos")
    public ResponseEntity<List<PuntoGraficaDTO>> getGrafica(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaInicio,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaFin) {

        if (fechaInicio.isAfter(fechaFin))
            return ResponseEntity.badRequest().build();

        return ResponseEntity.ok(
                service.getGraficaInversionVsIngresos(new PeriodoFiltroDTO(fechaInicio, fechaFin)));
    }

    /** RF14 — Tabla rentabilidad por canal */
    @GetMapping("/por-canal")
    public ResponseEntity<ReporteRentabilidadDTO> getRentabilidadPorCanal(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaInicio,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaFin) {

        if (fechaInicio.isAfter(fechaFin))
            return ResponseEntity.badRequest().build();

        return ResponseEntity.ok(
                service.getRentabilidadPorCanal(new PeriodoFiltroDTO(fechaInicio, fechaFin)));
    }
}
