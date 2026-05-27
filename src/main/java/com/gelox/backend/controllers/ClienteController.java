package com.gelox.backend.controllers;

import com.gelox.backend.catalogo.dto.PagedResponse;
import com.gelox.backend.ventas.rural.ClienteRuralService;
import com.gelox.backend.ventas.rural.dto.ClienteRuralDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Endpoints de clientes para el frontend de ventanilla.
 *
 * GET /api/clientes?page=0&size=100         → lista paginada de clientes rurales
 * GET /api/clientes/buscar?telefono={valor} → búsqueda por teléfono (requiere valor no vacío)
 */
@RestController
@RequestMapping("/api/clientes")
@RequiredArgsConstructor
public class ClienteController {

    private final ClienteRuralService clienteRuralService;

    /**
     * Lista todos los clientes rurales con paginación.
     * Parámetros opcionales: page (default 0), size (default 20, máx 100).
     */
    @GetMapping
    public ResponseEntity<PagedResponse<ClienteRuralDTO>> listar(
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size) {

        return ResponseEntity.ok(clienteRuralService.listarClientesPaginado(page, size));
    }

    /**
     * Busca clientes por teléfono (coincidencia parcial, case-insensitive).
     * Retorna 400 Bad Request si el parámetro llega vacío.
     */
    @GetMapping("/buscar")
    public ResponseEntity<List<ClienteRuralDTO>> buscarPorTelefono(
            @RequestParam String telefono) {

        return ResponseEntity.ok(clienteRuralService.buscarPorTelefono(telefono));
    }
}
