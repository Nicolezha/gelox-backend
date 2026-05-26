package com.gelox.backend.controllers;

import com.gelox.backend.dto.CierreCajaDTO;
import com.gelox.backend.dto.CierreCajaResponseDTO;
import com.gelox.backend.dto.ReporteDiarioDTO;
import com.gelox.backend.dto.TransaccionesDiaPageDTO;
import com.gelox.backend.entities.Usuario;
import com.gelox.backend.services.CierreCajaService;
import com.gelox.backend.services.ReporteDiarioService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/reportes/diario")
@RequiredArgsConstructor
public class ReporteDiarioController {

    private final ReporteDiarioService reporteDiarioService;
    private final CierreCajaService    cierreCajaService;

    /**
     * RF40 — Reporte del día con indicadores consolidados por canal.
     *
     * GET /api/reportes/diario?fecha=2025-05-26
     * Si no se envía fecha, usa el día actual.
     */
    @GetMapping
    public ResponseEntity<ReporteDiarioDTO> getReporteDiario(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha) {

        LocalDate dia = fecha != null ? fecha : LocalDate.now();
        return ResponseEntity.ok(reporteDiarioService.generarReporteDiario(dia));
    }

    /**
     * RF41 — Tabla de transacciones del día, filtrable por canal y paginada.
     *
     * GET /api/reportes/diario/transacciones?fecha=2025-05-26&canal=VENTANILLA&page=1&limit=20
     *
     * @param canal  VENTANILLA | RURAL | PLANILLA (opcional — sin filtro devuelve todos)
     * @param page   Página (desde 1, default 1)
     * @param limit  Registros por página (default 20, máx 100)
     */
    @GetMapping("/transacciones")
    public ResponseEntity<TransaccionesDiaPageDTO> getTransacciones(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha,
            @RequestParam(required = false) String canal,
            @RequestParam(defaultValue = "1")  int page,
            @RequestParam(defaultValue = "20") int limit) {

        LocalDate dia = fecha != null ? fecha : LocalDate.now();
        return ResponseEntity.ok(
                reporteDiarioService.listarTransacciones(dia, canal, page, limit));
    }

    /**
     * RF42 — Cierre operativo: registra el dinero físico percibido en los tres
     * canales para que el Gerente realice la conciliación (RF15).
     *
     * Delega al mismo servicio de cierre de caja (CierreCajaService) que ya
     * calcula automáticamente los montos teóricos y las diferencias por canal.
     *
     * POST /api/reportes/diario/cierre-operativo?fecha=2025-05-26
     * Body: { "montoFisicoVentanilla": 0, "montoFisicoRural": 0, "montoFisicoComerciantes": 0 }
     */
    @PostMapping("/cierre-operativo")
    public ResponseEntity<CierreCajaResponseDTO> cierreOperativo(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha,
            @Valid @RequestBody CierreCajaDTO dto,
            @AuthenticationPrincipal Usuario usuario) {

        LocalDate dia = fecha != null ? fecha : LocalDate.now();
        CierreCajaResponseDTO response = cierreCajaService.registrarDineroFisico(dia, dto, usuario);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}