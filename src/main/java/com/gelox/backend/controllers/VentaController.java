package com.gelox.backend.controllers;

import com.gelox.backend.dto.*;
import com.gelox.backend.entities.Usuario;
import com.gelox.backend.services.VentaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/ventas")
@RequiredArgsConstructor
public class VentaController {

    private final VentaService ventaService;

    @GetMapping("/catalogo")
    public ResponseEntity<List<CatalogoVentaDTO>> getCatalogo() {
        return ResponseEntity.ok(ventaService.getCatalogo());
    }

    @PostMapping("/confirmar")
    public ResponseEntity<ConfirmarVentaResponse> confirmar(
            @Valid @RequestBody ConfirmarVentaRequest req,
            @AuthenticationPrincipal Usuario usuario) {

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ventaService.confirmarVenta(req, usuario));
    }

    @PostMapping("/calcular")
    public ResponseEntity<CalcularVentaResponse> calcular(
            @Valid @RequestBody CalcularVentaRequest req) {

        return ResponseEntity.ok(ventaService.calcularVenta(req));
    }

    @PostMapping("/iniciar")
    public ResponseEntity<VentaResponseDTO> iniciar(
            @Valid @RequestBody IniciarVentaRequest req,
            @AuthenticationPrincipal Usuario usuario) {

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ventaService.iniciarVenta(req, usuario));
    }
}
