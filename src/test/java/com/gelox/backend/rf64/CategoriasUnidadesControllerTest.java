package com.gelox.backend.rf64;

import com.gelox.backend.TestHelper;
import com.gelox.backend.catalogo.CategoriaController;
import com.gelox.backend.catalogo.CategoriaService;
import com.gelox.backend.catalogo.UnidadMedidaController;
import com.gelox.backend.catalogo.UnidadMedidaService;
import com.gelox.backend.config.SecurityConfig;
import com.gelox.backend.entities.Categoria;
import com.gelox.backend.entities.RolUsuario;
import com.gelox.backend.entities.UnidadMedida;
import com.gelox.backend.gobierno.AuditoriaService;
import com.gelox.backend.repositories.CategoriaRepository;
import com.gelox.backend.repositories.UnidadMedidaRepository;
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

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * RF64 — GET y POST de /api/categorias y /api/unidades con los servicios reales.
 */
@WebMvcTest(controllers = {CategoriaController.class, UnidadMedidaController.class})
@Import({SecurityConfig.class, CategoriaService.class, UnidadMedidaService.class, RolVerificacionAspect.class})
@ImportAutoConfiguration(AopAutoConfiguration.class)
class CategoriasUnidadesControllerTest {

    private static final UUID TENANT = UUID.randomUUID();

    @Autowired MockMvc mockMvc;

    @MockBean CategoriaRepository categoriaRepository;
    @MockBean UnidadMedidaRepository unidadMedidaRepository;
    @MockBean FirebaseAuth firebaseAuth;
    @MockBean DistribuidoraEstadoCache distribuidoraEstadoCache;
    @MockBean UsuarioRepository usuarioRepository;
    @MockBean AuditoriaService auditoriaService;

    @BeforeEach
    void setUp() {
        TenantContext.set(TENANT);
        when(categoriaRepository.save(any(Categoria.class))).thenAnswer(inv -> {
            Categoria c = inv.getArgument(0);
            c.setId(UUID.randomUUID());
            return c;
        });
        when(unidadMedidaRepository.save(any(UnidadMedida.class))).thenAnswer(inv -> {
            UnidadMedida u = inv.getArgument(0);
            u.setId(UUID.randomUUID());
            return u;
        });
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private static Authentication como(RolUsuario rol) {
        return TestHelper.authParaUsuario(TestHelper.buildUsuario("uid-" + rol, rol + "@gelox.test", rol, true));
    }

    @Test
    @DisplayName("GET /api/categorias lista las de la distribuidora en su orden")
    void listarCategorias() throws Exception {
        Categoria paletas = new Categoria();
        paletas.setId(UUID.randomUUID());
        paletas.setNombre("PALETAS");
        paletas.setOrden(1);
        when(categoriaRepository.findAllByOrderByOrdenAscNombreAsc()).thenReturn(List.of(paletas));

        mockMvc.perform(get("/api/categorias").with(authentication(como(RolUsuario.ENCARGADO_VENTAS))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].nombre").value("PALETAS"))
                .andExpect(jsonPath("$[0].orden").value(1))
                .andExpect(jsonPath("$[0].activo").value(true));
    }

    @Test
    @DisplayName("POST /api/categorias → 201; nombre repetido → 409; sin nombre → 400")
    void crearCategoria() throws Exception {
        mockMvc.perform(post("/api/categorias").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"BEBIDAS\",\"orden\":5}")
                        .with(authentication(como(RolUsuario.ENCARGADO_INVENTARIO))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nombre").value("BEBIDAS"))
                .andExpect(jsonPath("$.orden").value(5));

        when(categoriaRepository.existsByNombreIgnoreCase("PALETAS")).thenReturn(true);
        mockMvc.perform(post("/api/categorias").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"PALETAS\"}")
                        .with(authentication(como(RolUsuario.ADMINISTRADOR))))
                .andExpect(status().isConflict());

        mockMvc.perform(post("/api/categorias").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"\"}")
                        .with(authentication(como(RolUsuario.ADMINISTRADOR))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("ENCARGADO_VENTAS puede listar pero no crear categorías ni unidades → 403")
    void ventasNoCrea() throws Exception {
        mockMvc.perform(post("/api/categorias").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"BEBIDAS\"}")
                        .with(authentication(como(RolUsuario.ENCARGADO_VENTAS))))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/unidades").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"codigo\":\"BOL\",\"nombre\":\"Bolsa\"}")
                        .with(authentication(como(RolUsuario.ENCARGADO_VENTAS))))
                .andExpect(status().isForbidden());

        verify(categoriaRepository, never()).save(any());
        verify(unidadMedidaRepository, never()).save(any());
    }

    @Test
    @DisplayName("GET /api/unidades lista las unidades de la distribuidora")
    void listarUnidades() throws Exception {
        UnidadMedida un = new UnidadMedida();
        un.setId(UUID.randomUUID());
        un.setCodigo("UN");
        un.setNombre("Unidad");
        when(unidadMedidaRepository.findAllByOrderByCodigoAsc()).thenReturn(List.of(un));

        mockMvc.perform(get("/api/unidades").with(authentication(como(RolUsuario.ADMINISTRADOR))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].codigo").value("UN"))
                .andExpect(jsonPath("$[0].nombre").value("Unidad"));
    }

    @Test
    @DisplayName("POST /api/unidades → 201 con el código en mayúsculas; código repetido → 409")
    void crearUnidad() throws Exception {
        mockMvc.perform(post("/api/unidades").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"codigo\":\"bol\",\"nombre\":\"Bolsa\"}")
                        .with(authentication(como(RolUsuario.ENCARGADO_INVENTARIO))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.codigo").value("BOL"));

        when(unidadMedidaRepository.existsByCodigoIgnoreCase("UN")).thenReturn(true);
        mockMvc.perform(post("/api/unidades").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"codigo\":\"UN\",\"nombre\":\"Unidad\"}")
                        .with(authentication(como(RolUsuario.ADMINISTRADOR))))
                .andExpect(status().isConflict());

        mockMvc.perform(post("/api/unidades").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Sin código\"}")
                        .with(authentication(como(RolUsuario.ADMINISTRADOR))))
                .andExpect(status().isBadRequest());
    }
}
