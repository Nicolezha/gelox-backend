package com.gelox.backend.controllers;

import com.gelox.backend.dto.InversionVsIngresosDTO;
import com.gelox.backend.dto.KpiDTO;
import com.gelox.backend.dto.VentasPorCanalDTO;
import com.gelox.backend.services.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping("/kpis")
    public ResponseEntity<KpiDTO> getKpis(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha) {

        return ResponseEntity.ok(dashboardService.obtenerKPIs(
                fecha != null ? fecha : LocalDate.now()));
    }

    @GetMapping("/inversion-ingresos")
    public ResponseEntity<List<InversionVsIngresosDTO>> getInversionVsIngresos() {
        return ResponseEntity.ok(dashboardService.obtenerInversionVsIngresos());
    }

    @GetMapping("/ventas-por-canal")
    public ResponseEntity<VentasPorCanalDTO> getVentasPorCanal(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaInicio,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaFin) {

        LocalDate fin    = fechaFin    != null ? fechaFin    : LocalDate.now();
        LocalDate inicio = fechaInicio != null ? fechaInicio : fin.minusDays(30);

        return ResponseEntity.ok(dashboardService.obtenerVentasPorCanal(inicio, fin));
    }
}