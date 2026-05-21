package com.gelox.backend.controllers;

import com.gelox.backend.dto.*;
import com.gelox.backend.services.ReporteFinancieroService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

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
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "La fecha de inicio no puede ser posterior a la fecha fin");

        return ResponseEntity.ok(service.generarReporte(new PeriodoFiltroDTO(fechaInicio, fechaFin)));
    }

    /** RF13 — Gráfica inversión vs ingresos con agrupación dinámica según tipo_periodo */
    @GetMapping("/grafica-inversion-ingresos")
    public ResponseEntity<List<PuntoGraficaDTO>> getGrafica(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaInicio,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaFin,
            @RequestParam(defaultValue = "SEMANA") TipoPeriodo tipo_periodo) {

        if (fechaInicio.isAfter(fechaFin))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "La fecha de inicio no puede ser posterior a la fecha fin");

        return ResponseEntity.ok(
                service.getGraficaInversionVsIngresos(
                        new PeriodoFiltroDTO(fechaInicio, fechaFin), tipo_periodo));
    }

    /** RF14 — Tabla rentabilidad por canal */
    @GetMapping("/por-canal")
    public ResponseEntity<ReporteRentabilidadDTO> getRentabilidadPorCanal(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaInicio,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaFin) {

        if (fechaInicio.isAfter(fechaFin))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "La fecha de inicio no puede ser posterior a la fecha fin");

        return ResponseEntity.ok(
                service.getRentabilidadPorCanal(new PeriodoFiltroDTO(fechaInicio, fechaFin)));
    }

    /** RF16 — Reporte completo con filtro de período dinámico */
    @GetMapping("/financieros")
    public ResponseEntity<ReporteFinancieroCompletoDTO> getReporteConPeriodo(
            @RequestParam TipoPeriodo tipo_periodo,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha_inicio,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha_fin) {

        if (tipo_periodo == TipoPeriodo.RANGO) {
            if (fecha_inicio == null || fecha_fin == null)
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "RANGO requiere fecha_inicio y fecha_fin");
            if (fecha_inicio.isAfter(fecha_fin))
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "fecha_inicio no puede ser posterior a fecha_fin");
        }

        return ResponseEntity.ok(
                service.generarReporteCompleto(tipo_periodo, fecha_inicio, fecha_fin));
    }
}
