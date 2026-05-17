package com.gelox.backend.rf06;

import com.gelox.backend.dto.CambioContrasenaDTO;
import com.gelox.backend.exceptions.ContrasenaNoCoincideException;
import com.gelox.backend.repositories.UsuarioRepository;
import com.gelox.backend.services.PerfilService;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.UserRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * RF06 — Cambio de contraseña.
 * Cubre CP16, CP17, CP18.
 *
 * <p>NOTA: Firebase Admin SDK no puede verificar la contraseña actual directamente.
 * La validación de {@code contrasenaActual} es responsabilidad del frontend
 * (signInWithEmailAndPassword). El backend valida que {@code nuevaContrasena}
 * coincida con {@code confirmacion} antes de actualizar en Firebase.
 */
@ExtendWith(MockitoExtension.class)
class CambioContrasenaTest {

    @Mock
    UsuarioRepository usuarioRepository;

    @InjectMocks
    PerfilService perfilService;

    private static final String FIREBASE_UID       = "uid-encargado-test-001";
    private static final String CONTRASENA_ACTUAL  = "ContraActual123!";
    private static final String NUEVA_CONTRASENA   = "NuevaPass456!";
    private static final String CONFIRMACION_OK    = "NuevaPass456!";
    private static final String CONFIRMACION_MAL   = "Diferente789!";

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(perfilService, "supabaseUrl", "http://localhost:9999");
        ReflectionTestUtils.setField(perfilService, "supabaseServiceKey", "test-key");
    }

    private CambioContrasenaDTO buildDto(String actual, String nueva, String confirmacion) {
        CambioContrasenaDTO dto = new CambioContrasenaDTO();
        dto.setContrasenaActual(actual);
        dto.setNuevaContrasena(nueva);
        dto.setConfirmacion(confirmacion);
        return dto;
    }

    // -----------------------------------------------------------------------
    // CP16 — cambio-contrasena-exitoso
    // -----------------------------------------------------------------------
    @Test
    @DisplayName("CP16 - cambio de contraseña exitoso: Firebase actualiza la contraseña sin excepciones")
    void cp16_cambioContrasenaExitoso() throws FirebaseAuthException {
        CambioContrasenaDTO dto = buildDto(CONTRASENA_ACTUAL, NUEVA_CONTRASENA, CONFIRMACION_OK);

        try (MockedStatic<FirebaseAuth> firebaseStatic = mockStatic(FirebaseAuth.class)) {
            FirebaseAuth mockAuth = mock(FirebaseAuth.class);
            firebaseStatic.when(FirebaseAuth::getInstance).thenReturn(mockAuth);

            UserRecord mockUser = mock(UserRecord.class);
            when(mockAuth.getUser(FIREBASE_UID)).thenReturn(mockUser);
            when(mockAuth.updateUser(any(UserRecord.UpdateRequest.class))).thenReturn(mockUser);

            assertThatNoException().isThrownBy(
                    () -> perfilService.cambiarContrasena(FIREBASE_UID, dto)
            );

            verify(mockAuth).updateUser(any(UserRecord.UpdateRequest.class));
        }
    }

    // -----------------------------------------------------------------------
    // CP16-extra — Firebase.updateUser se llama con el UID correcto
    // -----------------------------------------------------------------------
    @Test
    @DisplayName("CP16-extra - updateUser es invocado con el UID del usuario autenticado")
    void cp16Extra_updateUserInvocadoConUidCorrecto() throws FirebaseAuthException {
        CambioContrasenaDTO dto = buildDto(CONTRASENA_ACTUAL, NUEVA_CONTRASENA, CONFIRMACION_OK);

        try (MockedStatic<FirebaseAuth> firebaseStatic = mockStatic(FirebaseAuth.class)) {
            FirebaseAuth mockAuth = mock(FirebaseAuth.class);
            firebaseStatic.when(FirebaseAuth::getInstance).thenReturn(mockAuth);

            when(mockAuth.getUser(FIREBASE_UID)).thenReturn(mock(UserRecord.class));
            when(mockAuth.updateUser(any())).thenReturn(mock(UserRecord.class));

            perfilService.cambiarContrasena(FIREBASE_UID, dto);

            verify(mockAuth).getUser(FIREBASE_UID);
            verify(mockAuth).updateUser(argThat(req ->
                    FIREBASE_UID.equals(req.getUid())
            ));
        }
    }

    // -----------------------------------------------------------------------
    // CP17 — contrasena-actual-incorrecta
    // El backend no puede verificar la contraseña actual directamente con Firebase
    // Admin SDK. Esta validación debe hacerse en el frontend. Sin embargo, si
    // Firebase no encuentra el usuario (UID inválido), lanza una excepción que
    // el servicio convierte en IllegalArgumentException.
    // -----------------------------------------------------------------------
    @Test
    @DisplayName("CP17 - contrasena actual incorrecta: Firebase no encuentra el UID → IllegalArgumentException")
    void cp17_contrasenaActualIncorrecta_uidInvalido() throws FirebaseAuthException {
        CambioContrasenaDTO dto = buildDto("ContraWrong!", NUEVA_CONTRASENA, CONFIRMACION_OK);

        try (MockedStatic<FirebaseAuth> firebaseStatic = mockStatic(FirebaseAuth.class)) {
            FirebaseAuth mockAuth = mock(FirebaseAuth.class);
            firebaseStatic.when(FirebaseAuth::getInstance).thenReturn(mockAuth);

            FirebaseAuthException ex = mock(FirebaseAuthException.class);
            when(ex.getMessage()).thenReturn("No user record found");
            when(mockAuth.getUser(FIREBASE_UID)).thenThrow(ex);

            assertThatThrownBy(() -> perfilService.cambiarContrasena(FIREBASE_UID, dto))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Error al actualizar la contraseña");

            // updateUser NO debe ser invocado si getUser falla
            verify(mockAuth, never()).updateUser(any());
        }
    }

    // -----------------------------------------------------------------------
    // CP17-extra — contraseña no cambia cuando Firebase falla
    // -----------------------------------------------------------------------
    @Test
    @DisplayName("CP17-extra - cuando Firebase falla, no se actualiza nada en BD")
    void cp17Extra_cuandoFirebaseFalla_bdNoSeActualiza() throws FirebaseAuthException {
        CambioContrasenaDTO dto = buildDto(CONTRASENA_ACTUAL, NUEVA_CONTRASENA, CONFIRMACION_OK);

        try (MockedStatic<FirebaseAuth> firebaseStatic = mockStatic(FirebaseAuth.class)) {
            FirebaseAuth mockAuth = mock(FirebaseAuth.class);
            firebaseStatic.when(FirebaseAuth::getInstance).thenReturn(mockAuth);

            FirebaseAuthException ex = mock(FirebaseAuthException.class);
            when(mockAuth.getUser(FIREBASE_UID)).thenThrow(ex);

            assertThatThrownBy(() -> perfilService.cambiarContrasena(FIREBASE_UID, dto))
                    .isInstanceOf(IllegalArgumentException.class);

            verifyNoInteractions(usuarioRepository);
        }
    }

    // -----------------------------------------------------------------------
    // CP18 — confirmacion-no-coincide → ContrasenaNoCoincideException (HTTP 400)
    // -----------------------------------------------------------------------
    @Test
    @DisplayName("CP18 - confirmación no coincide: servicio lanza ContrasenaNoCoincideException antes de llamar a Firebase")
    void cp18_confirmacionNoCoincide() {
        CambioContrasenaDTO dto = buildDto(CONTRASENA_ACTUAL, NUEVA_CONTRASENA, CONFIRMACION_MAL);

        try (MockedStatic<FirebaseAuth> firebaseStatic = mockStatic(FirebaseAuth.class)) {
            FirebaseAuth mockAuth = mock(FirebaseAuth.class);
            firebaseStatic.when(FirebaseAuth::getInstance).thenReturn(mockAuth);

            assertThatThrownBy(() -> perfilService.cambiarContrasena(FIREBASE_UID, dto))
                    .isInstanceOf(ContrasenaNoCoincideException.class)
                    .hasMessageContaining("no coinciden");

            // Firebase no debe ser invocado si las contraseñas no coinciden
            verifyNoInteractions(mockAuth);
        }
    }

    // -----------------------------------------------------------------------
    // CP18-extra — la validación ocurre en el backend (no solo en frontend)
    // -----------------------------------------------------------------------
    @Test
    @DisplayName("CP18-extra - validación backend: incluso sin validación frontend, backend rechaza confirmación incorrecta")
    void cp18Extra_validacionOcurreEnBackend() {
        // Simula que el frontend no validó y envió directo al backend
        CambioContrasenaDTO dto = buildDto("cualquiera", "NuevaPass!", "DIFERENTE!");

        try (MockedStatic<FirebaseAuth> firebaseStatic = mockStatic(FirebaseAuth.class)) {
            assertThatThrownBy(() -> perfilService.cambiarContrasena(FIREBASE_UID, dto))
                    .isInstanceOf(ContrasenaNoCoincideException.class);
        }
    }
}
