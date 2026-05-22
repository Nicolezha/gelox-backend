package com.gelox.backend.controllers;

import com.gelox.backend.dto.InventarioProductoDTO;
import com.gelox.backend.entities.Usuario;
import com.gelox.backend.services.InventarioService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/inventario")
@RequiredArgsConstructor
public class InventarioController {

    private final InventarioService inventarioService;

    /**
     * GET /api/inventario/productos
     * Roles permitidos: ADMINISTRADOR, ENCARGADO_INVENTARIO
     * Retorna la lista de productos activos con su estado de stock.
     * El campo precioCosto solo se incluye para rol ADMINISTRADOR.
     */
    @GetMapping("/productos")
    public ResponseEntity<List<InventarioProductoDTO>> listarInventario(
            @AuthenticationPrincipal Usuario usuario) {

        String rol = usuario.getRol().name();
        List<InventarioProductoDTO> inventario = inventarioService.listarInventario(rol);
        return ResponseEntity.ok(inventario);
    }
}
