package com.gelox.backend.exportacion;

import com.gelox.backend.dto.TipoPeriodo;
import com.gelox.backend.entities.Usuario;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * RF68 — GET /api/reportes/financiero/excel y /pdf. Solo ADMINISTRADOR (SecurityConfig protege
 * /api/reportes/** y el service lo verifica con @RequiereRol).
 * Parámetros: tipo (DIA, SEMANA, MES, ANIO, RANGO), fechaInicio y fechaFin (obligatorias en RANGO)
 * y secciones (lista separada por comas de {@link SeccionReporte}).
 */
@RestController
@RequestMapping("/api/reportes/financiero")
@RequiredArgsConstructor
public class ReporteExportController {

    private static final MediaType XLSX =
            MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final ReporteExportService service;

    @GetMapping("/excel")
    public ResponseEntity<byte[]> excel(
            @RequestParam TipoPeriodo tipo,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaInicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaFin,
            @RequestParam(required = false) List<String> secciones,
            @AuthenticationPrincipal Usuario usuario) {
        return respuesta(service.exportarExcel(tipo, fechaInicio, fechaFin, parsear(secciones), usuario), XLSX);
    }

    @GetMapping("/pdf")
    public ResponseEntity<byte[]> pdf(
            @RequestParam TipoPeriodo tipo,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaInicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaFin,
            @RequestParam(required = false) List<String> secciones,
            @AuthenticationPrincipal Usuario usuario) {
        return respuesta(service.exportarPdf(tipo, fechaInicio, fechaFin, parsear(secciones), usuario),
                MediaType.APPLICATION_PDF);
    }

    private static ResponseEntity<byte[]> respuesta(ReporteExportService.ArchivoExportado archivo, MediaType tipo) {
        HttpHeaders cabeceras = new HttpHeaders();
        cabeceras.setContentDisposition(ContentDisposition.attachment().filename(archivo.nombre()).build());
        return ResponseEntity.ok().headers(cabeceras).contentType(tipo).body(archivo.contenido());
    }

    /** Sección desconocida → 400. Sin secciones lo valida el service (400 también). */
    private static Set<SeccionReporte> parsear(List<String> valores) {
        Set<SeccionReporte> secciones = EnumSet.noneOf(SeccionReporte.class);
        if (valores == null) return secciones;
        for (String valor : valores) {
            if (valor == null || valor.isBlank()) continue;
            try {
                secciones.add(SeccionReporte.valueOf(valor.trim().toUpperCase()));
            } catch (IllegalArgumentException e) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Sección desconocida: " + valor);
            }
        }
        return secciones;
    }
}
