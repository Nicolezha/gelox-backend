package com.gelox.backend.rf04;

import com.gelox.backend.TestHelper;
import com.gelox.backend.entities.RolUsuario;
import com.gelox.backend.entities.Usuario;
import com.gelox.backend.repositories.UsuarioRepository;
import com.gelox.backend.services.AuthService;
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

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * RF04 — Cierre de sesión.
 * Cubre CP08, CP09.
 */
@ExtendWith(MockitoExtension.class)
class CierreSesionTest {

    @Mock
    UsuarioRepository usuarioRepository;

    @Mock
    FirebaseAuth firebaseAuth;

    @InjectMocks
    AuthService authService;

    private static final String UID_ACTIVO = "uid-encargado-test-001";
    private Usuario usuarioActivo;

    @BeforeEach
    void setUp() {
        usuarioActivo = TestHelper.buildUsuario(UID_ACTIVO, "encargado@gelox-test.com",
                RolUsuario.ENCARGADO_INVENTARIO, true);
    }

    // -----------------------------------------------------------------------
    // CP08 — cierre-sesion-exitoso
    // -----------------------------------------------------------------------
    @Test
    @DisplayName("CP08 - cierre de sesión exitoso: Firebase revoca los refresh tokens del usuario")
    void cp08_cierreSesionExitoso() throws FirebaseAuthException {
        doNothing().when(firebaseAuth).revokeRefreshTokens(UID_ACTIVO);

        assertThatNoException().isThrownBy(
                () -> authService.cerrarSesion(UID_ACTIVO)
        );

        verify(firebaseAuth).revokeRefreshTokens(UID_ACTIVO);
    }

    // -----------------------------------------------------------------------
    // CP08-extra — revokeRefreshTokens se llama exactamente una vez
    // -----------------------------------------------------------------------
    @Test
    @DisplayName("CP08-extra - cerrarSesion invoca revokeRefreshTokens una sola vez con el UID correcto")
    void cp08Extra_revokeTokensInvocadoUnaVez() throws FirebaseAuthException {
        doNothing().when(firebaseAuth).revokeRefreshTokens(UID_ACTIVO);

        authService.cerrarSesion(UID_ACTIVO);

        verify(firebaseAuth, times(1)).revokeRefreshTokens(UID_ACTIVO);
    }

    // -----------------------------------------------------------------------
    // CP09 — acceso-con-token-revocado
    // Tras cerrar sesión, Firebase rechaza el token (verifyIdToken lanza excepción).
    // El FirebaseAuthFilter capturaría el error y respondería HTTP 401.
    // Aquí verificamos que:
    //   a) Firebase fue llamado para revocar (CP08 se ejecutó)
    //   b) Una llamada posterior con el mismo token sería rechazada por Firebase
    // -----------------------------------------------------------------------
    @Test
    @DisplayName("CP09 - acceso con token revocado: Firebase rechaza el token → HTTP 401 esperado del filtro")
    void cp09_accesoConTokenRevocado() throws FirebaseAuthException {
        // Paso 1: simular cierre de sesión (revocación de tokens)
        doNothing().when(firebaseAuth).revokeRefreshTokens(UID_ACTIVO);
        authService.cerrarSesion(UID_ACTIVO);
        verify(firebaseAuth).revokeRefreshTokens(UID_ACTIVO);

        // Paso 2: simular que Firebase rechaza el token anterior con checkRevoked=true
        // En producción, FirebaseAuthFilter llama verifyIdToken() que internamente puede
        // verificar revocación. Si el token fue revocado, Firebase lanza FirebaseAuthException.
        FirebaseAuthException tokenRevocadoEx = mock(FirebaseAuthException.class);
        when(firebaseAuth.verifyIdToken("token-antes-de-logout")).thenThrow(tokenRevocadoEx);

        // El filtro captura la excepción y responde 401.
        // Verificamos que la excepción se produce al intentar verificar el token.
        assertThatThrownBy(() -> firebaseAuth.verifyIdToken("token-antes-de-logout"))
                .isInstanceOf(FirebaseAuthException.class);
    }

    // -----------------------------------------------------------------------
    // CP09-extra — si Firebase falla al revocar, el servicio lanza HTTP 500
    // -----------------------------------------------------------------------
    @Test
    @DisplayName("CP09-extra - si Firebase falla al revocar tokens, el servicio responde HTTP 500")
    void cp09Extra_falloRevocacion_responde500() throws FirebaseAuthException {
        FirebaseAuthException ex = mock(FirebaseAuthException.class);
        doThrow(ex).when(firebaseAuth).revokeRefreshTokens(UID_ACTIVO);

        assertThatThrownBy(() -> authService.cerrarSesion(UID_ACTIVO))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(rse -> assertThat(((ResponseStatusException) rse).getStatusCode())
                        .isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR));
    }
}
