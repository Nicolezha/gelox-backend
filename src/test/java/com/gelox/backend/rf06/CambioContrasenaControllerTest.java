package com.gelox.backend.rf06;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gelox.backend.TestHelper;
import com.gelox.backend.controllers.PerfilController;
import com.gelox.backend.dto.CambioContrasenaDTO;
import com.gelox.backend.entities.RolUsuario;
import com.gelox.backend.entities.Usuario;
import com.gelox.backend.exceptions.ContrasenaNoCoincideException;
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
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * RF06 — Cambio de contraseña: tests de la capa HTTP (PerfilController).
 * CP16, CP17, CP18.
 */
@WebMvcTest(controllers = PerfilController.class)
class CambioContrasenaControllerTest {

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

    private static final String FIREBASE_UID = "uid-encargado-test-001";
    private Usuario usuarioActivo;

    @BeforeEach
    void setUp() {
        usuarioActivo = TestHelper.buildUsuario(
                FIREBASE_UID, "encargado@gelox-test.com",
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
    // CP16 — cambio-contrasena-exitoso → HTTP 200
    // -----------------------------------------------------------------------
    @Test
    @DisplayName("CP16 - cambio de contraseña exitoso: PUT /api/perfil/{uid}/contrasena → 200 con mensaje")
    void cp16_cambioContrasenaExitoso_retorna200() throws Exception {
        doNothing().when(perfilService).cambiarContrasena(eq(FIREBASE_UID), any(CambioContrasenaDTO.class));

        mockMvc.perform(put("/api/perfil/{firebaseUid}/contrasena", FIREBASE_UID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                buildDto("ActualPass123!", "NuevaPass456!", "NuevaPass456!")))
                        .with(SecurityMockMvcRequestPostProcessors.authentication(
                                TestHelper.authParaUsuario(usuarioActivo))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mensaje").value("Contraseña actualizada correctamente"));
    }

    // -----------------------------------------------------------------------
    // CP17 — contrasena-actual-incorrecta → HTTP 400
    // -----------------------------------------------------------------------
    @Test
    @DisplayName("CP17 - contraseña actual incorrecta: servicio lanza IllegalArgumentException → 400")
    void cp17_contrasenaActualIncorrecta_retorna400() throws Exception {
        doThrow(new IllegalArgumentException("Error al actualizar la contraseña: USER_NOT_FOUND"))
                .when(perfilService).cambiarContrasena(eq(FIREBASE_UID), any(CambioContrasenaDTO.class));

        mockMvc.perform(put("/api/perfil/{firebaseUid}/contrasena", FIREBASE_UID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                buildDto("ContraIncorrecta!", "NuevaPass456!", "NuevaPass456!")))
                        .with(SecurityMockMvcRequestPostProcessors.authentication(
                                TestHelper.authParaUsuario(usuarioActivo))))
                .andExpect(status().isBadRequest());
    }

    // -----------------------------------------------------------------------
    // CP18 — confirmacion-no-coincide → HTTP 400 con mensaje descriptivo
    // -----------------------------------------------------------------------
    @Test
    @DisplayName("CP18 - confirmación no coincide: PUT /api/perfil/{uid}/contrasena → 400 con error")
    void cp18_confirmacionNoCoincide_retorna400() throws Exception {
        doThrow(new ContrasenaNoCoincideException("La nueva contraseña y su confirmación no coinciden"))
                .when(perfilService).cambiarContrasena(eq(FIREBASE_UID), any(CambioContrasenaDTO.class));

        mockMvc.perform(put("/api/perfil/{firebaseUid}/contrasena", FIREBASE_UID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                buildDto("ActualPass123!", "NuevaPass456!", "DIFERENTE789!")))
                        .with(SecurityMockMvcRequestPostProcessors.authentication(
                                TestHelper.authParaUsuario(usuarioActivo))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("La nueva contraseña y su confirmación no coinciden"));
    }

    // -----------------------------------------------------------------------
    // CP18-extra — nueva contraseña menor a 6 chars → HTTP 400 de validación Bean
    // -----------------------------------------------------------------------
    @Test
    @DisplayName("CP18-extra - nueva contraseña menor a 6 caracteres: validación Jakarta → 400")
    void cp18Extra_nuevaContrasenaCorta_retorna400() throws Exception {
        mockMvc.perform(put("/api/perfil/{firebaseUid}/contrasena", FIREBASE_UID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                buildDto("ActualPass123!", "abc", "abc")))
                        .with(SecurityMockMvcRequestPostProcessors.authentication(
                                TestHelper.authParaUsuario(usuarioActivo))))
                .andExpect(status().isBadRequest());
    }
}
