package com.gelox.backend.rf71;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gelox.backend.TestHelper;
import com.gelox.backend.catalogo.CatalogoRepository;
import com.gelox.backend.catalogo.CatalogoService;
import com.gelox.backend.catalogo.dto.CrearProductoRequest;
import com.gelox.backend.dto.CrearComercianteRequest;
import com.gelox.backend.dto.CrearUsuarioDTO;
import com.gelox.backend.entities.Comerciante;
import com.gelox.backend.entities.MetodoClasificacion;
import com.gelox.backend.entities.RolUsuario;
import com.gelox.backend.entities.TipoIntencionVoz;
import com.gelox.backend.entities.Usuario;
import com.gelox.backend.exceptions.GlobalExceptionHandler;
import com.gelox.backend.gobierno.ArchivoAlmacenadoRepository;
import com.gelox.backend.gobierno.CuotaAlcanzadaException;
import com.gelox.backend.gobierno.CuotaService;
import com.gelox.backend.gobierno.TipoCuota;
import com.gelox.backend.entities.Categoria;
import com.gelox.backend.repositories.CategoriaRepository;
import com.gelox.backend.repositories.ComercianteRepository;
import com.gelox.backend.repositories.FactorConversionRepository;
import com.gelox.backend.repositories.ProveedorRepository;
import com.gelox.backend.repositories.UnidadMedidaRepository;
import com.gelox.backend.repositories.ItemPlanillaRepository;
import com.gelox.backend.repositories.PlanillaComercianteRepository;
import com.gelox.backend.repositories.UsuarioRepository;
import com.gelox.backend.services.ComercianteService;
import com.gelox.backend.services.EmailService;
import com.gelox.backend.services.EventoSistemaService;
import com.gelox.backend.services.SupabaseStorageService;
import com.gelox.backend.services.UsuarioService;
import com.gelox.backend.tenant.TenantContext;
import com.gelox.backend.voz.ClasificadorIntencion;
import com.gelox.backend.voz.ClasificadorIntencion.ResultadoClasificacion;
import com.gelox.backend.voz.llm.LlmCliente;
import com.gelox.backend.voz.llm.ProcesadorLenguajeLlm;
import com.google.firebase.auth.FirebaseAuth;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * RF70 — Con la cuota del plan alcanzada cada operación responde 409 y no guarda nada
 * (CP-70-04: tampoco se crea la cuenta en Firebase); la IA cae al clasificador de respaldo.
 */
@ExtendWith(MockitoExtension.class)
class CuotaEnServiciosTest {

    private static final UUID TENANT = UUID.randomUUID();

    @Mock UsuarioRepository usuarioRepository;
    @Mock EventoSistemaService eventoSistemaService;
    @Mock EmailService emailService;
    @Mock CatalogoRepository catalogoRepository;
    @Mock ComercianteRepository comercianteRepository;
    @Mock PlanillaComercianteRepository planillaRepository;
    @Mock ItemPlanillaRepository itemPlanillaRepository;
    @Mock ArchivoAlmacenadoRepository archivoRepository;
    @Mock CuotaService cuotaService;
    @Mock CategoriaRepository categoriaRepository;
    @Mock ProveedorRepository proveedorRepository;
    @Mock UnidadMedidaRepository unidadMedidaRepository;
    @Mock FactorConversionRepository factorConversionRepository;

    private Usuario gerente;

