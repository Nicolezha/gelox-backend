package com.gelox.backend.controllers;

import com.gelox.backend.catalogo.dto.PagedResponse;
import com.gelox.backend.ventas.rural.ClienteRuralService;
import com.gelox.backend.ventas.rural.dto.ClienteRuralDTO;
import com.gelox.backend.ventas.rural.dto.EditarClienteRuralRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * GET  /api/clientes              → lista paginada de clientes rurales
 * GET  /api/clientes/buscar       → búsqueda por teléfono
 * PUT  /api/clientes/{id}         → editar datos de un cliente
 */
@RestController
@RequestMapping("/api/clientes")
@RequiredArgsConstructor
public class ClienteController {

    private final ClienteRuralService clienteRuralService;

    @GetMapping
    public ResponseEntity<PagedResponse<ClienteRuralDTO>> listar(
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size) {

        return ResponseEntity.ok(clienteRuralService.listarClientesPaginado(page, size));
    }

    @GetMapping("/buscar")
    public ResponseEntity<List<ClienteRuralDTO>> buscarPorTelefono(
            @RequestParam String telefono) {

        return ResponseEntity.ok(clienteRuralService.buscarPorTelefono(telefono));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ClienteRuralDTO> editar(
            @PathVariable UUID id,
            @Valid @RequestBody EditarClienteRuralRequest req) {

        return ResponseEntity.ok(clienteRuralService.editarCliente(id, req));
    }
}
