package com.gelox.backend.controllers;

import com.gelox.backend.dto.*;
import com.gelox.backend.entities.Usuario;
import com.gelox.backend.services.ComercianteService;
import com.gelox.backend.services.SupabaseStorageService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Gestión de comerciantes independientes y su historial de planillas.
 *
 * <pre>
 * GET    /api/comerciantes                    — listar todos / buscar por ?q= (RF34)
 * POST   /api/comerciantes                    — registrar nuevo (JSON o multipart) (RF34)
 * PUT    /api/comerciantes/{id}               — editar datos (JSON o multipart)    (RF34)
 * PATCH  /api/comerciantes/{id}/estado        — activar / desactivar (RF34)
 * GET    /api/comerciantes/{id}/planillas     — historial planillas (RF35)
 * GET    /api/comerciantes/{id}/planillas/{planillaId} — detalle planilla (RF36-39)
 * </pre>
 */
@RestController
@RequestMapping("/api/comerciantes")
@RequiredArgsConstructor
public class ComercianteController {

    private final ComercianteService     comercianteService;
    private final SupabaseStorageService storageService;

    // ── GET /api/comerciantes ─────────────────────────────────────────────────

    @GetMapping
    public ResponseEntity<List<ComercianteResponseDTO>> listar(
            @RequestParam(required = false) String q) {
        return ResponseEntity.ok(comercianteService.listar(q));
    }

    // ── POST /api/comerciantes — JSON (sin foto) ──────────────────────────────

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ComercianteResponseDTO> crear(
            @RequestBody @Valid CrearComercianteRequest req,
            @AuthenticationPrincipal Usuario usuario) {

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(comercianteService.crear(req, usuario));
    }

    // ── POST /api/comerciantes — multipart (con foto opcional) ───────────────

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ComercianteResponseDTO> crearConFoto(
            @RequestParam                   String        nombre,
            @RequestParam(required = false) String        municipio,
            @RequestParam(required = false) String        direccion,
            @RequestParam(required = false) String        telefono,
            @RequestParam(required = false) String        contactoEmergenciaNombre,
            @RequestParam(required = false) String        contactoEmergenciaParentesco,
            @RequestParam(required = false) String        tallaUniforme,
            @RequestParam(required = false) MultipartFile foto,
            @AuthenticationPrincipal        Usuario       usuario) {

        String fotoUrl = (foto != null && !foto.isEmpty())
                ? storageService.subirImagen(foto, "comerciantes")
                : null;

        CrearComercianteRequest req = new CrearComercianteRequest(
                nombre, municipio, direccion, telefono,
                contactoEmergenciaNombre, contactoEmergenciaParentesco,
                tallaUniforme, fotoUrl);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(comercianteService.crear(req, usuario));
    }

    // ── PUT /api/comerciantes/{id} — JSON (sin foto) ──────────────────────────

    @PutMapping(value = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ComercianteResponseDTO> editar(
            @PathVariable UUID id,
            @RequestBody @Valid EditarComercianteRequest req,
            @AuthenticationPrincipal Usuario usuario) {

        return ResponseEntity.ok(comercianteService.editar(id, req, usuario));
    }

    // ── PUT /api/comerciantes/{id} — multipart (con foto opcional) ───────────

    @PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ComercianteResponseDTO> editarConFoto(
            @PathVariable                   UUID          id,
            @RequestParam                   String        nombre,
            @RequestParam(required = false) String        municipio,
            @RequestParam(required = false) String        direccion,
            @RequestParam(required = false) String        telefono,
            @RequestParam(required = false) String        contactoEmergenciaNombre,
            @RequestParam(required = false) String        contactoEmergenciaParentesco,
            @RequestParam(required = false) String        tallaUniforme,
            @RequestParam(required = false) MultipartFile foto,
            @AuthenticationPrincipal        Usuario       usuario) {

        String fotoUrl = (foto != null && !foto.isEmpty())
                ? storageService.subirImagen(foto, "comerciantes")
                : null;

        EditarComercianteRequest req = new EditarComercianteRequest(
                nombre, municipio, direccion, telefono,
                contactoEmergenciaNombre, contactoEmergenciaParentesco,
                tallaUniforme, fotoUrl);

        return ResponseEntity.ok(comercianteService.editar(id, req, usuario));
    }

    // ── PATCH /api/comerciantes/{id}/estado ───────────────────────────────────

    @PatchMapping("/{id}/estado")
    public ResponseEntity<ComercianteResponseDTO> cambiarEstado(
            @PathVariable UUID id,
            @RequestBody @Valid CambiarEstadoComercianteRequest req,
            @AuthenticationPrincipal Usuario usuario) {

        return ResponseEntity.ok(
                comercianteService.cambiarEstado(id, req.activo(), usuario));
    }

    // ── GET /api/comerciantes/{id}/planillas ──────────────────────────────────

    @GetMapping("/{id}/planillas")
    public ResponseEntity<List<PlanillaResumenDTO>> obtenerPlanillas(
            @PathVariable UUID id,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaInicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaFin) {

        return ResponseEntity.ok(
                comercianteService.obtenerPlanillas(id, fechaInicio, fechaFin));
    }

    // ── GET /api/comerciantes/{id}/planillas/{planillaId} ─────────────────────

    @GetMapping("/{id}/planillas/{planillaId}")
    public ResponseEntity<PlanillaDetalleResponseDTO> obtenerDetallePlanilla(
            @PathVariable UUID id,
            @PathVariable UUID planillaId,
            @AuthenticationPrincipal Usuario usuario) {

        return ResponseEntity.ok(
                comercianteService.obtenerDetallePlanilla(id, planillaId));
    }
}