package com.gelox.backend.controllers;

import com.gelox.backend.entities.Usuario;
import com.gelox.backend.voz.VozService;
import com.gelox.backend.voz.dto.VozConfirmarRequest;
import com.gelox.backend.voz.dto.VozConfirmarResponse;
import com.gelox.backend.voz.dto.VozInterpretarRequest;
import com.gelox.backend.voz.dto.VozInterpretarResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Comandos de voz: solo exige estar autenticado (/api/voz/**); cada handler
 * filtra el rol que necesita con @RequiereRol.
 */
@RestController
@RequestMapping("/api/voz")
@RequiredArgsConstructor
public class VozController {

    private final VozService vozService;

    @PostMapping("/interpretar")
    public ResponseEntity<VozInterpretarResponse> interpretar(
            @Valid @RequestBody VozInterpretarRequest req,
            @AuthenticationPrincipal Usuario usuario) {

        return ResponseEntity.ok(vozService.interpretar(req, usuario));
    }

    @PostMapping("/confirmar")
    public ResponseEntity<VozConfirmarResponse> confirmar(
            @Valid @RequestBody VozConfirmarRequest req,
            @AuthenticationPrincipal Usuario usuario) {

        return ResponseEntity.ok(vozService.confirmar(req, usuario));
    }
}
