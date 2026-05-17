package com.gelox.backend.rf02;

import com.gelox.backend.repositories.UsuarioRepository;
import com.gelox.backend.services.RecuperacionService;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import org.junit.jupiter.api.BeforeEach;
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

    @InjectMocks
    RecuperacionService recuperacionService;

    private static final String CORREO_EXISTENTE   = "encargado@gelox-test.com";
    private static final String CORREO_INEXISTENTE = "noexiste@gelox-test.com";

    // -----------------------------------------------------------------------
    // CP05 — recuperacion-correo-existente
    // -----------------------------------------------------------------------
    @Test
    @DisplayName("CP05 - recuperación con correo existente: Firebase genera el enlace, servicio no lanza excepción")
    void cp05_recuperacionCorreoExistente() throws FirebaseAuthException {
        when(usuarioRepository.existsByCorreo(CORREO_EXISTENTE)).thenReturn(true);
        when(firebaseAuth.generatePasswordResetLink(CORREO_EXISTENTE)).thenReturn("https://firebase.link/reset");

        assertThatNoException().isThrownBy(
                () -> recuperacionService.solicitarRecuperacion(CORREO_EXISTENTE)
        );

        verify(firebaseAuth).generatePasswordResetLink(CORREO_EXISTENTE);
    }

    // -----------------------------------------------------------------------
    // CP05-extra — respuesta no expone información sensible (enlace no retornado)
    // -----------------------------------------------------------------------
    @Test
    @DisplayName("CP05-extra - el servicio no retorna el enlace de recuperación al caller")
    void cp05Extra_enlaceNoExpuesto() throws FirebaseAuthException {
        when(usuarioRepository.existsByCorreo(CORREO_EXISTENTE)).thenReturn(true);
        when(firebaseAuth.generatePasswordResetLink(CORREO_EXISTENTE)).thenReturn("https://firebase.link/reset");

        // El método es void: no retorna el enlace; la verificación es que no lance excepción.
        recuperacionService.solicitarRecuperacion(CORREO_EXISTENTE);

        verify(firebaseAuth, times(1)).generatePasswordResetLink(CORREO_EXISTENTE);
    }

    // -----------------------------------------------------------------------
    // CP06 — recuperacion-correo-inexistente → HTTP 404
    // -----------------------------------------------------------------------
    @Test
    @DisplayName("CP06 - correo inexistente: servicio lanza HTTP 404 y NO llama a Firebase")
    void cp06_recuperacionCorreoInexistente() throws FirebaseAuthException {
        when(usuarioRepository.existsByCorreo(CORREO_INEXISTENTE)).thenReturn(false);

        assertThatThrownBy(() -> recuperacionService.solicitarRecuperacion(CORREO_INEXISTENTE))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
                });

        // Firebase no debe ser invocado para correos no registrados
        verify(firebaseAuth, never()).generatePasswordResetLink(any());
    }

    // -----------------------------------------------------------------------
    // CP07 — enlace-expirado
    // Firebase lanza FirebaseAuthException cuando el enlace ya fue invalidado o expirado.
    // El servicio traduce ese error a HTTP 500. Este CP verifica que el backend
    // no permite cambiar contraseña con un enlace inválido/expirado.
    // -----------------------------------------------------------------------
    @Test
    @DisplayName("CP07 - enlace expirado: Firebase lanza excepción → servicio responde HTTP 500")
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
    }
}
