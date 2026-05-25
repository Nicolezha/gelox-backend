package com.gelox.backend.ventas.rural;

import com.gelox.backend.ventas.rural.dto.ClienteRuralDTO;
import com.gelox.backend.ventas.rural.dto.CrearClienteRuralRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * RF33 — Endpoints de clientes rurales.
 *
 * GET  /api/ventas/clientes-rurales        → listar (con ?q= opcional)
 * POST /api/ventas/clientes-rurales        → registrar nuevo cliente
 */
@RestController
@RequestMapping("/api/ventas/clientes-rurales")
@RequiredArgsConstructor
public class ClienteRuralController {

    private final ClienteRuralService clienteRuralService;

    /** RF33 — Listar todos los clientes rurales, con búsqueda opcional por nombre o teléfono. */
    @GetMapping
    public ResponseEntity<List<ClienteRuralDTO>> listar(
            @RequestParam(required = false) String q) {

        return ResponseEntity.ok(clienteRuralService.listarClientes(q));
    }

    /** RF33 — Registrar un nuevo cliente rural (recurrente). */
    @PostMapping
    public ResponseEntity<ClienteRuralDTO> crear(
            @Valid @RequestBody CrearClienteRuralRequest request) {

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(clienteRuralService.crearCliente(request));
    }
}
