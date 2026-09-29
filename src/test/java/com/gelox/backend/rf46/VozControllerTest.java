package com.gelox.backend.rf46;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gelox.backend.TestHelper;
import com.gelox.backend.config.SecurityConfig;
import com.gelox.backend.controllers.VozController;
import com.gelox.backend.entities.RolUsuario;
import com.gelox.backend.entities.Usuario;
import com.gelox.backend.repositories.UsuarioRepository;
import com.gelox.backend.voz.VozService;
import com.gelox.backend.voz.dto.VozConfirmarResponse;
import com.gelox.backend.voz.dto.VozInterpretarResponse;
import com.google.firebase.auth.FirebaseAuth;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * RF46 — capa HTTP de la voz: POST /api/voz/interpretar y /api/voz/confirmar.
 * Texto vacío y confianza fuera de rango deben fallar la validación Jakarta
 * antes de llegar a VozService; una petición válida delega en el servicio.
 */
@WebMvcTest(controllers = VozController.class)
@Import(SecurityConfig.class)
class VozControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @MockBean
    VozService vozService;

    @MockBean
    FirebaseAuth firebaseAuth;

    @MockBean
    UsuarioRepository usuarioRepository;

    private Usuario usuarioActivo;

    @BeforeEach
    void setUp() {
        usuarioActivo = TestHelper.buildUsuario(
                "uid-voz-test-001", "voz@gelox-test.com", RolUsuario.ENCARGADO_VENTAS, true);
    }

    @Test
    @DisplayName("texto vacío → 400 por validación Jakarta")
    void interpretar_textoVacio_retorna400() throws Exception {
        mockMvc.perform(post("/api/voz/interpretar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"texto\": \"\", \"confianza\": 0.9}")
                        .with(SecurityMockMvcRequestPostProcessors.authentication(
                                TestHelper.authParaUsuario(usuarioActivo))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("confianza fuera de 0-1 → 400 por validación Jakarta")
    void interpretar_confianzaFueraDeRango_retorna400() throws Exception {
        mockMvc.perform(post("/api/voz/interpretar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"texto\": \"cuántas cajas de Festival quedan\", \"confianza\": 1.5}")
                        .with(SecurityMockMvcRequestPostProcessors.authentication(
                                TestHelper.authParaUsuario(usuarioActivo))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("petición válida → 200 con la respuesta de VozService")
    void interpretar_peticionValida_retorna200() throws Exception {
        UUID comandoId = UUID.randomUUID();
        VozInterpretarResponse respuesta = new VozInterpretarResponse(
                comandoId, true, "CONSULTAR_INVENTARIO", false, null,
                "Quedan 4 cajas y 6 unidades de Festival.", Map.of());
        when(vozService.interpretar(any(), any())).thenReturn(respuesta);

        mockMvc.perform(post("/api/voz/interpretar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"texto\": \"cuántas cajas de Festival quedan\", \"confianza\": 0.9}")
                        .with(SecurityMockMvcRequestPostProcessors.authentication(
                                TestHelper.authParaUsuario(usuarioActivo))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.comandoId").value(comandoId.toString()))
                .andExpect(jsonPath("$.ok").value(true))
                .andExpect(jsonPath("$.textoRespuesta").value("Quedan 4 cajas y 6 unidades de Festival."));
    }

    @Test
    @DisplayName("sin token → 401")
    void interpretar_sinToken_retorna401() throws Exception {
        mockMvc.perform(post("/api/voz/interpretar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"texto\": \"cuántas cajas de Festival quedan\", \"confianza\": 0.9}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("confirmar con comandoId válido → 200 con la respuesta de VozService")
    void confirmar_peticionValida_retorna200() throws Exception {
        UUID comandoId = UUID.randomUUID();
        VozConfirmarResponse respuesta = new VozConfirmarResponse("PROCESADO", "Venta registrada por $7.500.", Map.of());
        when(vozService.confirmar(any(), any())).thenReturn(respuesta);

        mockMvc.perform(post("/api/voz/confirmar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"comandoId\": \"" + comandoId + "\", \"confirmar\": true}")
                        .with(SecurityMockMvcRequestPostProcessors.authentication(
                                TestHelper.authParaUsuario(usuarioActivo))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("PROCESADO"));
    }
}
