package com.gelox.backend.gobierno;

import com.gelox.backend.gobierno.dto.PlanDTO;
import com.gelox.backend.gobierno.dto.PlanRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * RF70 — Planes de suscripción. Solo SUPERADMINISTRADOR (SecurityConfig: /api/plataforma/**,
 * y @RequiereRol en PlanService).
 */
@RestController
@RequestMapping("/api/plataforma/planes")
@RequiredArgsConstructor
public class PlanController {

    private final PlanService planService;

    @GetMapping
    public ResponseEntity<List<PlanDTO>> listar() {
        return ResponseEntity.ok(planService.listar());
    }

    @GetMapping("/{id}")
    public ResponseEntity<PlanDTO> obtener(@PathVariable UUID id) {
        return ResponseEntity.ok(planService.obtener(id));
    }

    @PostMapping
    public ResponseEntity<PlanDTO> crear(@RequestBody PlanRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(planService.crear(req));
    }

    @PutMapping("/{id}")
    public ResponseEntity<PlanDTO> editar(@PathVariable UUID id, @RequestBody PlanRequest req) {
        return ResponseEntity.ok(planService.editar(id, req));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable UUID id) {
        planService.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}
