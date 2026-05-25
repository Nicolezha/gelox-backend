package com.gelox.backend.controllers;

import com.gelox.backend.dto.CrearProductoDTO;
import com.gelox.backend.dto.EditarProductoDTO;
import com.gelox.backend.dto.ProductoResponseDTO;
import com.gelox.backend.entities.Usuario;
import com.gelox.backend.services.ProductoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/productos")
@RequiredArgsConstructor
public class ProductoController {

    private final ProductoService productoService;

    @GetMapping
    public ResponseEntity<List<ProductoResponseDTO>> listar() {
        return ResponseEntity.ok(productoService.listarProductos());
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProductoResponseDTO> obtener(@PathVariable UUID id) {
        return ResponseEntity.ok(productoService.obtenerProducto(id));
    }

    @PostMapping
    public ResponseEntity<ProductoResponseDTO> crear(
            @Valid @RequestBody CrearProductoDTO dto,
            @AuthenticationPrincipal Usuario usuario) {
        ProductoResponseDTO response = productoService.crearProducto(dto, usuario.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{id}")
    public ResponseEntity<ProductoResponseDTO> editar(
            @PathVariable UUID id,
            @Valid @RequestBody EditarProductoDTO dto,
            @AuthenticationPrincipal Usuario usuario) {
        return ResponseEntity.ok(productoService.editarProducto(id, dto, usuario.getId()));
    }

    @PatchMapping("/{id}/desactivar")
    public ResponseEntity<Map<String, String>> desactivar(
            @PathVariable UUID id,
            @AuthenticationPrincipal Usuario usuario) {
        productoService.desactivarProducto(id, usuario.getId());
        return ResponseEntity.ok(Map.of("mensaje", "Producto desactivado correctamente"));
    }

    @PatchMapping("/{id}/activar")
    public ResponseEntity<Map<String, String>> activar(
            @PathVariable UUID id,
            @AuthenticationPrincipal Usuario usuario) {
        productoService.activarProducto(id, usuario.getId());
        return ResponseEntity.ok(Map.of("mensaje", "Producto activado correctamente"));
    }
}