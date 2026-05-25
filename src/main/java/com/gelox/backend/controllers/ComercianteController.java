package com.gelox.backend.controllers;

import com.gelox.backend.dto.*;
import com.gelox.backend.entities.Usuario;
import com.gelox.backend.services.ComercianteService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Gestión de comerciantes independientes y su historial de planillas.
 *
 * <pre>
 * GET  /api/comerciantes                              — listar todos (RF34)
 * POST /api/comerciantes                              — registrar nuevo (RF35)
 * PUT  /api/comerciantes/{id}                         — editar datos (RF34)
 * PATCH /api/comerciantes/{id}/estado                 — activar / desactivar (RF34)
 * GET  /api/comerciantes/{id}/planillas               — historial planillas (RF35)
 * </pre>
 */
@RestController
@RequestMapping("/api/comerciantes")
@RequiredArgsConstructor
public class ComercianteController {

    private final ComercianteService comercianteService;

    // ──────────────────────────────────────────────────────────────────────
    // GET /api/comerciantes
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Lista todos los comerciantes (activos e inactivos) ordenados por nombre.
     * Roles: ADMINISTRADOR, ENCARGADO_VENTAS.
     */
    @GetMapping
    public ResponseEntity<List<ComercianteResponseDTO>> listar() {
        return ResponseEntity.ok(comercianteService.listar());
    }

    // ──────────────────────────────────────────────────────────────────────
    // POST /api/comerciantes
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Registra un nuevo comerciante.
     * Campos requeridos: nombre.
     * Campos opcionales: municipio, dirección, teléfono, contacto emergencia, talla, foto.
     *
     * Roles: ADMINISTRADOR.
     */
    @PostMapping
    public ResponseEntity<ComercianteResponseDTO> crear(
            @RequestBody @Valid CrearComercianteRequest req,
            @AuthenticationPrincipal Usuario usuario) {

        ComercianteResponseDTO creado = comercianteService.crear(req, usuario);
        return ResponseEntity.status(HttpStatus.CREATED).body(creado);
    }

    // ──────────────────────────────────────────────────────────────────────
    // PUT /api/comerciantes/{id}
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Actualiza todos los campos editables de un comerciante.
     * Roles: ADMINISTRADOR.
     */
    @PutMapping("/{id}")
    public ResponseEntity<ComercianteResponseDTO> editar(
            @PathVariable UUID id,
            @RequestBody @Valid EditarComercianteRequest req,
            @AuthenticationPrincipal Usuario usuario) {

        return ResponseEntity.ok(comercianteService.editar(id, req, usuario));
    }

    // ──────────────────────────────────────────────────────────────────────
    // PATCH /api/comerciantes/{id}/estado
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Activa o desactiva un comerciante.
     * Body: {@code { "activo": true|false }}
     *
     * Roles: ADMINISTRADOR.
     */
    @PatchMapping("/{id}/estado")
    public ResponseEntity<ComercianteResponseDTO> cambiarEstado(
            @PathVariable UUID id,
            @RequestBody @Valid CambiarEstadoComercianteRequest req,
            @AuthenticationPrincipal Usuario usuario) {

        return ResponseEntity.ok(
                comercianteService.cambiarEstado(id, req.activo(), usuario));
    }

    // ──────────────────────────────────────────────────────────────────────
    // GET /api/comerciantes/{id}/planillas
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Historial de planillas cerradas del comerciante, en orden descendente.
     *
     * <p>Parámetros opcionales (ISO-8601):
     * <ul>
     *   <li>{@code fechaInicio} — por defecto: primer día del mes actual</li>
     *   <li>{@code fechaFin}    — por defecto: hoy</li>
     * </ul>
     *
     * Cada ítem incluye: planillaId, fecha, totalDespachado, totalDevuelto,
     * unidadesVendidas, ganancia.
     *
     * Roles: ADMINISTRADOR.
     */
    @GetMapping("/{id}/planillas")
    public ResponseEntity<List<PlanillaResumenDTO>> obtenerPlanillas(
            @PathVariable UUID id,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaInicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaFin) {

        return ResponseEntity.ok(
                comercianteService.obtenerPlanillas(id, fechaInicio, fechaFin));
    }
}
