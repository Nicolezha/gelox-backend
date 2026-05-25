package com.gelox.backend.controllers;

import com.gelox.backend.dto.IniciarVentaRequest;
import com.gelox.backend.dto.VentaResponseDTO;
import com.gelox.backend.entities.Usuario;
import com.gelox.backend.services.VentaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/ventas")
@RequiredArgsConstructor
public class VentaController {

    private final VentaService ventaService;

    @PostMapping("/iniciar")
    public ResponseEntity<VentaResponseDTO> iniciar(
            @Valid @RequestBody IniciarVentaRequest req,
            @AuthenticationPrincipal Usuario usuario) {

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ventaService.iniciarVenta(req, usuario));
    }
}
