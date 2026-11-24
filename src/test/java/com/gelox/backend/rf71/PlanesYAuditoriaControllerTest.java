package com.gelox.backend.rf71;

import com.gelox.backend.TestHelper;
import com.gelox.backend.config.SecurityConfig;
import com.gelox.backend.entities.EstadoDistribuidora;
import com.gelox.backend.entities.RolUsuario;
import com.gelox.backend.entities.Usuario;
import com.gelox.backend.exceptions.CampoInvalidoException;
import com.gelox.backend.gobierno.AuditoriaConsultaService;
import com.gelox.backend.gobierno.AuditoriaController;
import com.gelox.backend.gobierno.AuditoriaService;
import com.gelox.backend.gobierno.CuotaService.CuotaExcedida;
import com.gelox.backend.gobierno.CuotaService.UsoCuota;
import com.gelox.backend.gobierno.PlanController;
import com.gelox.backend.gobierno.PlanService;
import com.gelox.backend.gobierno.TipoCuota;
import com.gelox.backend.gobierno.dto.AuditoriaFilaDTO;
import com.gelox.backend.gobierno.dto.AuditoriaUsuarioDTO;
import com.gelox.backend.gobierno.dto.PaginaDTO;
import com.gelox.backend.gobierno.dto.PlanDTO;
import com.gelox.backend.plataforma.DistribuidoraService;
import com.gelox.backend.plataforma.PlataformaController;
import com.gelox.backend.plataforma.dto.DistribuidoraDTO;
import com.gelox.backend.repositories.UsuarioRepository;
import com.gelox.backend.security.RequiereRol;
import com.gelox.backend.tenant.DistribuidoraEstadoCache;
import com.google.firebase.auth.FirebaseAuth;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * RF70/RF71 — /api/plataforma/planes, PATCH /distribuidoras/{id}/plan y /api/plataforma/auditoria:
 * el Superadministrador recibe la forma del contrato (mock de T193) y un Gerente recibe 403
 * en cada endpoint sin que se llame al servicio.
 */
@WebMvcTest(controllers = {PlanController.class, AuditoriaController.class, PlataformaController.class})
@Import(SecurityConfig.class)
class PlanesYAuditoriaControllerTest {

    private static final UUID PLAN = UUID.fromString("3f6a9c1e-1b2d-4e5f-8a9b-0c1d2e3f4a52");
    private static final UUID DISTRIBUIDORA = UUID.fromString("c4e8a2f1-3b5d-4c6e-8a9b-1d2e3f4a5b6c");

    @Autowired MockMvc mockMvc;

    @MockBean PlanService planService;
    @MockBean AuditoriaConsultaService consultaService;
    @MockBean DistribuidoraService distribuidoraService;
    @MockBean FirebaseAuth firebaseAuth;
    @MockBean DistribuidoraEstadoCache distribuidoraEstadoCache;
    @MockBean AuditoriaService auditoriaService;
    @MockBean UsuarioRepository usuarioRepository;

    private static Authentication como(RolUsuario rol) {
        Usuario u = TestHelper.buildUsuario("uid-" + rol, rol + "@gelox.test", rol, true);
        return TestHelper.authParaUsuario(u);
    }

    private static PlanDTO estandar() {
        return new PlanDTO(PLAN, "Estándar", "Plan intermedio", new BigDecimal("289000"),
                15, 600, 120, 5120, 2000, 1,
                List.of(new PlanDTO.DistribuidoraDelPlan(DISTRIBUIDORA, "Frío Express", EstadoDistribuidora.ACTIVO,
                        Map.of("productos", 534))));
    }

    private static final String PLAN_JSON = """
            {"nombre":"Estándar","descripcion":"Plan intermedio","precioMensual":289000,
             "maxUsuarios":15,"maxProductos":600,"maxComerciantes":120,"maxAlmacenamientoMb":5120,"maxLlamadasIaMes":null}
            """;

