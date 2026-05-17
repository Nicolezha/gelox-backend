package com.gelox.backend.rf05;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gelox.backend.TestHelper;
import com.gelox.backend.controllers.PerfilController;
import com.gelox.backend.dto.ActualizarPerfilDTO;
import com.gelox.backend.entities.RolUsuario;
import com.gelox.backend.entities.Usuario;
import com.gelox.backend.exceptions.CorreoDuplicadoException;
import com.gelox.backend.repositories.UsuarioRepository;
import com.gelox.backend.services.PerfilService;
import com.google.firebase.auth.FirebaseAuth;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * RF05 — Actualización de perfil: tests de la capa HTTP (PerfilController).
 * CP14, CP15.
 */
@WebMvcTest(controllers = PerfilController.class)
class ActualizarPerfilControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @MockBean
    PerfilService perfilService;

    @MockBean
    FirebaseAuth firebaseAuth;

    @MockBean
    UsuarioRepository usuarioRepository;

    private final UUID ID_USUARIO = UUID.randomUUID();
    private Usuario usuarioActivo;

    @BeforeEach
    void setUp() {
        usuarioActivo = TestHelper.buildUsuario(
                "uid-enc-001", "encargado@gelox-test.com",
                RolUsuario.ENCARGADO_INVENTARIO, true);
        ReflectionTestUtils.setField(usuarioActivo, "id", ID_USUARIO);
    }

    private ActualizarPerfilDTO buildDto(String nombre, String correo) {
        ActualizarPerfilDTO dto = new ActualizarPerfilDTO();
        dto.setNombre(nombre);
        dto.setCorreo(correo);
        dto.setTelefono("3001234567");
        return dto;
    }

    // -----------------------------------------------------------------------
    // CP14 — actualizar-perfil-exitoso → HTTP 200
    // -----------------------------------------------------------------------
    @Test
    @DisplayName("CP14 - actualizar perfil exitoso: PUT /api/perfil/{userId} → 200 con datos actualizados")
    void cp14_actualizarPerfilExitoso_retorna200() throws Exception {
        Usuario usuarioActualizado = TestHelper.buildUsuario(
                "uid-enc-001", "nuevo@gelox-test.com",
                RolUsuario.ENCARGADO_INVENTARIO, true);
        usuarioActualizado.setNombre("Nuevo Nombre");

        when(perfilService.actualizarPerfil(eq(ID_USUARIO), any(ActualizarPerfilDTO.class)))
                .thenReturn(usuarioActualizado);

        mockMvc.perform(put("/api/perfil/{userId}", ID_USUARIO)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildDto("Nuevo Nombre", "nuevo@gelox-test.com")))
                        .with(SecurityMockMvcRequestPostProcessors.authentication(
                                TestHelper.authParaUsuario(usuarioActivo))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Nuevo Nombre"))
                .andExpect(jsonPath("$.correo").value("nuevo@gelox-test.com"));
    }

    // -----------------------------------------------------------------------
    // CP15 — correo-duplicado → HTTP 409 Conflict
    // -----------------------------------------------------------------------
    @Test
    @DisplayName("CP15 - correo duplicado: PUT /api/perfil/{userId} → 409 Conflict con mensaje de error")
    void cp15_correoDuplicado_retorna409() throws Exception {
        when(perfilService.actualizarPerfil(eq(ID_USUARIO), any(ActualizarPerfilDTO.class)))
                .thenThrow(new CorreoDuplicadoException("El correo ya está registrado por otro usuario"));

        mockMvc.perform(put("/api/perfil/{userId}", ID_USUARIO)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildDto("Nombre", "duplicado@gelox-test.com")))
                        .with(SecurityMockMvcRequestPostProcessors.authentication(
                                TestHelper.authParaUsuario(usuarioActivo))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").exists());
    }

    // -----------------------------------------------------------------------
    // CP14-extra — correo con formato inválido → HTTP 400
    // -----------------------------------------------------------------------
    @Test
    @DisplayName("CP14-extra - correo con formato inválido: validación Jakarta → 400")
    void cp14Extra_correoInvalido_retorna400() throws Exception {
        ActualizarPerfilDTO dto = new ActualizarPerfilDTO();
        dto.setNombre("Nombre Válido");
        dto.setCorreo("esto-no-es-un-correo");

        mockMvc.perform(put("/api/perfil/{userId}", ID_USUARIO)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto))
                        .with(SecurityMockMvcRequestPostProcessors.authentication(
                                TestHelper.authParaUsuario(usuarioActivo))))
                .andExpect(status().isBadRequest());
    }
}
