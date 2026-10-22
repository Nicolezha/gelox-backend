package com.gelox.backend.plataforma;

import com.gelox.backend.dto.ActualizarIdentidadRequest;
import com.gelox.backend.dto.IdentidadDTO;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
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
        return ResponseEntity.ok(identidadService.obtener());
    }

    /**
     * PUT /api/distribuidora/identidad (multipart/form-data) — solo ADMINISTRADOR (Gerente).
     * nombreComercial, colorPrimario y colorSecundario se validan en el servicio (400 con mensaje).
     */
    @PutMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<IdentidadDTO> actualizar(
            @ModelAttribute ActualizarIdentidadRequest datos,
            @RequestParam(required = false) MultipartFile logo,
            @RequestParam(required = false) MultipartFile favicon) {
        return ResponseEntity.ok(identidadService.actualizar(datos, logo, favicon));
    }
}