    @BeforeEach
    void setUp() {
        TenantContext.set(TENANT);
        gerente = TestHelper.buildUsuario("uid-g", "g@b.com", RolUsuario.ADMINISTRADOR, true);
        gerente.setTenantId(TENANT);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private void cuotaAlcanzada(TipoCuota tipo, long limite) {
        doThrow(new CuotaAlcanzadaException(tipo, limite, limite)).when(cuotaService).validar(tipo);
    }

    @Test
    @DisplayName("CP-70-04: usuario n.º 4 con plan de máx. 3 → 409 y no se crea en Firebase ni en la base")
    void crearUsuarioBloqueado() {
        cuotaAlcanzada(TipoCuota.USUARIOS, 3);
        UsuarioService usuarioService = new UsuarioService(usuarioRepository, eventoSistemaService, emailService, cuotaService);

        CrearUsuarioDTO dto = new CrearUsuarioDTO();
        dto.setNombre("Vendedor 4");
        dto.setCorreo("vendedor4@b.com");
        dto.setContrasenaTemporal("Temporal123");
        dto.setRol("ENCARGADO_VENTAS");

        try (MockedStatic<FirebaseAuth> firebase = mockStatic(FirebaseAuth.class)) {
            assertThatThrownBy(() -> usuarioService.crearUsuario(dto)).isInstanceOf(CuotaAlcanzadaException.class);
            firebase.verifyNoInteractions();
        }
        verify(usuarioRepository, never()).save(any());
        verifyNoInteractions(eventoSistemaService, emailService);
    }

    @Test
    @DisplayName("CP-64-09: producto n.º 6 con plan de máx. 5 → 409 y no se guarda")
    void crearProductoBloqueado() {
        cuotaAlcanzada(TipoCuota.PRODUCTOS, 5);
        CatalogoService catalogoService = new CatalogoService(catalogoRepository, cuotaService,
                proveedorRepository, categoriaRepository, unidadMedidaRepository, factorConversionRepository);
        Categoria paletas = new Categoria();
        paletas.setNombre("PALETAS");
        when(categoriaRepository.findByNombreIgnoreCase("PALETAS")).thenReturn(Optional.of(paletas));

        CrearProductoRequest req = new CrearProductoRequest();
        req.setCodigoTecnico("P-006");
        req.setNombre("Paleta 6");
        req.setCategoria("PALETAS");
        req.setPrecioVenta(new BigDecimal("2000"));
        req.setPrecioCosto(new BigDecimal("1200"));

        assertThatThrownBy(() -> catalogoService.crearProducto(req, gerente)).isInstanceOf(CuotaAlcanzadaException.class);
        verify(catalogoRepository, never()).save(any());
    }

    private ComercianteService comercianteService() {
        return new ComercianteService(comercianteRepository, eventoSistemaService, planillaRepository,
                itemPlanillaRepository, cuotaService);
    }

    @Test
    @DisplayName("Comerciante nuevo con la cuota alcanzada → 409 y no se guarda")
    void crearComercianteBloqueado() {
        cuotaAlcanzada(TipoCuota.COMERCIANTES, 4);
        CrearComercianteRequest req = new CrearComercianteRequest("Don Pedro", "Cúcuta", null, null, null, null,
                null, null, null, null, null, null);

        assertThatThrownBy(() -> comercianteService().crear(req, gerente)).isInstanceOf(CuotaAlcanzadaException.class);
        verify(comercianteRepository, never()).save(any());
        verifyNoInteractions(eventoSistemaService);
    }

    @Test
    @DisplayName("Activar un comerciante inactivo con la cuota alcanzada → 409 y sigue inactivo")
    void activarComercianteBloqueado() {
        cuotaAlcanzada(TipoCuota.COMERCIANTES, 4);
        UUID id = UUID.randomUUID();
        Comerciante inactivo = Comerciante.builder().id(id).nombre("Don Pedro").activo(false).build();
        inactivo.setTenantId(TENANT);
        when(comercianteRepository.findById(id)).thenReturn(Optional.of(inactivo));

        assertThatThrownBy(() -> comercianteService().cambiarEstado(id, true, gerente))
                .isInstanceOf(CuotaAlcanzadaException.class);
        assertThat(inactivo.getActivo()).isFalse();
        verify(comercianteRepository, never()).save(any());
    }

    @Test
    @DisplayName("Desactivar un comerciante no consulta la cuota")
    void desactivarComercianteNoValidaCuota() {
        UUID id = UUID.randomUUID();
        Comerciante activo = Comerciante.builder().id(id).nombre("Don Pedro").activo(true).build();
        activo.setTenantId(TENANT);
        when(comercianteRepository.findById(id)).thenReturn(Optional.of(activo));
        when(comercianteRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        comercianteService().cambiarEstado(id, false, gerente);

        verifyNoInteractions(cuotaService);
    }

    @Test
    @DisplayName("CP-70-06: subir un archivo que supera max_almacenamiento_mb → 409, sin subir ni registrar")
    void subirImagenBloqueada() {
        MockMultipartFile foto = new MockMultipartFile("foto", "foto.png", "image/png", new byte[2048]);
        doThrow(new CuotaAlcanzadaException(TipoCuota.ALMACENAMIENTO_MB, 10, new BigDecimal("10.00")))
                .when(cuotaService).validar(TipoCuota.ALMACENAMIENTO_MB, 2048L);
        // Host inexistente: si llegara a subir, la respuesta sería un 500 y no el 409
        SupabaseStorageService storage = new SupabaseStorageService("http://localhost:1", "clave", "fotos",
                cuotaService, archivoRepository);

        assertThatThrownBy(() -> storage.subirImagen(foto, "productos")).isInstanceOf(CuotaAlcanzadaException.class);
        verifyNoInteractions(archivoRepository);
    }

    @Test
    @DisplayName("Sin distribuidora (Superadministrador) la subida no consulta la cuota")
    void subirImagenSinTenantNoValidaCuota() {
        TenantContext.clear();
        MockMultipartFile foto = new MockMultipartFile("foto", "foto.png", "image/png", new byte[16]);
        SupabaseStorageService storage = new SupabaseStorageService("http://localhost:1", "clave", "fotos",
                cuotaService, archivoRepository);

        // Falla la conexión al host inexistente, pero antes no se consultó la cuota
        assertThatThrownBy(() -> storage.subirImagen(foto, "productos"));
        verify(cuotaService, never()).validar(any(), anyLong());
        verifyNoInteractions(archivoRepository);
    }

    @Test
    @DisplayName("IA: con LLAMADAS_IA_MES agotada usa el clasificador OpenNLP de respaldo, sin 409")
    void iaUsaRespaldoConCuotaAgotada() {
        LlmCliente cliente = org.mockito.Mockito.mock(LlmCliente.class);
        ClasificadorIntencion respaldo = org.mockito.Mockito.mock(ClasificadorIntencion.class);
        ResultadoClasificacion esperado = new ResultadoClasificacion(
                TipoIntencionVoz.REGISTRAR_VENTA, Map.of(), 0.9, MetodoClasificacion.MODELO);
        when(cliente.configurado()).thenReturn(true);
        when(cuotaService.disponible(TipoCuota.LLAMADAS_IA_MES)).thenReturn(false);
        when(respaldo.clasificar(anyString())).thenReturn(esperado);

        ResultadoClasificacion r = new ProcesadorLenguajeLlm(cliente, respaldo, cuotaService)
                .procesar("vendí tres paletas");

        assertThat(r).isSameAs(esperado);
        verify(cliente, never()).clasificar(anyString());
    }

    @Test
    @DisplayName("IA: con cupo disponible sí llama al LLM")
    void iaConCupoLlamaAlLlm() throws Exception {
        LlmCliente cliente = org.mockito.Mockito.mock(LlmCliente.class);
        ClasificadorIntencion respaldo = org.mockito.Mockito.mock(ClasificadorIntencion.class);
        when(cliente.configurado()).thenReturn(true);
        when(cuotaService.disponible(TipoCuota.LLAMADAS_IA_MES)).thenReturn(true);
        when(cliente.clasificar(eq("vendí tres paletas"))).thenReturn(new ObjectMapper().readTree(
                "{\"intencion\":\"REGISTRAR_VENTA\",\"confianza\":0.95,\"slots\":{}}"));

        ResultadoClasificacion r = new ProcesadorLenguajeLlm(cliente, respaldo, cuotaService)
                .procesar("vendí tres paletas");

        assertThat(r.metodo()).isEqualTo(MetodoClasificacion.LLM);
        verifyNoInteractions(respaldo);
    }

    @Test
    @DisplayName("El 409 sale con {error, codigo: CUOTA_PLAN_ALCANZADA, cuota, limite, usado}")
    void cuerpoDel409() {
        ResponseEntity<Map<String, Object>> respuesta = new GlobalExceptionHandler()
                .handleCuotaAlcanzada(new CuotaAlcanzadaException(TipoCuota.USUARIOS, 3, 3L));

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(respuesta.getBody()).containsEntry("error", "Cuota del plan alcanzada")
                .containsEntry("codigo", "CUOTA_PLAN_ALCANZADA")
                .containsEntry("cuota", "USUARIOS")
                .containsEntry("limite", 3L)
                .containsEntry("usado", 3L);
    }
}
