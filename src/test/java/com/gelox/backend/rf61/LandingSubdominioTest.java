package com.gelox.backend.rf61;

import com.gelox.backend.controllers.LandingController;
import com.gelox.backend.entities.CategoriaProducto;
import com.gelox.backend.entities.Distribuidora;
import com.gelox.backend.entities.EstadoDistribuidora;
import com.gelox.backend.entities.Producto;
import com.gelox.backend.exceptions.GlobalExceptionHandler;
import com.gelox.backend.repositories.DistribuidoraRepository;
import com.gelox.backend.repositories.ProductoRepository;
import com.gelox.backend.services.LandingService;
import com.gelox.backend.tenant.TenantContext;
import com.gelox.backend.tenant.TenantFilterAspect;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.aop.AopAutoConfiguration;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * RF61 — Landing pública por subdominio (T119), sobre H2 con el filtro por tenant activo.
 * Las peticiones van por LandingController con MockMvc standalone y el LandingService real.
 */
@DataJpaTest(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"
})
@Import({LandingService.class, TenantFilterAspect.class})
@ImportAutoConfiguration(AopAutoConfiguration.class)
class LandingSubdominioTest {

    @Autowired
    LandingService landingService;

    @Autowired
    DistribuidoraRepository distribuidoraRepository;

    @Autowired
    ProductoRepository productoRepository;

    LandingController landingController;
    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        TenantContext.clear();
        landingController = new LandingController(landingService);
        mockMvc = MockMvcBuilders.standaloneSetup(landingController)
                .setControllerAdvice(new GlobalExceptionHandler())
                // standalone no ordena los conversores como Spring Boot: sin esto respondería XML
                .defaultRequest(get("/").accept(MediaType.APPLICATION_JSON))
                .build();

        Distribuidora piloto = distribuidora("magicosabor", "Mágico Sabor", EstadoDistribuidora.ACTIVO, "573124099628");
        Distribuidora otra = distribuidora("heladosnorte", "Helados Norte", EstadoDistribuidora.PRUEBA, null);
        Distribuidora suspendida = distribuidora("suspendida", "Suspendida SAS", EstadoDistribuidora.SUSPENDIDO, "573000000000");

