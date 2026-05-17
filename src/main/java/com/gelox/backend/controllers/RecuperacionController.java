package com.gelox.backend.controllers;

import com.gelox.backend.dto.RecuperacionRequestDTO;
import com.gelox.backend.services.RecuperacionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class RecuperacionController {

    private final RecuperacionService recuperacionService;

    @PostMapping("/recuperar-contrasena")
    public ResponseEntity<Map<String, String>> recuperarContrasena(
            @Valid @RequestBody RecuperacionRequestDTO request) {

        recuperacionService.solicitarRecuperacion(request.correo());
        return ResponseEntity.ok(Map.of("mensaje", "Enlace de recuperación enviado al correo registrado"));
    }
}