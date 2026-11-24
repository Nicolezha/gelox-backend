package com.gelox.backend.exportacion;

import com.gelox.backend.exportacion.dto.HomologacionesRequest;
import com.gelox.backend.exportacion.dto.PlantillaDTO;
import com.gelox.backend.exportacion.dto.PlantillaRequest;
import com.gelox.backend.exportacion.dto.VistaPreviaDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * RF67 — /api/proveedores/{id}/plantilla (GET, PUT), /plantilla/vista-previa (POST) y /homologaciones (PUT).
 * Roles ADMINISTRADOR y ENCARGADO_INVENTARIO (los verifica el servicio).
 */
@RestController
@RequestMapping("/api/proveedores/{id}")
@RequiredArgsConstructor
public class PlantillaExportacionController {

    private final PlantillaExportacionService service;

    @GetMapping("/plantilla")
    public ResponseEntity<PlantillaDTO> obtener(@PathVariable UUID id) {
        return ResponseEntity.ok(service.obtener(id));
    }

    @PutMapping("/plantilla")
    public ResponseEntity<PlantillaDTO> guardar(@PathVariable UUID id, @RequestBody PlantillaRequest request) {
        return ResponseEntity.ok(service.guardar(id, request));
    }

    @PostMapping("/plantilla/vista-previa")
    public ResponseEntity<VistaPreviaDTO> vistaPrevia(@PathVariable UUID id, @RequestBody PlantillaRequest request) {
        return ResponseEntity.ok(service.vistaPrevia(id, request));
    }

    @PutMapping("/homologaciones")
    public ResponseEntity<PlantillaDTO> homologaciones(@PathVariable UUID id,
                                                       @RequestBody HomologacionesRequest request) {
        return ResponseEntity.ok(service.guardarHomologaciones(id, request.homologaciones()));
    }
}
