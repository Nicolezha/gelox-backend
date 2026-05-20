package com.gelox.backend.services;

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
public class RecuperacionService {

    private final UsuarioRepository usuarioRepository;
    private final FirebaseAuth firebaseAuth;
    private final EmailService emailService;

    public void solicitarRecuperacion(String correo) {
        if (!usuarioRepository.existsByCorreo(correo)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Correo no registrado");
        }

        try {
            String enlace = firebaseAuth.generatePasswordResetLink(correo);
            emailService.enviarCorreoRecuperacion(correo, enlace);
        } catch (FirebaseAuthException e) {
            log.error("Error al generar enlace de recuperación para {}: {}", correo, e.getMessage());
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR, "No se pudo generar el enlace de recuperación");
        } catch (RuntimeException e) {
            log.error("Error al enviar correo de recuperación a {}: {}", correo, e.getMessage());
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR, "No se pudo enviar el correo de recuperación");
        }
    }
}