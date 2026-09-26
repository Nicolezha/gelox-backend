package com.gelox.backend.rf06;

import com.gelox.backend.TestHelper;
import com.gelox.backend.dto.CambioContrasenaDTO;
import com.gelox.backend.entities.RolUsuario;
import com.gelox.backend.entities.TipoEvento;
import com.gelox.backend.entities.Usuario;
import com.gelox.backend.exceptions.ContrasenaActualIncorrectaException;
import com.gelox.backend.exceptions.ContrasenaNoCoincideException;
import com.gelox.backend.repositories.UsuarioRepository;
import com.gelox.backend.services.EventoSistemaService;
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
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * RF06 — Cambio de contraseña. Cubre CP16, CP17, CP18.
 *
 * <p>El servicio verifica la contraseña actual contra Firebase (identitytoolkit,
 * por HTTP) y luego actualiza con el {@code FirebaseAuth} inyectado. Ambos se
 * reemplazan por mocks: el {@code RestTemplate} vía reflexión, para no salir a red.
 */
@ExtendWith(MockitoExtension.class)
class CambioContrasenaTest {

    @Mock
    UsuarioRepository usuarioRepository;

    @Mock
    FirebaseAuth firebaseAuth;

    @Mock
    EventoSistemaService eventoSistemaService;

    @Mock
    RestTemplate restTemplate;

    @InjectMocks
    PerfilService perfilService;

    private static final String FIREBASE_UID       = "uid-encargado-test-001";
    private static final String CONTRASENA_ACTUAL  = "ContraActual123!";
    private static final String NUEVA_CONTRASENA   = "NuevaPass456!";
    private static final String CONFIRMACION_OK    = "NuevaPass456!";
    private static final String CONFIRMACION_MAL   = "Diferente789!";

    private Usuario usuario;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(perfilService, "restTemplate", restTemplate);
        usuario = TestHelper.buildUsuario(FIREBASE_UID, "encargado@gelox-test.com",
                RolUsuario.ENCARGADO_INVENTARIO, true);
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
    @DisplayName("CP16 - cambio exitoso: verifica la contraseña actual, actualiza en Firebase y registra el evento")
    void cp16_cambioContrasenaExitoso() throws FirebaseAuthException {
        when(usuarioRepository.findByFirebaseUid(FIREBASE_UID)).thenReturn(Optional.of(usuario));
        when(firebaseAuth.updateUser(any(UserRecord.UpdateRequest.class))).thenReturn(mock(UserRecord.class));

        assertThatNoException().isThrownBy(() -> perfilService.cambiarContrasena(
                FIREBASE_UID, buildDto(CONTRASENA_ACTUAL, NUEVA_CONTRASENA, CONFIRMACION_OK)));

        verify(restTemplate).postForEntity(anyString(), any(HttpEntity.class), eq(String.class));
        verify(firebaseAuth).updateUser(any(UserRecord.UpdateRequest.class));
        verify(eventoSistemaService).registrarEvento(eq(TipoEvento.CAMBIO_CONTRASENA), anyString(), eq(usuario.getId()));
    }

    // -----------------------------------------------------------------------
    // CP17 — contrasena-actual-incorrecta
    // -----------------------------------------------------------------------
    @Test
    @DisplayName("CP17 - contraseña actual incorrecta: Firebase la rechaza → ContrasenaActualIncorrectaException y no se actualiza")
    void cp17_contrasenaActualIncorrecta() throws FirebaseAuthException {
        when(usuarioRepository.findByFirebaseUid(FIREBASE_UID)).thenReturn(Optional.of(usuario));
        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(String.class)))
                .thenThrow(new HttpClientErrorException(HttpStatus.BAD_REQUEST));

        assertThatThrownBy(() -> perfilService.cambiarContrasena(
                FIREBASE_UID, buildDto("ContraWrong!", NUEVA_CONTRASENA, CONFIRMACION_OK)))
                .isInstanceOf(ContrasenaActualIncorrectaException.class);

        verify(firebaseAuth, never()).updateUser(any());
        verifyNoInteractions(eventoSistemaService);
    }

    @Test
    @DisplayName("CP17-extra - usuario no registrado: IllegalArgumentException sin llamar a Firebase")
    void cp17Extra_usuarioNoRegistrado() {
        when(usuarioRepository.findByFirebaseUid(FIREBASE_UID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> perfilService.cambiarContrasena(
                FIREBASE_UID, buildDto(CONTRASENA_ACTUAL, NUEVA_CONTRASENA, CONFIRMACION_OK)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Usuario no encontrado");

        verifyNoInteractions(restTemplate, firebaseAuth);
    }

    @Test
    @DisplayName("CP17-extra2 - Firebase falla al actualizar: IllegalArgumentException y no se registra evento")
    void cp17Extra2_firebaseFallaAlActualizar() throws FirebaseAuthException {
        when(usuarioRepository.findByFirebaseUid(FIREBASE_UID)).thenReturn(Optional.of(usuario));
        FirebaseAuthException ex = mock(FirebaseAuthException.class);
        when(ex.getMessage()).thenReturn("No user record found");
        when(firebaseAuth.updateUser(any(UserRecord.UpdateRequest.class))).thenThrow(ex);

        assertThatThrownBy(() -> perfilService.cambiarContrasena(
                FIREBASE_UID, buildDto(CONTRASENA_ACTUAL, NUEVA_CONTRASENA, CONFIRMACION_OK)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Error al actualizar la contraseña");

        verifyNoInteractions(eventoSistemaService);
    }

    // -----------------------------------------------------------------------
    // CP18 — confirmacion-no-coincide → ContrasenaNoCoincideException (HTTP 400)
    // -----------------------------------------------------------------------
    @Test
    @DisplayName("CP18 - confirmación no coincide: se rechaza antes de tocar el repositorio, la red o Firebase")
    void cp18_confirmacionNoCoincide() {
        assertThatThrownBy(() -> perfilService.cambiarContrasena(
                FIREBASE_UID, buildDto(CONTRASENA_ACTUAL, NUEVA_CONTRASENA, CONFIRMACION_MAL)))
                .isInstanceOf(ContrasenaNoCoincideException.class)
                .hasMessageContaining("no coinciden");

        verifyNoInteractions(usuarioRepository, restTemplate, firebaseAuth);
    }
}
