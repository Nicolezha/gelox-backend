package com.gelox.backend.controllers;

import com.gelox.backend.dto.UsuarioDTO;
import com.gelox.backend.entities.Usuario;
import com.gelox.backend.services.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/verificar")
    public ResponseEntity<UsuarioDTO> verificar(@AuthenticationPrincipal Usuario usuario) {
        UsuarioDTO dto = authService.autenticarUsuario(usuario.getFirebaseUid());
        return ResponseEntity.ok(dto);
    }

    @GetMapping("/perfil")
    public ResponseEntity<UsuarioDTO> perfil(@AuthenticationPrincipal Usuario usuario) {
        UsuarioDTO dto = authService.obtenerPerfil(usuario.getFirebaseUid());
        return ResponseEntity.ok(dto);
    }

    @PostMapping("/cerrar-sesion")
    public ResponseEntity<Map<String, String>> cerrarSesion(@AuthenticationPrincipal Usuario usuario) {
        authService.cerrarSesion(usuario.getFirebaseUid());
        SecurityContextHolder.clearContext();
        return ResponseEntity.ok(Map.of("mensaje", "Sesión cerrada correctamente"));
    }
}