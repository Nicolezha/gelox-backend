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
            log.info("Enlace Firebase generado para {}. Enviando correo...", correo);
            emailService.enviarCorreoRecuperacion(correo, enlace);
            log.info("Correo de recuperación enviado exitosamente a {}", correo);
        } catch (FirebaseAuthException e) {
            log.error("Error Firebase al generar enlace para {}: [{}] {}", correo, e.getErrorCode(), e.getMessage());
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR, "No se pudo generar el enlace de recuperación");
        } catch (RuntimeException e) {
            log.error("Error SMTP al enviar correo a {}: {} - Causa: {}", correo, e.getMessage(),
                    e.getCause() != null ? e.getCause().getMessage() : "sin causa");
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR, "No se pudo enviar el correo de recuperación");
        }
    }
}