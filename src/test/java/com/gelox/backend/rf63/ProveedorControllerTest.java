package com.gelox.backend.rf63;

import com.gelox.backend.TestHelper;
import com.gelox.backend.config.SecurityConfig;
import com.gelox.backend.entities.Proveedor;
import com.gelox.backend.entities.RolUsuario;
import com.gelox.backend.entities.Usuario;
import com.gelox.backend.gobierno.AuditoriaAspect;
import com.gelox.backend.gobierno.AuditoriaService;
import com.gelox.backend.proveedores.ProveedorController;
import com.gelox.backend.proveedores.ProveedorService;
import com.gelox.backend.repositories.ProveedorRepository;
import com.gelox.backend.repositories.UsuarioRepository;
import com.gelox.backend.security.RolVerificacionAspect;
import com.gelox.backend.tenant.DistribuidoraEstadoCache;
import com.gelox.backend.tenant.TenantContext;
import com.google.firebase.auth.FirebaseAuth;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.aop.AopAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * RF63 — /api/proveedores con el servicio real (roles y auditoría incluidos) sobre un
 * repositorio simulado: validaciones 400, proveedor de otra distribuidora 404, NIT repetido 409
 * y las acciones PROVEEDOR_CREAR / PROVEEDOR_EDITAR / PROVEEDOR_ACTIVAR.
 */
@WebMvcTest(controllers = ProveedorController.class)
@Import({SecurityConfig.class, ProveedorService.class, RolVerificacionAspect.class, AuditoriaAspect.class})
@ImportAutoConfiguration(AopAutoConfiguration.class)
class ProveedorControllerTest {

    private static final UUID TENANT = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID OTRA_DISTRIBUIDORA = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID PROVEEDOR = UUID.fromString("c4e8a2f1-3b5d-4c6e-8a9b-1d2e3f4a5b6c");

    private static final String BODY_VALIDO = """
            {"nombre":"Nutresa","nit":"890900050","contactoNombre":"Ana","telefono":"3001112233",
             "correo":"ventas@nutresa.test","tiempoEntregaDias":2,"diasHabiles":true,"montoMinimoPedido":150000}
            """;

    @Autowired MockMvc mockMvc;

    @MockBean ProveedorRepository proveedorRepository;
    @MockBean AuditoriaService auditoriaService;
    @MockBean FirebaseAuth firebaseAuth;
    @MockBean DistribuidoraEstadoCache distribuidoraEstadoCache;
    @MockBean UsuarioRepository usuarioRepository;

