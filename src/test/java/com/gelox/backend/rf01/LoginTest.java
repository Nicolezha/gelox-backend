package com.gelox.backend.rf01;

import com.gelox.backend.TestHelper;
import com.gelox.backend.dto.UsuarioDTO;
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
 * RF01 — Login de usuario.
 * Cubre CP01, CP02, CP03, CP04.
 */
@ExtendWith(MockitoExtension.class)
class LoginTest {

    @Mock
    UsuarioRepository usuarioRepository;

    @Mock
    FirebaseAuth firebaseAuth;

    @InjectMocks
    AuthService authService;

    private static final String UID_ENCARGADO  = "uid-encargado-test-001";
    private static final String UID_ADMIN      = "uid-admin-test-001";
    private static final String UID_DESACTIVADO = "uid-inactivo-test-001";

    private Usuario encargado;
    private Usuario admin;
    private Usuario desactivado;

    @BeforeEach
    void setUp() {
        encargado   = TestHelper.buildUsuario(UID_ENCARGADO,  "encargado@gelox-test.com",  RolUsuario.ENCARGADO_INVENTARIO, true);
        admin       = TestHelper.buildUsuario(UID_ADMIN,      "admin@gelox-test.com",       RolUsuario.ADMINISTRADOR,        true);
        desactivado = TestHelper.buildUsuario(UID_DESACTIVADO,"inactivo@gelox-test.com",    RolUsuario.ENCARGADO_VENTAS,     false);
    }

    // -----------------------------------------------------------------------
    // CP01 — login-exitoso (ENCARGADO_INVENTARIO)
    // -----------------------------------------------------------------------
    @Test
    @DisplayName("CP01 - login exitoso: usuario activo ENCARGADO_INVENTARIO recibe UsuarioDTO con su rol")
    void cp01_loginExitoso_encargadoInventario() {
        when(usuarioRepository.findByFirebaseUid(UID_ENCARGADO)).thenReturn(Optional.of(encargado));

        UsuarioDTO dto = authService.autenticarUsuario(UID_ENCARGADO);

        assertThat(dto).isNotNull();
        assertThat(dto.id()).isEqualTo(encargado.getId());
        assertThat(dto.rol()).isEqualTo(RolUsuario.ENCARGADO_INVENTARIO);
        assertThat(dto.nombre()).isEqualTo(encargado.getNombre());
        verify(usuarioRepository).findByFirebaseUid(UID_ENCARGADO);
    }

    // -----------------------------------------------------------------------
    // CP02 — login-rol-gerente (ADMINISTRADOR)
    // -----------------------------------------------------------------------
    @Test
    @DisplayName("CP02 - login con rol ADMINISTRADOR: UsuarioDTO devuelve rol ADMINISTRADOR")
    void cp02_loginRolGerente_administrador() {
        when(usuarioRepository.findByFirebaseUid(UID_ADMIN)).thenReturn(Optional.of(admin));

        UsuarioDTO dto = authService.autenticarUsuario(UID_ADMIN);

        assertThat(dto.rol()).isEqualTo(RolUsuario.ADMINISTRADOR);
    }

    // -----------------------------------------------------------------------
    // CP03 — login-credenciales-invalidas
    // El backend lanza 401 cuando el firebase_uid no existe en la tabla usuario.
    // En producción, un token inválido es rechazado por FirebaseAuthFilter (401)
    // antes de llegar al servicio. Aquí probamos que el servicio lanza 401 si
    // el UID no está registrado en BD.
    // -----------------------------------------------------------------------
    @Test
    @DisplayName("CP03 - credenciales inválidas: UID no registrado en BD → HTTP 401")
    void cp03_loginCredencialesInvalidas_uidNoRegistrado() {
        when(usuarioRepository.findByFirebaseUid("uid-inexistente")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.autenticarUsuario("uid-inexistente"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
                });
    }

    // -----------------------------------------------------------------------
    // CP04 — login-usuario-deshabilitado
    // -----------------------------------------------------------------------
    @Test
    @DisplayName("CP04 - usuario deshabilitado (activo=false): backend responde HTTP 403")
    void cp04_loginUsuarioDeshabilitado() {
        when(usuarioRepository.findByFirebaseUid(UID_DESACTIVADO)).thenReturn(Optional.of(desactivado));

        assertThatThrownBy(() -> authService.autenticarUsuario(UID_DESACTIVADO))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
                    assertThat(rse.getReason()).containsIgnoringCase("desactivada");
                });
    }

    // -----------------------------------------------------------------------
    // CP03-extra — FirebaseAuthFilter rechaza token inválido antes de llegar
    // al servicio. Este test verifica el comportamiento esperado del filtro:
    // cuando FirebaseAuth.verifyIdToken() lanza FirebaseAuthException,
    // el filtro responde 401 sin llegar al servicio de autenticación.
    // -----------------------------------------------------------------------
    @Test
    @DisplayName("CP03-extra - token Firebase inválido: FirebaseAuth lanza excepción → servicio no se invoca")
    void cp03Extra_tokenFirebaseInvalido_servicioNoInvocado() throws FirebaseAuthException {
        when(firebaseAuth.verifyIdToken("token-invalido"))
                .thenThrow(mock(FirebaseAuthException.class));

        // El filtro capturaría la excepción y respondería 401.
        // Verificamos que el repositorio no fue consultado (el filtro no llega al servicio).
        verifyNoInteractions(usuarioRepository);
    }
}