        producto("MS-1", "Paleta Mora", CategoriaProducto.PALETAS, piloto);
        producto("MS-2", "Cono Vainilla", CategoriaProducto.CONOS, piloto);
        producto("HN-1", "Paleta Limón Norte", CategoriaProducto.PALETAS, otra);
        producto("SU-1", "Paleta Suspendida", CategoriaProducto.PALETAS, suspendida);
    }

    @AfterEach
    void limpiar() {
        TenantContext.clear();
    }

    private Distribuidora distribuidora(String subdominio, String nombre, EstadoDistribuidora estado, String whatsapp) {
        return distribuidoraRepository.saveAndFlush(Distribuidora.builder()
                .razonSocial(nombre)
                .nit("NIT-" + subdominio)
                .nombreComercial(nombre)
                .subdominio(subdominio)
                .correoGerente("gerente@" + subdominio + ".test")
                .plan("PILOTO")
                .estado(estado)
                .colorPrimario("#9E2016")
                .colorSecundario("#7C0202")
                .direccion("Calle " + subdominio)
                .barrio("Barrio " + subdominio)
                .ciudad("Cúcuta")
                .horario("Lunes a Domingo")
                .mapsUrl("https://maps.test/" + subdominio)
                .whatsapp(whatsapp)
                .eslogan("Helados para todos")
                .build());
    }

    private void producto(String codigo, String nombre, CategoriaProducto categoria, Distribuidora d) {
        Producto p = new Producto();
        p.setCodigoTecnico(codigo);
        p.setNombre(nombre);
        p.setCategoria(categoria);
        p.setPrecioVenta(BigDecimal.valueOf(3000));
        p.setPrecioCosto(BigDecimal.valueOf(2000));
        p.setTenantId(d.getId());
        productoRepository.saveAndFlush(p);
    }

    @Test
    @DisplayName("/api/landing/magicosabor/productos trae solo el catálogo de Mágico Sabor")
    void catalogoPorSubdominio_sinProductosDeOtraDistribuidora() throws Exception {
        mockMvc.perform(get("/api/landing/magicosabor/productos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.PALETAS[*].nombre", containsInAnyOrder("Paleta Mora")))
                .andExpect(jsonPath("$.CONOS[*].nombre", containsInAnyOrder("Cono Vainilla")));

        mockMvc.perform(get("/api/landing/heladosnorte/productos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.PALETAS[*].nombre", containsInAnyOrder("Paleta Limón Norte")))
                .andExpect(jsonPath("$.CONOS").doesNotExist());
    }

    @Test
    @DisplayName("la ruta actual sin subdominio devuelve el mismo catálogo que magicosabor")
    void rutaSinSubdominio_esLaDistribuidoraPiloto() {
        assertThat(landingController.productos().getBody()).isEqualTo(landingService.productos("magicosabor"));
    }

    @Test
    @DisplayName("las rutas viejas por categoría siguen funcionando y usan la piloto")
    void rutasPorCategoria_siguenFuncionando() throws Exception {
        mockMvc.perform(get("/api/landing/productos/paletas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].nombre").value("Paleta Mora"));
    }

    @Test
    @DisplayName("subdominio suspendido → 404")
    void subdominioSuspendido_404() throws Exception {
        mockMvc.perform(get("/api/landing/suspendida/productos"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Distribuidora no encontrada"));
        mockMvc.perform(get("/api/landing/suspendida/identidad"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("subdominio inexistente → 404")
    void subdominioInexistente_404() throws Exception {
        mockMvc.perform(get("/api/landing/noexiste/productos"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Distribuidora no encontrada"));
    }

    @Test
    @DisplayName("info-negocio, whatsapp e identidad salen de la distribuidora")
    void datosDelNegocio_salenDeLaDistribuidora() throws Exception {
        mockMvc.perform(get("/api/landing/magicosabor/info-negocio"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombreComercial").value("Mágico Sabor"))
                .andExpect(jsonPath("$.eslogan").value("Helados para todos"))
                .andExpect(jsonPath("$.direccion").value("Calle magicosabor"))
                .andExpect(jsonPath("$.whatsapp").value("573124099628"))
                .andExpect(jsonPath("$.enlaceComoLlegar").value("https://maps.test/magicosabor"));
        mockMvc.perform(get("/api/landing/magicosabor/whatsapp"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url").value("https://wa.me/573124099628"));
        mockMvc.perform(get("/api/landing/heladosnorte/identidad"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombreComercial").value("Helados Norte"))
                .andExpect(jsonPath("$.colorPrimario").value("#9E2016"));
    }

    @Test
    @DisplayName("distribuidora sin WhatsApp configurado → 404")
    void sinWhatsapp_404() throws Exception {
        mockMvc.perform(get("/api/landing/heladosnorte/whatsapp"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("el TenantContext queda vacío después de la petición pública (también si responde 404)")
    void peticionPublica_dejaElContextoVacio() throws Exception {
        mockMvc.perform(get("/api/landing/magicosabor/productos")).andExpect(status().isOk());
        assertThat(TenantContext.get()).isNull();

        mockMvc.perform(get("/api/landing/suspendida/productos")).andExpect(status().isNotFound());
        assertThat(TenantContext.get()).isNull();
    }

    @Test
    @DisplayName("el tenant puesto a mano no queda en el contexto, y si había uno se restaura")
    void tenantManual_seRestaura() {
        landingService.productos("magicosabor");
        assertThat(TenantContext.get()).isNull();

        UUID tenantDeLaPeticion = UUID.randomUUID();
        TenantContext.set(tenantDeLaPeticion);
        landingService.productos("heladosnorte");
        assertThat(TenantContext.get()).isEqualTo(tenantDeLaPeticion);
    }
}
