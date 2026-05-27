package com.gelox.backend.controllers;

import com.gelox.backend.dto.CrearUsuarioDTO;
import com.gelox.backend.dto.EditarUsuarioDTO;
import com.gelox.backend.dto.UsuarioResponseDTO;
import com.gelox.backend.services.SupabaseStorageService;
import com.gelox.backend.services.UsuarioService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/usuarios")
@RequiredArgsConstructor
public class UsuarioController {

    private final UsuarioService        usuarioService;
    private final SupabaseStorageService storageService;

    @GetMapping
    public ResponseEntity<List<UsuarioResponseDTO>> listar() {
        return ResponseEntity.ok(usuarioService.listarUsuarios());
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> obtener(@PathVariable UUID id) {
        try {
            return ResponseEntity.ok(usuarioService.obtenerUsuario(id));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        } catch (ResponseStatusException e) {
            return ResponseEntity.status(e.getStatusCode()).body(e.getReason());
        }
    }

    // ── Crear usuario — JSON (sin foto) ──────────────────────────────────────
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> crear(@Valid @RequestBody CrearUsuarioDTO dto) {
        try {
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(usuarioService.crearUsuario(dto));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (ResponseStatusException e) {
            return ResponseEntity.status(e.getStatusCode()).body(e.getReason());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error al crear usuario: " + e.getMessage());
        }
    }

    // ── Crear usuario — multipart/form-data (con foto opcional) ─────────────
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> crearConFoto(
            @RequestParam                          String         nombre,
            @RequestParam                          String         correo,
            @RequestParam                          String         contrasenaTemporal,
            @RequestParam                          String         rol,
            @RequestParam(required = false)        MultipartFile  foto) {
        try {
            CrearUsuarioDTO dto = new CrearUsuarioDTO();
            dto.setNombre(nombre);
            dto.setCorreo(correo);
            dto.setContrasenaTemporal(contrasenaTemporal);
            dto.setRol(rol);

            if (foto != null && !foto.isEmpty()) {
                dto.setFotoUrl(storageService.subirImagen(foto, "usuarios"));
            }

            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(usuarioService.crearUsuario(dto));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (ResponseStatusException e) {
            return ResponseEntity.status(e.getStatusCode()).body(e.getReason());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error al crear usuario: " + e.getMessage());
        }
    }

    // ── Editar usuario — JSON (sin foto) ──────────────────────────────────────
    @PutMapping(value = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> editar(@PathVariable UUID id,
                                    @Valid @RequestBody EditarUsuarioDTO dto) {
        try {
            return ResponseEntity.ok(usuarioService.editarUsuario(id, dto));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (ResponseStatusException e) {
            return ResponseEntity.status(e.getStatusCode()).body(e.getReason());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error al editar usuario: " + e.getMessage());
        }
    }

    // ── Editar usuario — multipart/form-data (con foto opcional) ────────────
    @PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> editarConFoto(
            @PathVariable                          UUID           id,
            @RequestParam                          String         nombre,
            @RequestParam                          String         correo,
            @RequestParam                          String         rol,
            @RequestParam(required = false)        MultipartFile  foto) {
        try {
            EditarUsuarioDTO dto = new EditarUsuarioDTO();
            dto.setNombre(nombre);
            dto.setCorreo(correo);
            dto.setRol(rol);

            if (foto != null && !foto.isEmpty()) {
                dto.setFotoUrl(storageService.subirImagen(foto, "usuarios"));
            }

            return ResponseEntity.ok(usuarioService.editarUsuario(id, dto));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (ResponseStatusException e) {
            return ResponseEntity.status(e.getStatusCode()).body(e.getReason());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error al editar usuario: " + e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deshabilitar(@PathVariable UUID id) {
        try {
            usuarioService.deshabilitarUsuario(id);
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (ResponseStatusException e) {
            return ResponseEntity.status(e.getStatusCode()).body(e.getReason());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error al deshabilitar usuario: " + e.getMessage());
        }
    }

    @PatchMapping("/{id}/habilitar")
    public ResponseEntity<?> habilitar(@PathVariable UUID id) {
        try {
            usuarioService.habilitarUsuario(id);
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (ResponseStatusException e) {
            return ResponseEntity.status(e.getStatusCode()).body(e.getReason());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error al habilitar usuario: " + e.getMessage());
        }
    }
}