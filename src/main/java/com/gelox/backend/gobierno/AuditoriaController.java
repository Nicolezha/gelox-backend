package com.gelox.backend.gobierno;

import com.gelox.backend.gobierno.dto.AuditoriaFilaDTO;
import com.gelox.backend.gobierno.dto.AuditoriaUsuarioDTO;
import com.gelox.backend.gobierno.dto.PaginaDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * RF71 — Log de auditoría del Superadministrador (SecurityConfig: /api/plataforma/**,
 * y @RequiereRol en AuditoriaConsultaService).
 */
@RestController
@RequestMapping("/api/plataforma/auditoria")
@RequiredArgsConstructor
public class AuditoriaController {

    private final AuditoriaConsultaService consultaService;

    /** Página ordenada por fechaHora desc. desde/hasta en YYYY-MM-DD (inclusivos). */
    @GetMapping
    public ResponseEntity<PaginaDTO<AuditoriaFilaDTO>> listar(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) UUID distribuidoraId,
            @RequestParam(required = false) UUID usuarioId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(required = false) String accion) {
        return ResponseEntity.ok(consultaService.listar(
                new AuditoriaConsultaService.Filtros(distribuidoraId, usuarioId, desde, hasta, accion), page, size));
    }

    @GetMapping("/acciones")
    public ResponseEntity<List<String>> acciones() {
        return ResponseEntity.ok(consultaService.acciones());
    }

    @GetMapping("/usuarios")
    public ResponseEntity<List<AuditoriaUsuarioDTO>> usuarios(@RequestParam(required = false) UUID distribuidoraId) {
        return ResponseEntity.ok(consultaService.usuarios(distribuidoraId));
    }
}
