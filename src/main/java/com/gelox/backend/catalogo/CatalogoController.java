package com.gelox.backend.catalogo;

import com.gelox.backend.catalogo.dto.CatalogoProductoDTO;
import com.gelox.backend.catalogo.dto.CrearProductoRequest;
import com.gelox.backend.catalogo.dto.EditarProductoRequest;
import com.gelox.backend.catalogo.dto.PagedResponse;
import com.gelox.backend.entities.Usuario;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * RF18 — GET  /api/catalogo/productos        Listar con filtro y paginación
 * RF19 — POST /api/catalogo/productos        Crear producto
 * RF20 — PUT  /api/catalogo/productos/{id}   Editar producto
 * RF20 — DELETE /api/catalogo/productos/{id} Eliminar (soft delete)
 */
@RestController
@RequestMapping("/api/catalogo/productos")
@RequiredArgsConstructor
public class CatalogoController {

    private final CatalogoService catalogoService;

    // ── RF18 ──────────────────────────────────────────────────────
    @GetMapping
    public ResponseEntity<PagedResponse<CatalogoProductoDTO>> listar(
            @RequestParam(required = false) String categoria,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal Usuario usuario) {

        return ResponseEntity.ok(
                catalogoService.listarProductos(categoria, page, size, usuario));
    }

    // ── RF19 ──────────────────────────────────────────────────────
    @PostMapping
    public ResponseEntity<CatalogoProductoDTO> crear(
            @Valid @RequestBody CrearProductoRequest request,
            @AuthenticationPrincipal Usuario usuario) {

        CatalogoProductoDTO dto = catalogoService.crearProducto(request, usuario);
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    // ── RF20 — editar ─────────────────────────────────────────────
    @PutMapping("/{id}")
    public ResponseEntity<CatalogoProductoDTO> editar(
            @PathVariable UUID id,
            @Valid @RequestBody EditarProductoRequest request,
            @AuthenticationPrincipal Usuario usuario) {

        return ResponseEntity.ok(catalogoService.editarProducto(id, request, usuario));
    }

    // ── RF20 — eliminar ───────────────────────────────────────────
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(
            @PathVariable UUID id,
            @AuthenticationPrincipal Usuario usuario) {

        catalogoService.eliminarProducto(id);
        return ResponseEntity.noContent().build();
    }
}
