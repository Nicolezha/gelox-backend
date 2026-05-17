package com.gelox.backend.rf03;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gelox.backend.TestHelper;
import com.gelox.backend.controllers.UsuarioController;
import com.gelox.backend.dto.CrearUsuarioDTO;
import com.gelox.backend.dto.EditarUsuarioDTO;
import com.gelox.backend.dto.UsuarioResponseDTO;
import com.gelox.backend.entities.RolUsuario;
import com.gelox.backend.entities.Usuario;
import com.gelox.backend.repositories.UsuarioRepository;
import com.gelox.backend.services.UsuarioService;
import com.google.firebase.auth.FirebaseAuth;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * RF03 — Gestión de usuarios: tests de la capa HTTP (UsuarioController).
 * CP10, CP11, CP12, CP13.
 */
@WebMvcTest(controllers = UsuarioController.class)
class GestionUsuariosControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @MockBean
    UsuarioService usuarioService;

    @MockBean
    FirebaseAuth firebaseAuth;

    @MockBean
    UsuarioRepository usuarioRepository;

    private Usuario admin;
    private Usuario encargado;
    private final UUID ID_USUARIO = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        admin     = TestHelper.buildUsuario("uid-adm", "adm@test.com", RolUsuario.ADMINISTRADOR,        true);
        encargado = TestHelper.buildUsuario("uid-enc", "enc@test.com", RolUsuario.ENCARGADO_INVENTARIO, true);
    }

    private CrearUsuarioDTO crearDto() {
        CrearUsuarioDTO dto = new CrearUsuarioDTO();
        dto.setNombre("Nuevo Usuario");
        dto.setCorreo("nuevo@gelox-test.com");
        dto.setContrasenaTemporal("Pass1234!");
        dto.setRol("ENCARGADO_VENTAS");
        return dto;
    }

    // -----------------------------------------------------------------------
    // CP10 — crear-usuario-como-gerente (ADMINISTRADOR) → HTTP 201
    // -----------------------------------------------------------------------
    @Test
    @DisplayName("CP10 - crear usuario como ADMINISTRADOR: POST /api/usuarios → 201 Created con activo=true")
    void cp10_crearUsuario_comoAdministrador_retorna201() throws Exception {
        UsuarioResponseDTO response = new UsuarioResponseDTO();
        response.setId(UUID.randomUUID());
        response.setNombre("Nuevo Usuario");
        response.setCorreo("nuevo@gelox-test.com");
        response.setRol("ENCARGADO_VENTAS");
        response.setActivo(true);

        when(usuarioService.crearUsuario(any(CrearUsuarioDTO.class))).thenReturn(response);

        mockMvc.perform(post("/api/usuarios")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(crearDto()))
                        .with(SecurityMockMvcRequestPostProcessors.authentication(
                                TestHelper.authParaUsuario(admin))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.activo").value(true))
                .andExpect(jsonPath("$.rol").value("ENCARGADO_VENTAS"))
                .andExpect(jsonPath("$.correo").value("nuevo@gelox-test.com"));
    }

    // -----------------------------------------------------------------------
    // CP11 — crear-usuario-como-no-gerente → HTTP 403
    // -----------------------------------------------------------------------
    @Test
    @DisplayName("CP11 - crear usuario como ENCARGADO_INVENTARIO: servicio lanza 403 → controller retorna 403")
    void cp11_crearUsuario_comoNoGerente_retorna403() throws Exception {
        when(usuarioService.crearUsuario(any(CrearUsuarioDTO.class)))
                .thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "No tienes permiso para realizar esta acción"));

        mockMvc.perform(post("/api/usuarios")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(crearDto()))
                        .with(SecurityMockMvcRequestPostProcessors.authentication(
                                TestHelper.authParaUsuario(encargado))))
                .andExpect(status().isForbidden());
    }

    // -----------------------------------------------------------------------
    // CP11-extra — sin autenticación → HTTP 401
    // -----------------------------------------------------------------------
    @Test
    @DisplayName("CP11-extra - sin token de autenticación: POST /api/usuarios → 401")
    void cp11Extra_sinAutenticacion_retorna401() throws Exception {
        mockMvc.perform(post("/api/usuarios")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(crearDto())))
                .andExpect(status().isUnauthorized());
    }

    // -----------------------------------------------------------------------
    // CP12 — editar-usuario (ADMINISTRADOR) → HTTP 200
    // -----------------------------------------------------------------------
    @Test
    @DisplayName("CP12 - editar usuario como ADMINISTRADOR: PUT /api/usuarios/{id} → 200 con datos actualizados")
    void cp12_editarUsuario_comoAdministrador_retorna200() throws Exception {
        EditarUsuarioDTO editDto = new EditarUsuarioDTO();
        editDto.setNombre("Nombre Actualizado");
        editDto.setCorreo("actualizado@gelox-test.com");
        editDto.setRol("ENCARGADO_INVENTARIO");

        UsuarioResponseDTO response = new UsuarioResponseDTO();
        response.setId(ID_USUARIO);
        response.setNombre("Nombre Actualizado");
        response.setCorreo("actualizado@gelox-test.com");
        response.setRol("ENCARGADO_INVENTARIO");
        response.setActivo(true);

        when(usuarioService.editarUsuario(eq(ID_USUARIO), any(EditarUsuarioDTO.class))).thenReturn(response);

        mockMvc.perform(put("/api/usuarios/{id}", ID_USUARIO)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(editDto))
                        .with(SecurityMockMvcRequestPostProcessors.authentication(
                                TestHelper.authParaUsuario(admin))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Nombre Actualizado"))
                .andExpect(jsonPath("$.correo").value("actualizado@gelox-test.com"))
                .andExpect(jsonPath("$.rol").value("ENCARGADO_INVENTARIO"));
    }

    // -----------------------------------------------------------------------
    // CP13 — deshabilitar-usuario (ADMINISTRADOR) → HTTP 204
    // -----------------------------------------------------------------------
    @Test
    @DisplayName("CP13 - deshabilitar usuario como ADMINISTRADOR: DELETE /api/usuarios/{id} → 204 No Content")
    void cp13_deshabilitarUsuario_comoAdministrador_retorna204() throws Exception {
        doNothing().when(usuarioService).deshabilitarUsuario(ID_USUARIO);

        mockMvc.perform(delete("/api/usuarios/{id}", ID_USUARIO)
                        .with(SecurityMockMvcRequestPostProcessors.authentication(
                                TestHelper.authParaUsuario(admin))))
                .andExpect(status().isNoContent());

        verify(usuarioService).deshabilitarUsuario(ID_USUARIO);
    }

    // -----------------------------------------------------------------------
    // CP13-extra — deshabilitar usuario inexistente → HTTP 400
    // -----------------------------------------------------------------------
    @Test
    @DisplayName("CP13-extra - usuario a deshabilitar no existe: servicio lanza BadRequest → 400")
    void cp13Extra_deshabilitarUsuarioInexistente_retorna400() throws Exception {
        doThrow(new IllegalArgumentException("Usuario no encontrado"))
                .when(usuarioService).deshabilitarUsuario(ID_USUARIO);

        mockMvc.perform(delete("/api/usuarios/{id}", ID_USUARIO)
                        .with(SecurityMockMvcRequestPostProcessors.authentication(
                                TestHelper.authParaUsuario(admin))))
                .andExpect(status().isBadRequest());
    }
}