    @BeforeEach
    void setUp() {
        TenantContext.set(TENANT);
        when(proveedorRepository.save(any(Proveedor.class))).thenAnswer(inv -> {
            Proveedor p = inv.getArgument(0);
            if (p.getId() == null) p.setId(PROVEEDOR);
            p.setTenantId(TENANT);
            return p;
        });
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private static Authentication como(RolUsuario rol) {
        return TestHelper.authParaUsuario(TestHelper.buildUsuario("uid-" + rol, rol + "@gelox.test", rol, true));
    }

    private static Proveedor proveedor(UUID tenant, boolean activo) {
        Proveedor p = new Proveedor();
        p.setId(PROVEEDOR);
        p.setTenantId(tenant);
        p.setNombre("Nutresa");
        p.setNit("890900050");
        p.setActivo(activo);
        return p;
    }

    // ── POST ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("POST crea el proveedor (201) y audita PROVEEDOR_CREAR con su id")
    void crear() throws Exception {
        mockMvc.perform(post("/api/proveedores").contentType(MediaType.APPLICATION_JSON).content(BODY_VALIDO)
                        .with(authentication(como(RolUsuario.ENCARGADO_INVENTARIO))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(PROVEEDOR.toString()))
                .andExpect(jsonPath("$.nombre").value("Nutresa"))
                .andExpect(jsonPath("$.tiempoEntregaDias").value(2))
                .andExpect(jsonPath("$.montoMinimoPedido").value(150000))
                .andExpect(jsonPath("$.activo").value(true));

        verify(auditoriaService).registrar(eq("PROVEEDOR_CREAR"), eq("Proveedor"), eq(PROVEEDOR), any(), any());
    }

    @Test
    @DisplayName("POST sin nombre → 400 y no se guarda ni se audita")
    void nombreObligatorio() throws Exception {
        mockMvc.perform(post("/api/proveedores").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"  \",\"tiempoEntregaDias\":1}")
                        .with(authentication(como(RolUsuario.ADMINISTRADOR))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.nombre").value("El nombre es obligatorio"));

        verify(proveedorRepository, never()).save(any());
        verifyNoInteractions(auditoriaService);
    }

    @Test
    @DisplayName("POST con tiempo de entrega negativo → 400")
    void tiempoNoNegativo() throws Exception {
        mockMvc.perform(post("/api/proveedores").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Nutresa\",\"tiempoEntregaDias\":-1}")
                        .with(authentication(como(RolUsuario.ADMINISTRADOR))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.tiempoEntregaDias").value("El tiempo de entrega no puede ser negativo"));

        verify(proveedorRepository, never()).save(any());
    }

    @Test
    @DisplayName("POST con monto mínimo negativo → 400")
    void montoNoNegativo() throws Exception {
        mockMvc.perform(post("/api/proveedores").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Nutresa\",\"montoMinimoPedido\":-0.01}")
                        .with(authentication(como(RolUsuario.ADMINISTRADOR))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.montoMinimoPedido").value("El monto mínimo de pedido no puede ser negativo"));

        verify(proveedorRepository, never()).save(any());
    }

    @Test
    @DisplayName("POST con tiempo 0 y monto 0 es válido (no negativos)")
    void ceroEsValido() throws Exception {
        mockMvc.perform(post("/api/proveedores").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Local\",\"tiempoEntregaDias\":0,\"montoMinimoPedido\":0}")
                        .with(authentication(como(RolUsuario.ADMINISTRADOR))))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("POST con un NIT que ya existe en la distribuidora → 409")
    void nitRepetido() throws Exception {
        when(proveedorRepository.existsByNit("890900050")).thenReturn(true);

        mockMvc.perform(post("/api/proveedores").contentType(MediaType.APPLICATION_JSON).content(BODY_VALIDO)
                        .with(authentication(como(RolUsuario.ADMINISTRADOR))))
                .andExpect(status().isConflict());

        verify(proveedorRepository, never()).save(any());
        verifyNoInteractions(auditoriaService);
    }

    // ── GET ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("GET ?q= busca por nombre o NIT dentro de la distribuidora")
    void buscar() throws Exception {
        when(proveedorRepository.buscar("nut", null)).thenReturn(List.of(proveedor(TENANT, true)));

        mockMvc.perform(get("/api/proveedores").param("q", " nut ")
                        .with(authentication(como(RolUsuario.ENCARGADO_INVENTARIO))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].nombre").value("Nutresa"));
    }

    @Test
    @DisplayName("GET /{id} de una distribuidora ajena → 404")
    void obtenerDeOtraDistribuidora() throws Exception {
        when(proveedorRepository.findById(PROVEEDOR)).thenReturn(Optional.of(proveedor(OTRA_DISTRIBUIDORA, true)));

        mockMvc.perform(get("/api/proveedores/" + PROVEEDOR).with(authentication(como(RolUsuario.ADMINISTRADOR))))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET /{id} propio → 200")
    void obtenerPropio() throws Exception {
        when(proveedorRepository.findById(PROVEEDOR)).thenReturn(Optional.of(proveedor(TENANT, true)));

        mockMvc.perform(get("/api/proveedores/" + PROVEEDOR).with(authentication(como(RolUsuario.ADMINISTRADOR))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nit").value("890900050"));
    }

    // ── PUT / PATCH ─────────────────────────────────────────────────────

    @Test
    @DisplayName("PUT edita el proveedor y audita PROVEEDOR_EDITAR con el id de la ruta")
    void editar() throws Exception {
        when(proveedorRepository.findById(PROVEEDOR)).thenReturn(Optional.of(proveedor(TENANT, true)));

        mockMvc.perform(put("/api/proveedores/" + PROVEEDOR).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Nutresa S.A.\",\"nit\":\"890900050\",\"montoMinimoPedido\":200000}")
                        .with(authentication(como(RolUsuario.ADMINISTRADOR))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Nutresa S.A."))
                .andExpect(jsonPath("$.montoMinimoPedido").value(BigDecimal.valueOf(200000)));

        verify(auditoriaService).registrar(eq("PROVEEDOR_EDITAR"), eq("Proveedor"), eq(PROVEEDOR), any(), any());
    }

    @Test
    @DisplayName("PUT de un proveedor ajeno → 404 y no se guarda")
    void editarDeOtraDistribuidora() throws Exception {
        when(proveedorRepository.findById(PROVEEDOR)).thenReturn(Optional.of(proveedor(OTRA_DISTRIBUIDORA, true)));

        mockMvc.perform(put("/api/proveedores/" + PROVEEDOR).contentType(MediaType.APPLICATION_JSON).content(BODY_VALIDO)
                        .with(authentication(como(RolUsuario.ADMINISTRADOR))))
                .andExpect(status().isNotFound());

        verify(proveedorRepository, never()).save(any());
        verifyNoInteractions(auditoriaService);
    }

    @Test
    @DisplayName("PUT con el NIT de otro proveedor → 409")
    void editarNitRepetido() throws Exception {
        when(proveedorRepository.findById(PROVEEDOR)).thenReturn(Optional.of(proveedor(TENANT, true)));
        when(proveedorRepository.existsByNitAndIdNot("890900050", PROVEEDOR)).thenReturn(true);

        mockMvc.perform(put("/api/proveedores/" + PROVEEDOR).contentType(MediaType.APPLICATION_JSON).content(BODY_VALIDO)
                        .with(authentication(como(RolUsuario.ADMINISTRADOR))))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("PATCH /{id}/activar reactiva al proveedor y audita PROVEEDOR_ACTIVAR")
    void activar() throws Exception {
        when(proveedorRepository.findById(PROVEEDOR)).thenReturn(Optional.of(proveedor(TENANT, false)));

        mockMvc.perform(patch("/api/proveedores/" + PROVEEDOR + "/activar")
                        .with(authentication(como(RolUsuario.ENCARGADO_INVENTARIO))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activo").value(true));

        verify(auditoriaService).registrar(eq("PROVEEDOR_ACTIVAR"), eq("Proveedor"), eq(PROVEEDOR), any(), any());
    }

    @Test
    @DisplayName("PATCH /{id}/activar?activo=false lo inactiva")
    void inactivar() throws Exception {
        when(proveedorRepository.findById(PROVEEDOR)).thenReturn(Optional.of(proveedor(TENANT, true)));

        mockMvc.perform(patch("/api/proveedores/" + PROVEEDOR + "/activar").param("activo", "false")
                        .with(authentication(como(RolUsuario.ADMINISTRADOR))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activo").value(false));
    }

    @Test
    @DisplayName("PATCH /{id}/activar de un proveedor ajeno → 404")
    void activarDeOtraDistribuidora() throws Exception {
        when(proveedorRepository.findById(PROVEEDOR)).thenReturn(Optional.of(proveedor(OTRA_DISTRIBUIDORA, false)));

        mockMvc.perform(patch("/api/proveedores/" + PROVEEDOR + "/activar")
                        .with(authentication(como(RolUsuario.ADMINISTRADOR))))
                .andExpect(status().isNotFound());

        verify(proveedorRepository, never()).save(any());
    }

    // ── Roles ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("ENCARGADO_VENTAS no puede listar ni crear proveedores → 403")
    void soloAdministradorEInventario() throws Exception {
        Authentication ventas = como(RolUsuario.ENCARGADO_VENTAS);

        mockMvc.perform(get("/api/proveedores").with(authentication(ventas)))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/proveedores").contentType(MediaType.APPLICATION_JSON).content(BODY_VALIDO)
                        .with(authentication(ventas)))
                .andExpect(status().isForbidden());

        verify(proveedorRepository, never()).save(any());
    }

    @Test
    @DisplayName("Sin token → 401")
    void sinToken() throws Exception {
        mockMvc.perform(get("/api/proveedores")).andExpect(status().isUnauthorized());
    }
}