    // ── Planes ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("GET /planes (Superadministrador) → 200 con los campos del contrato")
    void listarPlanes() throws Exception {
        when(planService.listar()).thenReturn(List.of(estandar()));

        mockMvc.perform(get("/api/plataforma/planes").with(authentication(como(RolUsuario.SUPERADMINISTRADOR))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(PLAN.toString()))
                .andExpect(jsonPath("$[0].nombre").value("Estándar"))
                .andExpect(jsonPath("$[0].precioMensual").value(289000))
                .andExpect(jsonPath("$[0].maxAlmacenamientoMb").value(5120))
                .andExpect(jsonPath("$[0].maxLlamadasIaMes").value(2000))
                .andExpect(jsonPath("$[0].totalDistribuidoras").value(1))
                .andExpect(jsonPath("$[0].distribuidoras[0].nombreComercial").value("Frío Express"))
                .andExpect(jsonPath("$[0].distribuidoras[0].uso.productos").value(534));
    }

    @Test
    @DisplayName("GET /planes/{id} → 200")
    void obtenerPlan() throws Exception {
        when(planService.obtener(PLAN)).thenReturn(estandar());

        mockMvc.perform(get("/api/plataforma/planes/" + PLAN).with(authentication(como(RolUsuario.SUPERADMINISTRADOR))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.maxComerciantes").value(120));
    }

    @Test
    @DisplayName("POST /planes → 201; nombre repetido → 409 {error, campo}")
    void crearPlan() throws Exception {
        when(planService.crear(any())).thenReturn(estandar());

        mockMvc.perform(post("/api/plataforma/planes").contentType(MediaType.APPLICATION_JSON).content(PLAN_JSON)
                        .with(authentication(como(RolUsuario.SUPERADMINISTRADOR))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(PLAN.toString()));

        when(planService.crear(any())).thenThrow(
                new CampoInvalidoException(HttpStatus.CONFLICT, "Ya existe un plan con ese nombre", "nombre"));
        mockMvc.perform(post("/api/plataforma/planes").contentType(MediaType.APPLICATION_JSON).content(PLAN_JSON)
                        .with(authentication(como(RolUsuario.SUPERADMINISTRADOR))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Ya existe un plan con ese nombre"))
                .andExpect(jsonPath("$.campo").value("nombre"));
    }

    @Test
    @DisplayName("PUT /planes/{id} → 200; cuota negativa → 400 {campo}")
    void editarPlan() throws Exception {
        when(planService.editar(eq(PLAN), any())).thenReturn(estandar());

        mockMvc.perform(put("/api/plataforma/planes/" + PLAN).contentType(MediaType.APPLICATION_JSON).content(PLAN_JSON)
                        .with(authentication(como(RolUsuario.SUPERADMINISTRADOR))))
                .andExpect(status().isOk());

        when(planService.editar(eq(PLAN), any())).thenThrow(
                new CampoInvalidoException(HttpStatus.BAD_REQUEST, "Las cuotas no pueden ser negativas", "maxUsuarios"));
        mockMvc.perform(put("/api/plataforma/planes/" + PLAN).contentType(MediaType.APPLICATION_JSON).content(PLAN_JSON)
                        .with(authentication(como(RolUsuario.SUPERADMINISTRADOR))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campo").value("maxUsuarios"));
    }

    @Test
    @DisplayName("DELETE /planes/{id} → 204")
    void eliminarPlan() throws Exception {
        mockMvc.perform(delete("/api/plataforma/planes/" + PLAN).with(authentication(como(RolUsuario.SUPERADMINISTRADOR))))
                .andExpect(status().isNoContent());
        verify(planService).eliminar(PLAN);
    }

    // ── Asignar plan ────────────────────────────────────────────────────

    @Test
    @DisplayName("PATCH /distribuidoras/{id}/plan → 200 con planId, planNombre, plan, fechaPlan, uso y advertencias")
    void asignarPlan() throws Exception {
        Map<String, UsoCuota> uso = Map.of("productos", new UsoCuota(534L, 500));
        DistribuidoraDTO dto = new DistribuidoraDTO(DISTRIBUIDORA, "Frío Express S.A.S.", "901", "Frío Express",
                "frioexpress", "Ana", "g@frio.co", null, null, null, null, PLAN, "Estándar", "Estándar",
                LocalDateTime.of(2026, 11, 24, 10, 0), EstadoDistribuidora.ACTIVO, null, null, null, 3L,
                null, null, "#1F3864", "#2E75B6", uso,
                List.of(new CuotaExcedida(TipoCuota.PRODUCTOS, 534L, 500)), null);
        when(distribuidoraService.asignarPlan(eq(DISTRIBUIDORA), any())).thenReturn(dto);

        mockMvc.perform(patch("/api/plataforma/distribuidoras/" + DISTRIBUIDORA + "/plan")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"planId\":\"" + PLAN + "\",\"motivo\":\"Creció en catálogo\"}")
                        .with(authentication(como(RolUsuario.SUPERADMINISTRADOR))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.planId").value(PLAN.toString()))
                .andExpect(jsonPath("$.planNombre").value("Estándar"))
                .andExpect(jsonPath("$.plan").value("Estándar"))
                .andExpect(jsonPath("$.fechaPlan").value("2026-11-24T10:00:00"))
                .andExpect(jsonPath("$.uso.productos.usado").value(534))
                .andExpect(jsonPath("$.uso.productos.limite").value(500))
                .andExpect(jsonPath("$.advertencias[0].cuota").value("PRODUCTOS"))
                .andExpect(jsonPath("$.correoEnviado").doesNotExist());
    }

    // ── Auditoría ───────────────────────────────────────────────────────

    @Test
    @DisplayName("GET /auditoria con filtros → página {content, totalElements, totalPages, number, size}")
    void listarAuditoria() throws Exception {
        AuditoriaFilaDTO fila = new AuditoriaFilaDTO(UUID.randomUUID(), LocalDateTime.of(2026, 11, 23, 10, 30),
                "181.50.1.20", null, "Frío Express", UUID.randomUUID(), "Super", "super@gelox.app",
                "SUPERADMINISTRADOR", "PLAN_ASIGNAR", "Distribuidora", DISTRIBUIDORA,
                "{\"planAnterior\":\"Básico\",\"planNuevo\":\"Estándar\",\"motivo\":\"Creció\"}");
        when(consultaService.listar(any(), eq(0), eq(20))).thenReturn(new PaginaDTO<>(List.of(fila), 1, 1, 0, 20));

        mockMvc.perform(get("/api/plataforma/auditoria")
                        .param("page", "0").param("size", "20")
                        .param("distribuidoraId", DISTRIBUIDORA.toString())
                        .param("desde", "2026-11-01").param("hasta", "2026-11-30")
                        .param("accion", "PLAN_ASIGNAR")
                        .with(authentication(como(RolUsuario.SUPERADMINISTRADOR))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.number").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.content[0].distribuidoraNombre").value("Frío Express"))
                .andExpect(jsonPath("$.content[0].usuarioRol").value("SUPERADMINISTRADOR"))
                .andExpect(jsonPath("$.content[0].detalle").value(fila.detalle()));

        verify(consultaService).listar(eq(new AuditoriaConsultaService.Filtros(DISTRIBUIDORA, null,
                LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30), "PLAN_ASIGNAR")), eq(0), eq(20));
    }

    @Test
    @DisplayName("GET /auditoria/acciones y /auditoria/usuarios?distribuidoraId → 200")
    void filtrosDeAuditoria() throws Exception {
        when(consultaService.acciones()).thenReturn(List.of("USUARIO_CREAR", "PLAN_ASIGNAR"));
        when(consultaService.usuarios(DISTRIBUIDORA)).thenReturn(List.of(
                new AuditoriaUsuarioDTO(UUID.randomUUID(), "Kevin Ortega", "kevin@frio.co", "ENCARGADO_VENTAS", "Frío Express")));

        mockMvc.perform(get("/api/plataforma/auditoria/acciones").with(authentication(como(RolUsuario.SUPERADMINISTRADOR))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[1]").value("PLAN_ASIGNAR"));
        mockMvc.perform(get("/api/plataforma/auditoria/usuarios").param("distribuidoraId", DISTRIBUIDORA.toString())
                        .with(authentication(como(RolUsuario.SUPERADMINISTRADOR))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].distribuidoraNombre").value("Frío Express"));

        when(consultaService.usuarios(isNull())).thenReturn(List.of());
        mockMvc.perform(get("/api/plataforma/auditoria/usuarios").with(authentication(como(RolUsuario.SUPERADMINISTRADOR))))
                .andExpect(status().isOk());
    }

    // ── 403 ─────────────────────────────────────────────────────────────

    @ParameterizedTest(name = "{0} {1} como Gerente → 403")
    @CsvSource({
            "GET,    /api/plataforma/planes",
            "GET,    /api/plataforma/planes/3f6a9c1e-1b2d-4e5f-8a9b-0c1d2e3f4a52",
            "POST,   /api/plataforma/planes",
            "PUT,    /api/plataforma/planes/3f6a9c1e-1b2d-4e5f-8a9b-0c1d2e3f4a52",
            "DELETE, /api/plataforma/planes/3f6a9c1e-1b2d-4e5f-8a9b-0c1d2e3f4a52",
            "PATCH,  /api/plataforma/distribuidoras/c4e8a2f1-3b5d-4c6e-8a9b-1d2e3f4a5b6c/plan",
            "GET,    /api/plataforma/auditoria",
            "GET,    /api/plataforma/auditoria/acciones",
            "GET,    /api/plataforma/auditoria/usuarios"
    })
    void gerenteRecibe403(String metodo, String ruta) throws Exception {
        MockHttpServletRequestBuilder peticion = MockMvcRequestBuilders.request(metodo, java.net.URI.create(ruta))
                .contentType(MediaType.APPLICATION_JSON)
                .content(metodo.equals("PATCH") ? "{\"planId\":\"" + PLAN + "\",\"motivo\":\"x\"}" : PLAN_JSON);

        mockMvc.perform(peticion.with(authentication(como(RolUsuario.ADMINISTRADOR))))
                .andExpect(status().isForbidden());

        verifyNoInteractions(planService, consultaService, distribuidoraService);
    }

    @Test
    @DisplayName("Sin token → 401 en /planes y /auditoria")
    void sinToken401() throws Exception {
        mockMvc.perform(get("/api/plataforma/planes")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/plataforma/auditoria")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Todos los métodos públicos de PlanService y AuditoriaConsultaService, y asignarPlan, llevan @RequiereRol SUPERADMINISTRADOR")
    void requiereRolEnServicios() throws Exception {
        for (Class<?> clase : List.of(PlanService.class, AuditoriaConsultaService.class)) {
            Arrays.stream(clase.getDeclaredMethods())
                    .filter(m -> Modifier.isPublic(m.getModifiers()) && !Modifier.isStatic(m.getModifiers()))
                    .forEach(m -> assertThat(m.getAnnotation(RequiereRol.class))
                            .as(clase.getSimpleName() + "." + m.getName()).isNotNull()
                            .extracting(RequiereRol::value).isEqualTo(new String[]{"SUPERADMINISTRADOR"}));
        }
        Method asignar = DistribuidoraService.class.getMethod("asignarPlan", UUID.class,
                com.gelox.backend.plataforma.dto.AsignarPlanRequest.class);
        assertThat(asignar.getAnnotation(RequiereRol.class).value()).containsExactly("SUPERADMINISTRADOR");
    }
}
