package com.gelox.backend.proveedores;

import com.gelox.backend.proveedores.dto.ProveedorDTO;
import com.gelox.backend.proveedores.dto.ProveedorRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * RF63 — /api/proveedores. Roles ADMINISTRADOR y ENCARGADO_INVENTARIO (los verifica el servicio).
 */
@RestController
@RequestMapping("/api/proveedores")
@RequiredArgsConstructor
public class ProveedorController {

    private final ProveedorService proveedorService;

    @GetMapping
    public ResponseEntity<List<ProveedorDTO>> listar(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Boolean activo) {
        return ResponseEntity.ok(proveedorService.listar(q, activo));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProveedorDTO> obtener(@PathVariable UUID id) {
        return ResponseEntity.ok(proveedorService.obtener(id));
    }

    @PostMapping
    public ResponseEntity<ProveedorDTO> crear(@Valid @RequestBody ProveedorRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(proveedorService.crear(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ProveedorDTO> editar(@PathVariable UUID id,
                                               @Valid @RequestBody ProveedorRequest request) {
        return ResponseEntity.ok(proveedorService.editar(id, request));
    }

    /** Activa al proveedor; con {@code ?activo=false} lo inactiva. */
    @PatchMapping("/{id}/activar")
    public ResponseEntity<ProveedorDTO> activar(@PathVariable UUID id,
                                                @RequestParam(defaultValue = "true") boolean activo) {
        return ResponseEntity.ok(proveedorService.activar(id, activo));
    }
}
