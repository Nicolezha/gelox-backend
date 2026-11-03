package com.gelox.backend.plataforma;

import com.gelox.backend.plataforma.dto.CambiarEstadoRequest;
import com.gelox.backend.plataforma.dto.CrearDistribuidoraRequest;
import com.gelox.backend.plataforma.dto.DistribuidoraDTO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * RF58/RF59 — Panel del Superadministrador: aprovisionamiento y ciclo de vida
 * de distribuidoras. Protegido en SecurityConfig con hasRole("SUPERADMINISTRADOR").
 */
@RestController
@RequestMapping("/api/plataforma/distribuidoras")
@RequiredArgsConstructor
public class PlataformaController {

    private final DistribuidoraService distribuidoraService;

    @PostMapping
    public ResponseEntity<DistribuidoraDTO> crear(@Valid @RequestBody CrearDistribuidoraRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(distribuidoraService.aprovisionar(req));
    }

    @GetMapping
    public ResponseEntity<List<DistribuidoraDTO>> listar(
            @RequestParam(required = false) String estado,
            @RequestParam(required = false) String q) {
        return ResponseEntity.ok(distribuidoraService.listar(estado, q));
    }

    @GetMapping("/{id}")
    public ResponseEntity<DistribuidoraDTO> obtener(@PathVariable UUID id) {
        return ResponseEntity.ok(distribuidoraService.obtener(id));
    }

    @PatchMapping("/{id}/estado")
    public ResponseEntity<DistribuidoraDTO> cambiarEstado(
            @PathVariable UUID id,
            @Valid @RequestBody CambiarEstadoRequest req) {
        return ResponseEntity.ok(distribuidoraService.cambiarEstado(id, req));
    }

    @PostMapping("/{id}/reenviar-acceso")
    public ResponseEntity<DistribuidoraDTO> reenviarAcceso(@PathVariable UUID id) {
        return ResponseEntity.ok(distribuidoraService.reenviarAcceso(id));
    }
}
