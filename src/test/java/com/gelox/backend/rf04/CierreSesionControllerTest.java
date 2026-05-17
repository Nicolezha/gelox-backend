package com.gelox.backend.rf04;

import com.gelox.backend.TestHelper;
import com.gelox.backend.controllers.AuthController;
import com.gelox.backend.entities.RolUsuario;
import com.gelox.backend.entities.Usuario;
import com.gelox.backend.repositories.UsuarioRepository;
import com.gelox.backend.services.AuthService;
import com.google.firebase.auth.FirebaseAuth;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * RF04 — Cierre de sesión: tests de la capa HTTP (AuthController).
 * CP08, CP09.
 */
@WebMvcTest(controllers = AuthController.class)
class CierreSesionControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    AuthService authService;

    @MockBean
    FirebaseAuth firebaseAuth;

    @MockBean
    UsuarioRepository usuarioRepository;

    private Usuario usuarioActivo;

    @BeforeEach
    void setUp() {
        usuarioActivo = TestHelper.buildUsuario(
                "uid-encargado-test-001", "encargado@gelox-test.com",
                RolUsuario.ENCARGADO_INVENTARIO, true);
    }

    // -----------------------------------------------------------------------
    // CP08 — cierre-sesion-exitoso → HTTP 200 + mensaje de confirmación
    // -----------------------------------------------------------------------
    @Test
    @DisplayName("CP08 - cierre de sesión exitoso: POST /api/auth/cerrar-sesion → 200 con mensaje")
    void cp08_cierreSesionExitoso() throws Exception {
        doNothing().when(authService).cerrarSesion(usuarioActivo.getFirebaseUid());

        mockMvc.perform(post("/api/auth/cerrar-sesion")
                        .with(SecurityMockMvcRequestPostProcessors.authentication(
                                TestHelper.authParaUsuario(usuarioActivo))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mensaje").value("Sesión cerrada correctamente"));

        verify(authService).cerrarSesion(usuarioActivo.getFirebaseUid());
    }

    // -----------------------------------------------------------------------
    // CP09 — acceso-con-token-revocado → HTTP 401 (sin autenticación)
    // -----------------------------------------------------------------------
    @Test
    @DisplayName("CP09 - acceso sin autenticación (token revocado/expirado): POST /api/auth/cerrar-sesion → 401")
    void cp09_accesoSinAutenticacion_retorna401() throws Exception {
        mockMvc.perform(post("/api/auth/cerrar-sesion"))
                .andExpect(status().isUnauthorized());
    }

    // -----------------------------------------------------------------------
    // CP08-extra — si Firebase falla al revocar → HTTP 500
    // -----------------------------------------------------------------------
    @Test
    @DisplayName("CP08-extra - error al revocar tokens: servicio lanza 500")
    void cp08Extra_falloRevocacion_retorna500() throws Exception {
        doThrow(new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "No se pudo cerrar la sesión"))
                .when(authService).cerrarSesion(usuarioActivo.getFirebaseUid());

        mockMvc.perform(post("/api/auth/cerrar-sesion")
                        .with(SecurityMockMvcRequestPostProcessors.authentication(
                                TestHelper.authParaUsuario(usuarioActivo))))
                .andExpect(status().isInternalServerError());
    }
}
