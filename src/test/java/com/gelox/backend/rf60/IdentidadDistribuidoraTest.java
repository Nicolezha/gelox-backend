package com.gelox.backend.rf60;

import com.gelox.backend.TestHelper;
import com.gelox.backend.dto.IdentidadDTO;
import com.gelox.backend.dto.PlanillaImpresionResponseDTO;
import com.gelox.backend.entities.Comerciante;
import com.gelox.backend.entities.Distribuidora;
import com.gelox.backend.entities.EstadoDistribuidora;
import com.gelox.backend.entities.EstadoPedido;
import com.gelox.backend.entities.PedidoProveedor;
import com.gelox.backend.entities.PlanillaComerciante;
import com.gelox.backend.entities.RolUsuario;
import com.gelox.backend.entities.Usuario;
import com.gelox.backend.repositories.DistribuidoraRepository;
import com.gelox.backend.repositories.ItemPlanillaRepository;
import com.gelox.backend.repositories.PedidoProveedorRepository;
import com.gelox.backend.repositories.PlanillaComercianteRepository;
import com.gelox.backend.security.RolVerificacionAspect;
import com.gelox.backend.services.IdentidadDistribuidoraService;
import com.gelox.backend.services.PedidoProveedorService;
import com.gelox.backend.services.PlanillaService;
import com.gelox.backend.services.SupabaseStorageService;
import com.gelox.backend.tenant.TenantContext;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * RF60 — Identidad visual de la distribuidora (T119).
 * El rol se prueba con el RolVerificacionAspect real, aplicado con un proxy de AspectJ.
 */
@ExtendWith(MockitoExtension.class)
class IdentidadDistribuidoraTest {

    private static final UUID TENANT = UUID.randomUUID();
    private static final String CARPETA = "distribuidoras/" + TENANT;
    private static final String LOGO_URL = "https://storage.test/" + CARPETA + "/logo.png";

    @Mock
    DistribuidoraRepository distribuidoraRepository;

    @Mock
    SupabaseStorageService storageService;

    IdentidadDistribuidoraService service;
    Distribuidora distribuidora;

    @BeforeEach
    void setUp() {
        service = new IdentidadDistribuidoraService(distribuidoraRepository, storageService);
        distribuidora = Distribuidora.builder()
                .id(TENANT)
                .razonSocial("Mágico Sabor")
                .nit("900000001")
                .nombreComercial("Mágico Sabor")
                .subdominio("magicosabor")
                .correoGerente("gerente@magicosabor.test")
                .plan("PILOTO")
                .estado(EstadoDistribuidora.ACTIVO)
                .colorPrimario("#9E2016")
                .colorSecundario("#7C0202")
                .build();
        TenantContext.set(TENANT);
        lenient().when(distribuidoraRepository.findById(TENANT)).thenReturn(Optional.of(distribuidora));
        lenient().when(distribuidoraRepository.save(any(Distribuidora.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @AfterEach
    void limpiar() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    /** El servicio con @RequiereRol aplicado y el usuario indicado en el SecurityContext. */
    private IdentidadDistribuidoraService comoUsuario(RolUsuario rol) {
        Usuario usuario = TestHelper.buildUsuario("uid-" + rol, rol + "@gelox-test.com", rol, true);
        SecurityContextHolder.getContext().setAuthentication(TestHelper.authParaUsuario(usuario));
        AspectJProxyFactory factory = new AspectJProxyFactory(service);
        factory.setProxyTargetClass(true);
        factory.addAspect(new RolVerificacionAspect());
        return factory.getProxy();
    }

    private static MockMultipartFile imagen(String nombre, String tipo, int bytes) {
        return new MockMultipartFile(nombre, nombre + (tipo.contains("svg") ? ".svg" : ".png"), tipo, new byte[bytes]);
    }

    @Test
    @DisplayName("GET devuelve la identidad de la distribuidora del contexto")
    void obtener_devuelveLaIdentidadActual() {
        IdentidadDTO identidad = service.obtenerIdentidad();

        assertThat(identidad.nombreComercial()).isEqualTo("Mágico Sabor");
        assertThat(identidad.colorPrimario()).isEqualTo("#9E2016");
        assertThat(identidad.colorSecundario()).isEqualTo("#7C0202");
    }

    @Test
    @DisplayName("el Gerente cambia color y logo, y GET devuelve los valores nuevos")
    void gerente_cambiaColorYLogo_getDevuelveLoNuevo() {
        MockMultipartFile logo = imagen("logo", "image/png", 1024);
        when(storageService.subirImagen(eq(logo), eq(CARPETA), anySet())).thenReturn(LOGO_URL);

        comoUsuario(RolUsuario.ADMINISTRADOR)
                .actualizarIdentidad("Mágico Sabor Norte", "#112233", "#AABBCC", logo, null);

        IdentidadDTO despues = service.obtenerIdentidad();
        assertThat(despues.nombreComercial()).isEqualTo("Mágico Sabor Norte");
        assertThat(despues.colorPrimario()).isEqualTo("#112233");
        assertThat(despues.colorSecundario()).isEqualTo("#AABBCC");
        assertThat(despues.logoUrl()).isEqualTo(LOGO_URL);
        assertThat(despues.faviconUrl()).isNull();
    }

    @Test
    @DisplayName("color inválido → 400 sin subir archivos ni guardar")
    void colorInvalido_400() {
        MockMultipartFile logo = imagen("logo", "image/png", 1024);

        assertThatThrownBy(() -> comoUsuario(RolUsuario.ADMINISTRADOR)
                .actualizarIdentidad("Mágico Sabor", "rojo", "#AABBCC", logo, null))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));

        assertThatThrownBy(() -> service.actualizarIdentidad("Mágico Sabor", "#112233", "#12345", null, null))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));

        verifyNoInteractions(storageService);
        verify(distribuidoraRepository, never()).save(any());
    }

