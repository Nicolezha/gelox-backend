package com.gelox.backend.controllers;

import com.gelox.backend.dto.ActualizarDespachoItemRequest;
import com.gelox.backend.dto.CrearDespachoRequest;
import com.gelox.backend.dto.CrearDespachoResponse;
import com.gelox.backend.dto.LiquidacionRequestDTO;
import com.gelox.backend.dto.LiquidacionResponseDTO;
import com.gelox.backend.dto.PlanillaImpresionResponseDTO;
import com.gelox.backend.entities.Usuario;
import com.gelox.backend.services.LiquidacionService;
import com.gelox.backend.services.PlanillaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/planillas")
@RequiredArgsConstructor
public class PlanillaController {

    private final PlanillaService planillaService;
    private final LiquidacionService liquidacionService;

    @PutMapping("/{id}/despacho")
    public ResponseEntity<Void> actualizarDespacho(
            @PathVariable UUID id,
            @Valid @RequestBody List<ActualizarDespachoItemRequest> items,
            @AuthenticationPrincipal Usuario usuario) {
        planillaService.actualizarDespacho(id, items, usuario);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/despacho")
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<CrearDespachoResponse> crearDespacho(
            @Valid @RequestBody CrearDespachoRequest req,
            @AuthenticationPrincipal Usuario usuario) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(planillaService.crearDespacho(req, usuario));
    }

    @PostMapping("/{id}/liquidar")
    public ResponseEntity<LiquidacionResponseDTO> liquidar(
            @PathVariable UUID id,
            @Valid @RequestBody LiquidacionRequestDTO request,
            @AuthenticationPrincipal Usuario usuario) {
        return ResponseEntity.ok(liquidacionService.liquidarPlanilla(id, request, usuario));
    }

    // ── GET /api/planillas/{id}/imprimir ──────────────────────────────────

    @GetMapping("/{id}/imprimir")
    public ResponseEntity<PlanillaImpresionResponseDTO> imprimir(
            @PathVariable UUID id,
            @AuthenticationPrincipal Usuario usuario) {
        return ResponseEntity.ok(planillaService.obtenerParaImpresion(id));
    }
}
