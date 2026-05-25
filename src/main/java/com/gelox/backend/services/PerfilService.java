package com.gelox.backend.services;

import com.gelox.backend.dto.ActualizarPerfilDTO;
import com.gelox.backend.dto.CambioContrasenaDTO;
import com.gelox.backend.entities.TipoEvento;
import com.gelox.backend.entities.Usuario;
import com.gelox.backend.exceptions.ContrasenaActualIncorrectaException;
import com.gelox.backend.exceptions.ContrasenaNoCoincideException;
import com.gelox.backend.exceptions.CorreoDuplicadoException;
import com.gelox.backend.repositories.UsuarioRepository;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.UserRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;
import java.util.UUID;

@Service
public class PerfilService {

    private final UsuarioRepository usuarioRepository;
    private final FirebaseAuth firebaseAuth;
    private final EventoSistemaService eventoSistemaService;

    @Value("${supabase.url}")
    private String supabaseUrl;

    @Value("${supabase.service-key}")
    private String supabaseServiceKey;

    @Value("${firebase.web-api-key}")
    private String firebaseWebApiKey;

    public PerfilService(UsuarioRepository usuarioRepository, FirebaseAuth firebaseAuth,
                         EventoSistemaService eventoSistemaService) {
        this.usuarioRepository = usuarioRepository;
        this.firebaseAuth = firebaseAuth;
        this.eventoSistemaService = eventoSistemaService;
    }

    @Transactional
    public Usuario actualizarPerfil(UUID userId, ActualizarPerfilDTO dto) {
        Usuario usuario = usuarioRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado"));

        if (!usuario.getCorreo().equalsIgnoreCase(dto.getCorreo())) {
            usuarioRepository.findByCorreo(dto.getCorreo()).ifPresent(otro -> {
                if (!otro.getId().equals(userId)) {
                    throw new CorreoDuplicadoException("El correo ya está registrado por otro usuario");
                }
            });
        }

        usuario.setNombre(dto.getNombre());
        usuario.setCorreo(dto.getCorreo());
        usuario.setTelefono(dto.getTelefono());

        if (dto.getFotoUrl() != null && !dto.getFotoUrl().isBlank()) {
            usuario.setFotoUrl(dto.getFotoUrl());
        }

        Usuario actualizado = usuarioRepository.save(usuario);
        eventoSistemaService.registrarEvento(
                TipoEvento.ACTUALIZACION_PERFIL,
                "Perfil actualizado: " + actualizado.getNombre() + " (" + actualizado.getCorreo() + ")",
                actualizado.getId()
        );
        return actualizado;
    }

    public void cambiarContrasena(String firebaseUid, CambioContrasenaDTO dto) {
        if (!dto.getNuevaContrasena().equals(dto.getConfirmacion())) {
            throw new ContrasenaNoCoincideException("La nueva contraseña y su confirmación no coinciden");
        }

        String email = usuarioRepository.findByFirebaseUid(firebaseUid)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado"))
                .getCorreo();

        verificarContrasenaActual(email, dto.getContrasenaActual());

        try {
            UserRecord.UpdateRequest updateRequest = new UserRecord.UpdateRequest(firebaseUid)
                    .setPassword(dto.getNuevaContrasena());
            firebaseAuth.updateUser(updateRequest);

            usuarioRepository.findByFirebaseUid(firebaseUid).ifPresent(usuario ->
                    eventoSistemaService.registrarEvento(
                            TipoEvento.CAMBIO_CONTRASENA,
                            "Cambio de contraseña realizado por: " + usuario.getNombre()
                                    + " (" + usuario.getCorreo() + ")",
                            usuario.getId()
                    )
            );

        } catch (com.google.firebase.auth.FirebaseAuthException e) {
            throw new IllegalArgumentException("Error al actualizar la contraseña: " + e.getMessage());
        }
    }

    private void verificarContrasenaActual(String email, String contrasena) {
        String url = "https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=" + firebaseWebApiKey;

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> body = Map.of(
                "email", email,
                "password", contrasena,
                "returnSecureToken", false
        );

        try {
            new RestTemplate().postForEntity(url, new HttpEntity<>(body, headers), String.class);
        } catch (HttpClientErrorException e) {
            throw new ContrasenaActualIncorrectaException("La contraseña actual es incorrecta");
        }
    }

    public String subirFotoPerfil(UUID userId, MultipartFile foto) {
        try {
            String nombreArchivo = "perfil_" + userId + "_" + System.currentTimeMillis()
                    + getExtension(foto.getOriginalFilename());
            String uploadUrl = supabaseUrl + "/storage/v1/object/fotos-perfil/" + nombreArchivo;

            RestTemplate restTemplate = new RestTemplate();
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + supabaseServiceKey);
            headers.setContentType(MediaType.parseMediaType(foto.getContentType()));

            HttpEntity<byte[]> requestEntity = new HttpEntity<>(foto.getBytes(), headers);
            restTemplate.exchange(uploadUrl, HttpMethod.POST, requestEntity, String.class);

            return supabaseUrl + "/storage/v1/object/public/fotos-perfil/" + nombreArchivo;

        } catch (Exception e) {
            throw new IllegalArgumentException("Error al subir la foto: " + e.getMessage());
        }
    }

    @Transactional
    public void actualizarFotoUrl(UUID userId, String fotoUrl) {
        Usuario usuario = usuarioRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado"));
        usuario.setFotoUrl(fotoUrl);
        usuarioRepository.save(usuario);
    }

    private String getExtension(String filename) {
        if (filename == null || !filename.contains(".")) return ".jpg";
        return filename.substring(filename.lastIndexOf("."));
    }
}
