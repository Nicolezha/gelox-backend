package com.gelox.backend.services;

import com.gelox.backend.dto.UsuarioDTO;
import com.gelox.backend.entities.TipoEvento;
import com.gelox.backend.entities.Usuario;
import com.gelox.backend.repositories.UsuarioRepository;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UsuarioRepository usuarioRepository;
    private final FirebaseAuth firebaseAuth;
    private final EventoSistemaService eventoSistemaService;

    public UsuarioDTO autenticarUsuario(String firebaseUid) {
        Usuario usuario = usuarioRepository.findByFirebaseUid(firebaseUid)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED, "Usuario no registrado en el sistema"));

        if (!usuario.getActivo()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "La cuenta está desactivada");
        }

        eventoSistemaService.registrarEvento(
                TipoEvento.INICIO_SESION,
                "Inicio de sesión: " + usuario.getNombre() + " (" + usuario.getCorreo() + ")",
                usuario.getId()
        );

        return new UsuarioDTO(
                usuario.getId(),
                usuario.getNombre(),
                usuario.getRol(),
                usuario.getFotoUrl(),
                usuario.getCorreo(),
                usuario.getTelefono()
        );
    }

    public UsuarioDTO obtenerPerfil(String firebaseUid) {
        Usuario usuario = usuarioRepository.findByFirebaseUid(firebaseUid)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED, "Usuario no registrado en el sistema"));

        if (!usuario.getActivo()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "La cuenta está desactivada");
        }

        return new UsuarioDTO(
                usuario.getId(),
                usuario.getNombre(),
                usuario.getRol(),
                usuario.getFotoUrl(),
                usuario.getCorreo(),
                usuario.getTelefono()
        );
    }

    public void cerrarSesion(String firebaseUid) {
        try {
            firebaseAuth.revokeRefreshTokens(firebaseUid);
            usuarioRepository.findByFirebaseUid(firebaseUid).ifPresent(usuario ->
                    eventoSistemaService.registrarEvento(
                            TipoEvento.CIERRE_SESION,
                            "Cierre de sesión: " + usuario.getNombre() + " (" + usuario.getCorreo() + ")",
                            usuario.getId()
                    )
            );
        } catch (FirebaseAuthException e) {
            log.error("Error al revocar tokens para {}: {}", firebaseUid, e.getMessage());
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "No se pudo cerrar la sesión");
        }
    }
}