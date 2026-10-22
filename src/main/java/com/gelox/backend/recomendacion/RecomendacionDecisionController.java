package com.gelox.backend.recomendacion;

import com.gelox.backend.catalogo.dto.PagedResponse;
import com.gelox.backend.entities.EstadoRecomendacion;
import com.gelox.backend.entities.Usuario;
import com.gelox.backend.recomendacion.dto.DecidirItemRequest;
import com.gelox.backend.recomendacion.dto.ItemDTO;
import com.gelox.backend.recomendacion.dto.MetricasRecomendacionDTO;
import com.gelox.backend.recomendacion.dto.PedidoGeneradoDTO;
import com.gelox.backend.recomendacion.dto.RecomendacionDTO;
import com.gelox.backend.recomendacion.dto.RecomendacionResumenDTO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.UUID;

/**
 * RF55 — PATCH /api/recomendaciones/{id}/items/{itemId}   Decidir por ítem
 * RF55 — GET   /api/recomendaciones/{id}                  Detalle con ítems
 * RF56 — POST  /api/recomendaciones/{id}/pedido           Generar el pedido a proveedor
 * RF57 — GET   /api/recomendaciones                       Historial paginado
 * RF57 — GET   /api/recomendaciones/metricas              Métricas de precisión
 */
@RestController
@RequestMapping("/api/recomendaciones")
@RequiredArgsConstructor
public class RecomendacionDecisionController {

    private final DecisionRecomendacionService service;

    @GetMapping
    public ResponseEntity<PagedResponse<RecomendacionResumenDTO>> historial(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(required = false) EstadoRecomendacion estado,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size) {

        return ResponseEntity.ok(service.historial(desde, hasta, estado, page, size));
    }

    @GetMapping("/metricas")
    public ResponseEntity<MetricasRecomendacionDTO> metricas(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {

        return ResponseEntity.ok(service.metricas(desde, hasta));
    }

    @GetMapping("/{id}")
    public ResponseEntity<RecomendacionDTO> obtener(@PathVariable UUID id) {
        return ResponseEntity.ok(service.obtener(id));
    }

    @PatchMapping("/{id}/items/{itemId}")
    public ResponseEntity<ItemDTO> decidir(
            @PathVariable UUID id,
            @PathVariable UUID itemId,
            @RequestBody @Valid DecidirItemRequest req) {

        return ResponseEntity.ok(service.decidir(id, itemId, req));
    }

    @PostMapping("/{id}/pedido")
    public ResponseEntity<PedidoGeneradoDTO> generarPedido(
            @PathVariable UUID id,
            @AuthenticationPrincipal Usuario usuario) {

        return ResponseEntity.ok(service.generarPedido(id, usuario));
    }
}
