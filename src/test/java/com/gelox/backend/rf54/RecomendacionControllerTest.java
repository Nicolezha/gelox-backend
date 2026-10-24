package com.gelox.backend.rf54;

import com.gelox.backend.TestHelper;
import com.gelox.backend.config.SecurityConfig;
import com.gelox.backend.entities.EstadoRecomendacion;
import com.gelox.backend.entities.OrigenRecomendacion;
import com.gelox.backend.entities.RolUsuario;
import com.gelox.backend.entities.Usuario;
import com.gelox.backend.recomendacion.RecomendacionCompraService;
import com.gelox.backend.recomendacion.RecomendacionController;
import com.gelox.backend.recomendacion.dto.ProductoExcluidoDTO;
import com.gelox.backend.recomendacion.dto.RecomendacionGeneradaDTO;
import com.gelox.backend.repositories.UsuarioRepository;
import com.gelox.backend.security.RolVerificacionAspect;
import com.gelox.backend.services.SupabaseStorageService;
import com.gelox.backend.tenant.DistribuidoraEstadoCache;
import com.google.firebase.auth.FirebaseAuth;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.aop.AopAutoConfiguration;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * RF54 — POST /api/recomendaciones: 201 con la forma del contrato, 400 por presupuesto y 403
 * por rol (con el aspecto de @RequiereRol real).
 */
@WebMvcTest(controllers = RecomendacionController.class)
@Import({SecurityConfig.class, RolVerificacionAspect.class})
@ImportAutoConfiguration(AopAutoConfiguration.class)
class RecomendacionControllerTest {

    @Autowired MockMvc mockMvc;

    @MockBean RecomendacionCompraService service;
    @MockBean SupabaseStorageService storageService;
    @MockBean FirebaseAuth firebaseAuth;
    @MockBean DistribuidoraEstadoCache distribuidoraEstadoCache;
    @MockBean UsuarioRepository usuarioRepository;

    static Usuario usuario(RolUsuario rol) {
        return TestHelper.buildUsuario("uid-" + rol, rol + "@test.com", rol, true);
    }

    static RecomendacionGeneradaDTO respuesta() {
        return new RecomendacionGeneradaDTO(
                UUID.randomUUID(), EstadoRecomendacion.GENERADA, OrigenRecomendacion.PANTALLA,
                new BigDecimal("80000"), new BigDecimal("69880"), BigDecimal.ZERO, true, 140, 3,
                LocalDateTime.now(), List.of(),
                List.of(new ProductoExcluidoDTO(UUID.randomUUID(), "Sin costo", "Sin precio de costo")));
    }

    @Test
    @DisplayName("ENCARGADO_INVENTARIO genera la recomendación → 201 con costoTotal y productosExcluidos")
    void generar201() throws Exception {
        when(service.generar(eq(new BigDecimal("80000")), eq(OrigenRecomendacion.PANTALLA), any()))
                .thenReturn(respuesta());

        mockMvc.perform(post("/api/recomendaciones")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"presupuesto\": 80000, \"origen\": \"PANTALLA\"}")
                        .with(authentication(TestHelper.authParaUsuario(usuario(RolUsuario.ENCARGADO_INVENTARIO)))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("GENERADA"))
                .andExpect(jsonPath("$.costoTotal").value(69880))
                .andExpect(jsonPath("$.confianzaBaja").value(true))
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.productosExcluidos[0].motivo").value("Sin precio de costo"));
    }

    @Test
    @DisplayName("Presupuesto 0 → 400 {\"error\": \"El presupuesto debe ser mayor a cero\"}")
    void presupuestoCero400() throws Exception {
        when(service.generar(eq(BigDecimal.ZERO), any(), any()))
                .thenThrow(new IllegalArgumentException("El presupuesto debe ser mayor a cero"));

        mockMvc.perform(post("/api/recomendaciones")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"presupuesto\": 0}")
                        .with(authentication(TestHelper.authParaUsuario(usuario(RolUsuario.ADMINISTRADOR)))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("El presupuesto debe ser mayor a cero"));
    }

    @Test
    @DisplayName("Sin body → el servicio recibe presupuesto nulo y responde 400")
    void sinBody400() throws Exception {
        when(service.generar(isNull(), isNull(), any()))
                .thenThrow(new IllegalArgumentException("El presupuesto debe ser mayor a cero"));

        mockMvc.perform(post("/api/recomendaciones")
                        .with(authentication(TestHelper.authParaUsuario(usuario(RolUsuario.ADMINISTRADOR)))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("El presupuesto debe ser mayor a cero"));
    }

    @Test
    @DisplayName("ENCARGADO_VENTAS → 403 y no se genera nada")
    void rolVentas403() throws Exception {
        mockMvc.perform(post("/api/recomendaciones")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"presupuesto\": 80000}")
                        .with(authentication(TestHelper.authParaUsuario(usuario(RolUsuario.ENCARGADO_VENTAS)))))
                .andExpect(status().isForbidden());

        verify(service, never()).generar(any(), any(), any());
    }
}
