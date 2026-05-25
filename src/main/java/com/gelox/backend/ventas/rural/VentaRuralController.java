package com.gelox.backend.ventas.rural;

import com.gelox.backend.entities.Usuario;
import com.gelox.backend.ventas.rural.dto.ConfirmarPedidoRuralRequest;
import com.gelox.backend.ventas.rural.dto.PedidoRuralResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * RF32 — Endpoints de pedidos rurales.
 *
 * GET  /api/ventas/rural   → listar pedidos rurales (paginado, ?estadoEnvio=)
 * POST /api/ventas/rural   → confirmar pedido rural completo (transacción atómica)
 */
@RestController
@RequestMapping("/api/ventas/rural")
@RequiredArgsConstructor
public class VentaRuralController {

    private final VentaRuralService ventaRuralService;

    /** RF32 — Listar pedidos rurales con filtros opcionales. */
    @GetMapping
    public ResponseEntity<List<PedidoRuralResponse>> listar(
            @RequestParam(required = false) String estadoEnvio,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size) {

        return ResponseEntity.ok(
                ventaRuralService.listarPedidos(estadoEnvio, page, size));
    }

    /** RF32 — Confirmar pedido rural: registra venta, ítems, pedido_rural y descuenta inventario. */
    @PostMapping
    public ResponseEntity<PedidoRuralResponse> confirmar(
            @Valid @RequestBody ConfirmarPedidoRuralRequest request,
            @AuthenticationPrincipal Usuario usuario) {

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ventaRuralService.confirmarPedidoRural(request, usuario));
    }
}
