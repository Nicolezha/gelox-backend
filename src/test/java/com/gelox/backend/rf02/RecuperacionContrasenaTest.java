package com.gelox.backend.rf02;

import com.gelox.backend.repositories.UsuarioRepository;
import com.gelox.backend.services.EmailService;
import com.gelox.backend.services.RecuperacionService;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * RF02 — Recuperación de contraseña.
 * Cubre CP05, CP06, CP07.
 */
@ExtendWith(MockitoExtension.class)
class RecuperacionContrasenaTest {

    @Mock
    UsuarioRepository usuarioRepository;

    @Mock
    FirebaseAuth firebaseAuth;

    @Mock
    EmailService emailService;

    @InjectMocks
    RecuperacionService recuperacionService;

    private static final String CORREO_EXISTENTE   = "encargado@gelox-test.com";
    private static final String CORREO_INEXISTENTE = "noexiste@gelox-test.com";
    private static final String ENLACE_RESET       = "https://firebase.link/reset?oobCode=abc123";

    // -----------------------------------------------------------------------
    // CP05 — recuperacion-correo-existente
    // -----------------------------------------------------------------------
    @Test
    @DisplayName("CP05 - recuperación con correo existente: Firebase genera el enlace y se envía el correo")
    void cp05_recuperacionCorreoExistente() throws FirebaseAuthException {
        when(usuarioRepository.existsByCorreo(CORREO_EXISTENTE)).thenReturn(true);
        when(firebaseAuth.generatePasswordResetLink(CORREO_EXISTENTE)).thenReturn(ENLACE_RESET);

        assertThatNoException().isThrownBy(
                () -> recuperacionService.solicitarRecuperacion(CORREO_EXISTENTE)
        );

        verify(firebaseAuth).generatePasswordResetLink(CORREO_EXISTENTE);
        verify(emailService).enviarCorreoRecuperacion(CORREO_EXISTENTE, ENLACE_RESET);
    }

    // -----------------------------------------------------------------------
    // CP05-extra — respuesta no expone información sensible (enlace no retornado)
    // -----------------------------------------------------------------------
    @Test
    @DisplayName("CP05-extra - el servicio no retorna el enlace al caller y delega el envío al EmailService")
    void cp05Extra_enlaceNoExpuesto() throws FirebaseAuthException {
        when(usuarioRepository.existsByCorreo(CORREO_EXISTENTE)).thenReturn(true);
        when(firebaseAuth.generatePasswordResetLink(CORREO_EXISTENTE)).thenReturn(ENLACE_RESET);

        // El método es void: no retorna el enlace al controller.
        recuperacionService.solicitarRecuperacion(CORREO_EXISTENTE);

        verify(firebaseAuth, times(1)).generatePasswordResetLink(CORREO_EXISTENTE);
        // El enlace es enviado por correo, no expuesto al caller
        verify(emailService, times(1)).enviarCorreoRecuperacion(CORREO_EXISTENTE, ENLACE_RESET);
    }

    // -----------------------------------------------------------------------
    // CP06 — recuperacion-correo-inexistente → HTTP 404
    // -----------------------------------------------------------------------
    @Test
    @DisplayName("CP06 - correo inexistente: servicio lanza HTTP 404 y NO llama a Firebase ni a EmailService")
    void cp06_recuperacionCorreoInexistente() throws FirebaseAuthException {
        when(usuarioRepository.existsByCorreo(CORREO_INEXISTENTE)).thenReturn(false);

        assertThatThrownBy(() -> recuperacionService.solicitarRecuperacion(CORREO_INEXISTENTE))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
                });

        verify(firebaseAuth, never()).generatePasswordResetLink(any());
        verify(emailService, never()).enviarCorreoRecuperacion(any(), any());
    }

    // -----------------------------------------------------------------------
    // CP07 — Firebase lanza excepción → HTTP 500
    // -----------------------------------------------------------------------
    @Test
    @DisplayName("CP07 - Firebase falla al generar el enlace → servicio responde HTTP 500")
    void cp07_enlaceExpirado_firebaseRechaza() throws FirebaseAuthException {
        when(usuarioRepository.existsByCorreo(CORREO_EXISTENTE)).thenReturn(true);

        FirebaseAuthException firebaseEx = mock(FirebaseAuthException.class);
        when(firebaseAuth.generatePasswordResetLink(CORREO_EXISTENTE)).thenThrow(firebaseEx);

        assertThatThrownBy(() -> recuperacionService.solicitarRecuperacion(CORREO_EXISTENTE))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
                });

        // Si Firebase falla, el correo no debe enviarse
        verify(emailService, never()).enviarCorreoRecuperacion(any(), any());
    }

    // -----------------------------------------------------------------------
    // CP07-b — EmailService falla al enviar → HTTP 500
    // -----------------------------------------------------------------------
    @Test
    @DisplayName("CP07-b - SMTP falla al enviar el correo → servicio responde HTTP 500")
    void cp07b_emailServiceFalla() throws FirebaseAuthException {
        when(usuarioRepository.existsByCorreo(CORREO_EXISTENTE)).thenReturn(true);
        when(firebaseAuth.generatePasswordResetLink(CORREO_EXISTENTE)).thenReturn(ENLACE_RESET);
        doThrow(new RuntimeException("SMTP connection refused"))
                .when(emailService).enviarCorreoRecuperacion(CORREO_EXISTENTE, ENLACE_RESET);

        assertThatThrownBy(() -> recuperacionService.solicitarRecuperacion(CORREO_EXISTENTE))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
                });
    }
}
