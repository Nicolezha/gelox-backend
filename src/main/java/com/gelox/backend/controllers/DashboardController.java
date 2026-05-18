package com.gelox.backend.controllers;

import com.gelox.backend.dto.EventoSistemaDTO;
import com.gelox.backend.dto.InversionVsIngresosDTO;
import com.gelox.backend.dto.KpiDTO;
import com.gelox.backend.dto.Top5ComerciantesDTO;
import com.gelox.backend.services.DashboardService;
import com.gelox.backend.services.EventoSistemaService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
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
    private final EventoSistemaService eventoSistemaService;

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

    @GetMapping("/top5-comerciantes")
    public ResponseEntity<Top5ComerciantesDTO> top5Comerciantes(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaInicio,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaFin) {

        LocalDate inicio = (fechaInicio != null) ? fechaInicio : LocalDate.now().withDayOfMonth(1);
        LocalDate fin    = (fechaFin != null)    ? fechaFin    : LocalDate.now();
        return ResponseEntity.ok(dashboardService.obtenerTop5Comerciantes(inicio, fin));
    }

    @GetMapping("/eventos")
    public ResponseEntity<Page<EventoSistemaDTO>> eventos(
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size) {

        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(eventoSistemaService.obtenerEventos(pageable));
    }
}