    @Test
    @DisplayName("nombre comercial vacío → 400")
    void nombreVacio_400() {
        assertThatThrownBy(() -> service.actualizarIdentidad("   ", "#112233", "#AABBCC", null, null))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    @DisplayName("ENCARGADO_VENTAS intenta el PUT → 403")
    void encargadoVentas_put_403() {
        IdentidadDistribuidoraService proxy = comoUsuario(RolUsuario.ENCARGADO_VENTAS);

        assertThatThrownBy(() -> proxy.actualizarIdentidad("Otro", "#112233", "#AABBCC", null, null))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));

        verify(distribuidoraRepository, never()).save(any());
    }

    @Test
    @DisplayName("ENCARGADO_VENTAS sí puede leer la identidad (GET)")
    void encargadoVentas_get_200() {
        assertThat(comoUsuario(RolUsuario.ENCARGADO_VENTAS).obtenerIdentidad().nombreComercial())
                .isEqualTo("Mágico Sabor");
    }

    @Test
    @DisplayName("logo SVG se acepta y se sube a distribuidoras/{tenantId}")
    void logoSvg_seAcepta() {
        MockMultipartFile logo = imagen("logo", "image/svg+xml", 2048);
        when(storageService.subirImagen(eq(logo), eq(CARPETA), anySet())).thenReturn(LOGO_URL);

        IdentidadDTO resultado = service.actualizarIdentidad("Mágico Sabor", "#112233", "#AABBCC", logo, null);

        assertThat(resultado.logoUrl()).isEqualTo(LOGO_URL);
    }

    @Test
    @DisplayName("logo de más de 2 MB → 413; tipo no permitido (GIF) → 415")
    void logoInvalido_413y415() {
        MockMultipartFile grande = imagen("logo", "image/png", 2 * 1024 * 1024 + 1);
        MockMultipartFile gif = imagen("logo", "image/gif", 1024);

        assertThatThrownBy(() -> service.actualizarIdentidad("Mágico Sabor", "#112233", "#AABBCC", grande, null))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE));
        assertThatThrownBy(() -> service.actualizarIdentidad("Mágico Sabor", "#112233", "#AABBCC", null, gif))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE));
        verifyNoInteractions(storageService);
    }

    @Test
    @DisplayName("el Excel del pedido lleva el nombre comercial en el título y conserva las columnas Nutresa")
    void excelPedido_llevaNombreComercial() throws Exception {
        PedidoProveedorRepository pedidoRepo = mock(PedidoProveedorRepository.class);
        PedidoProveedorService pedidoService = new PedidoProveedorService(
                pedidoRepo, null, null, null, null, null, service);
        ReflectionTestUtils.setField(pedidoService, "plantillaExcel", new ClassPathResource("templates/catalogo.xlsx"));
        PedidoProveedor pedido = PedidoProveedor.builder()
                .id(UUID.randomUUID()).fecha(LocalDate.now()).estado(EstadoPedido.PENDIENTE).build();
        pedido.setItems(new ArrayList<>());
        when(pedidoRepo.findByIdWithItems(pedido.getId())).thenReturn(Optional.of(pedido));

        byte[] excel = pedidoService.exportarExcelPedido(pedido.getId());

        DataFormatter f = new DataFormatter();
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(excel))) {
            Sheet sheet = wb.getSheetAt(0);
            assertThat(f.formatCellValue(sheet.getRow(0).getCell(0)))
                    .contains("Formato de pedido masivo")
                    .endsWith(" - Mágico Sabor");
            Row encabezados = sheet.getRow(3);
            assertThat(f.formatCellValue(encabezados.getCell(0))).isEqualTo("Producto/SKU");
            assertThat(f.formatCellValue(encabezados.getCell(2))).isEqualTo("Unidad de Medida");
            assertThat(f.formatCellValue(encabezados.getCell(3))).isEqualTo("Cantidad");
        }
    }

    @Test
    @DisplayName("la planilla para imprimir trae la identidad (nombre, logo y color primario)")
    void planillaImpresion_traeIdentidad() {
        distribuidora.setLogoUrl(LOGO_URL);
        PlanillaComercianteRepository planillaRepo = mock(PlanillaComercianteRepository.class);
        ItemPlanillaRepository itemRepo = mock(ItemPlanillaRepository.class);
        PlanillaService planillaService = new PlanillaService(
                null, planillaRepo, itemRepo, null, null, null, service);

        Usuario usuario = TestHelper.buildUsuario("uid-ventas", "ventas@gelox-test.com", RolUsuario.ENCARGADO_VENTAS, true);
        Comerciante comerciante = Comerciante.builder().id(UUID.randomUUID()).nombre("Comerciante Test").build();
        PlanillaComerciante planilla = PlanillaComerciante.builder()
                .id(UUID.randomUUID())
                .tenantId(TENANT)
                .comerciante(comerciante)
                .usuario(usuario)
                .fecha(LocalDate.now())
                .build();
        when(planillaRepo.findById(planilla.getId())).thenReturn(Optional.of(planilla));
        when(itemRepo.findByPlanillaIdWithProducto(planilla.getId())).thenReturn(List.of());

        PlanillaImpresionResponseDTO dto = planillaService.obtenerParaImpresion(planilla.getId());

        assertThat(dto.identidad().nombreComercial()).isEqualTo("Mágico Sabor");
        assertThat(dto.identidad().logoUrl()).isEqualTo(LOGO_URL);
        assertThat(dto.identidad().colorPrimario()).isEqualTo("#9E2016");
    }
}
