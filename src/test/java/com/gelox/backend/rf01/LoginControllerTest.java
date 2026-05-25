package com.gelox.backend.rf01;

import com.gelox.backend.TestHelper;
import com.gelox.backend.controllers.AuthController;
import com.gelox.backend.dto.UsuarioDTO;
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
 * RF01 — Login: tests de la capa HTTP (AuthController).
 * CP01, CP02, CP03, CP04.
 *
 * <p>@WebMvcTest carga solo los beans de capa web. FirebaseConfig (plain @Configuration)
 * no se carga. FirebaseAuth y UsuarioRepository se proveen como mocks para que
 * FirebaseAuthFilter pueda ser instanciado. El filtro pasa las requests sin
 * cabecera Authorization directamente a los controllers.</p>
 */
@WebMvcTest(controllers = AuthController.class)
class LoginControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    AuthService authService;

    // Necesarios para que FirebaseAuthFilter y SecurityConfig se instancien
    @MockBean
    FirebaseAuth firebaseAuth;

    @MockBean
    UsuarioRepository usuarioRepository;

    private Usuario encargado;
    private Usuario admin;
    private Usuario desactivado;

    @BeforeEach
    void setUp() {
        encargado   = TestHelper.buildUsuario("uid-enc", "enc@test.com", RolUsuario.ENCARGADO_INVENTARIO, true);
        admin       = TestHelper.buildUsuario("uid-adm", "adm@test.com", RolUsuario.ADMINISTRADOR,        true);
        desactivado = TestHelper.buildUsuario("uid-dis", "dis@test.com", RolUsuario.ENCARGADO_VENTAS,     false);
    }

    // -----------------------------------------------------------------------
    // CP01 — login-exitoso (ENCARGADO_INVENTARIO) → HTTP 200
    // -----------------------------------------------------------------------
    @Test
    @DisplayName("CP01 - login exitoso ENCARGADO_INVENTARIO: POST /api/auth/verificar → 200 con rol correcto")
    void cp01_loginExitoso_encargadoInventario() throws Exception {
        UsuarioDTO dto = new UsuarioDTO(encargado.getId(), encargado.getNombre(),
                RolUsuario.ENCARGADO_INVENTARIO, null, encargado.getCorreo(), encargado.getTelefono());
        when(authService.autenticarUsuario(encargado.getFirebaseUid())).thenReturn(dto);

        mockMvc.perform(post("/api/auth/verificar")
                        .with(SecurityMockMvcRequestPostProcessors.authentication(
                                TestHelper.authParaUsuario(encargado))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rol").value("ENCARGADO_INVENTARIO"))
                .andExpect(jsonPath("$.nombre").value(encargado.getNombre()));
    }

    // -----------------------------------------------------------------------
    // CP02 — login-rol-gerente (ADMINISTRADOR) → HTTP 200 con rol ADMINISTRADOR
    // -----------------------------------------------------------------------
    @Test
    @DisplayName("CP02 - login ADMINISTRADOR: POST /api/auth/verificar → 200 con rol ADMINISTRADOR")
    void cp02_loginRolGerente_administrador() throws Exception {
        UsuarioDTO dto = new UsuarioDTO(admin.getId(), admin.getNombre(),
                RolUsuario.ADMINISTRADOR, null, admin.getCorreo(), admin.getTelefono());
        when(authService.autenticarUsuario(admin.getFirebaseUid())).thenReturn(dto);

        mockMvc.perform(post("/api/auth/verificar")
                        .with(SecurityMockMvcRequestPostProcessors.authentication(
                                TestHelper.authParaUsuario(admin))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rol").value("ADMINISTRADOR"));
    }

    // -----------------------------------------------------------------------
    // CP03 — login-credenciales-invalidas → HTTP 401 (sin autenticación)
    // -----------------------------------------------------------------------
    @Test
    @DisplayName("CP03 - sin token de autenticación: POST /api/auth/verificar → 401")
    void cp03_sinAutenticacion_retorna401() throws Exception {
        mockMvc.perform(post("/api/auth/verificar"))
                .andExpect(status().isUnauthorized());
    }

    // -----------------------------------------------------------------------
    // CP04 — login-usuario-deshabilitado → HTTP 403
    // -----------------------------------------------------------------------
    @Test
    @DisplayName("CP04 - usuario deshabilitado: servicio lanza 403 → controller retorna 403")
    void cp04_usuarioDeshabilitado_retorna403() throws Exception {
        when(authService.autenticarUsuario(desactivado.getFirebaseUid()))
                .thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "La cuenta está desactivada"));

        mockMvc.perform(post("/api/auth/verificar")
                        .with(SecurityMockMvcRequestPostProcessors.authentication(
                                TestHelper.authParaUsuario(desactivado))))
                .andExpect(status().isForbidden());
    }
}
