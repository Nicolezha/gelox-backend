package com.gelox.backend.catalogo;

import com.gelox.backend.catalogo.dto.CategoriaDTO;
import com.gelox.backend.catalogo.dto.CategoriaRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** RF64 — GET y POST /api/categorias. */
@RestController
@RequestMapping("/api/categorias")
@RequiredArgsConstructor
public class CategoriaController {

    private final CategoriaService categoriaService;

    @GetMapping
    public ResponseEntity<List<CategoriaDTO>> listar() {
        return ResponseEntity.ok(categoriaService.listar());
    }

    @PostMapping
    public ResponseEntity<CategoriaDTO> crear(@Valid @RequestBody CategoriaRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(categoriaService.crear(request));
    }
}
