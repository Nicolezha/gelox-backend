package com.gelox.backend.controllers;

import com.gelox.backend.dto.ActualizarPerfilDTO;
import com.gelox.backend.dto.CambioContrasenaDTO;
import com.gelox.backend.entities.Usuario;
import com.gelox.backend.services.PerfilService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/perfil")
public class PerfilController {

    private final PerfilService perfilService;

    public PerfilController(PerfilService perfilService) {
        this.perfilService = perfilService;
    }

    /**
     * PUT /api/perfil/{userId}
     * Actualiza nombre, correo, teléfono y opcionalmente fotoUrl.
     * Requiere autenticación (Firebase ID Token en header Authorization).
     */
    @PutMapping("/{userId}")
    public ResponseEntity<Usuario> actualizarPerfil(
            @PathVariable UUID userId,
            @Valid @RequestBody ActualizarPerfilDTO dto) {
        Usuario actualizado = perfilService.actualizarPerfil(userId, dto);
        return ResponseEntity.ok(actualizado);
    }

    /**
     * POST /api/perfil/{userId}/foto
     * Sube foto de perfil a Supabase Storage y actualiza foto_url en BD.
     * Requiere autenticación.
     */
    @PostMapping("/{userId}/foto")
    public ResponseEntity<Map<String, String>> subirFoto(
            @PathVariable UUID userId,
            @RequestParam("foto") MultipartFile foto) {
        String fotoUrl = perfilService.subirFotoPerfil(userId, foto);
        perfilService.actualizarFotoUrl(userId, fotoUrl);
        return ResponseEntity.ok(Map.of("fotoUrl", fotoUrl));
    }

    /**
     * PUT /api/perfil/{firebaseUid}/contrasena
     * Cambia la contraseña del usuario en Firebase Auth.
     * Requiere autenticación.
     */
    @PutMapping("/{firebaseUid}/contrasena")
    public ResponseEntity<Map<String, String>> cambiarContrasena(
            @PathVariable String firebaseUid,
            @Valid @RequestBody CambioContrasenaDTO dto) {
        perfilService.cambiarContrasena(firebaseUid, dto);
        return ResponseEntity.ok(Map.of("mensaje", "Contraseña actualizada correctamente"));
    }
}
