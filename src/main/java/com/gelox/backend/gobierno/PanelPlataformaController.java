package com.gelox.backend.gobierno;

import com.gelox.backend.gobierno.dto.PanelPlataformaDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * RF69 — Dashboard del Superadministrador (SecurityConfig: /api/plataforma/**,
 * y @RequiereRol en PanelPlataformaService).
 */
@RestController
@RequestMapping("/api/plataforma/panel")
@RequiredArgsConstructor
public class PanelPlataformaController {

    private final PanelPlataformaService panelService;

    /** desde/hasta en YYYY-MM-DD, inclusivos; por defecto, los últimos 30 días hasta hoy. */
    @GetMapping
    public ResponseEntity<PanelPlataformaDTO> obtener(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        return ResponseEntity.ok(panelService.generar(desde, hasta));
    }
}
