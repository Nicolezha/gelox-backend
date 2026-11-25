package com.gelox.backend.catalogo;

import com.gelox.backend.catalogo.dto.UnidadMedidaDTO;
import com.gelox.backend.catalogo.dto.UnidadMedidaRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** RF64 — GET y POST /api/unidades. */
@RestController
@RequestMapping("/api/unidades")
@RequiredArgsConstructor
public class UnidadMedidaController {

    private final UnidadMedidaService unidadMedidaService;

    @GetMapping
    public ResponseEntity<List<UnidadMedidaDTO>> listar() {
        return ResponseEntity.ok(unidadMedidaService.listar());
    }

    @PostMapping
    public ResponseEntity<UnidadMedidaDTO> crear(@Valid @RequestBody UnidadMedidaRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(unidadMedidaService.crear(request));
    }
}
