package com.gelox.backend.controllers;

import com.gelox.backend.dto.IdentidadDTO;
import com.gelox.backend.services.IdentidadDistribuidoraService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * RF60 — Identidad visual de la distribuidora del usuario autenticado.
 */
@RestController
@RequestMapping("/api/distribuidora/identidad")
public class IdentidadDistribuidoraController {

    private final IdentidadDistribuidoraService identidadService;

    public IdentidadDistribuidoraController(IdentidadDistribuidoraService identidadService) {
        this.identidadService = identidadService;
    }

    /** GET /api/distribuidora/identidad — cualquier usuario autenticado de la distribuidora. */
    @GetMapping
    public ResponseEntity<IdentidadDTO> obtener() {
        return ResponseEntity.ok(identidadService.obtenerIdentidad());
    }

    /**
     * PUT /api/distribuidora/identidad (multipart/form-data) — solo ADMINISTRADOR (Gerente).
     * Los campos llegan como opcionales para que su ausencia responda 400 con mensaje, no 500.
     */
    @PutMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<IdentidadDTO> actualizar(
            @RequestParam(required = false) String        nombreComercial,
            @RequestParam(required = false) String        colorPrimario,
            @RequestParam(required = false) String        colorSecundario,
            @RequestParam(required = false) MultipartFile logo,
            @RequestParam(required = false) MultipartFile favicon) {
        return ResponseEntity.ok(identidadService.actualizarIdentidad(
                nombreComercial, colorPrimario, colorSecundario, logo, favicon));
    }
}
