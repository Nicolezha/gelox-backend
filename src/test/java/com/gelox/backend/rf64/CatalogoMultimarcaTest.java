package com.gelox.backend.rf64;

import com.gelox.backend.TestHelper;
import com.gelox.backend.catalogo.CatalogoRepository;
import com.gelox.backend.catalogo.CatalogoService;
import com.gelox.backend.catalogo.dto.CatalogoProductoDTO;
import com.gelox.backend.catalogo.dto.CrearProductoRequest;
import com.gelox.backend.catalogo.dto.EditarProductoRequest;
import com.gelox.backend.catalogo.dto.FactorConversionRequest;
import com.gelox.backend.entities.Categoria;
import com.gelox.backend.entities.FactorConversion;
import com.gelox.backend.entities.Producto;
import com.gelox.backend.entities.Proveedor;
import com.gelox.backend.entities.RolUsuario;
import com.gelox.backend.entities.UnidadMedida;
import com.gelox.backend.entities.Usuario;
import com.gelox.backend.gobierno.CuotaService;
import com.gelox.backend.repositories.CategoriaRepository;
import com.gelox.backend.repositories.FactorConversionRepository;
import com.gelox.backend.repositories.ProveedorRepository;
import com.gelox.backend.repositories.UnidadMedidaRepository;
import com.gelox.backend.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * RF64 — el catálogo con sku, marca, proveedor, categoría de tabla y factores de conversión.
 */
@ExtendWith(MockitoExtension.class)
class CatalogoMultimarcaTest {

    private static final UUID TENANT = UUID.randomUUID();
    private static final UUID OTRO_TENANT = UUID.randomUUID();

    @Mock CatalogoRepository catalogoRepository;
    @Mock CuotaService cuotaService;
    @Mock ProveedorRepository proveedorRepository;
    @Mock CategoriaRepository categoriaRepository;
    @Mock UnidadMedidaRepository unidadMedidaRepository;
    @Mock FactorConversionRepository factorRepository;

    CatalogoService service;
    Usuario gerente;
    Categoria conos;
    UnidadMedida un;
    UnidadMedida bol;
    UnidadMedida cj;

    @BeforeEach
    void setUp() {
        TenantContext.set(TENANT);
        service = new CatalogoService(catalogoRepository, cuotaService, proveedorRepository,
                categoriaRepository, unidadMedidaRepository, factorRepository);
        gerente = TestHelper.buildUsuario("uid-g", "g@b.com", RolUsuario.ADMINISTRADOR, true);

        conos = categoria("CONOS", TENANT, true);
        un = unidad("UN", TENANT);
        bol = unidad("BOL", TENANT);
        cj = unidad("CJ", TENANT);

        lenient().when(catalogoRepository.save(any(Producto.class))).thenAnswer(inv -> {
            Producto p = inv.getArgument(0);
            if (p.getId() == null) p.setId(UUID.randomUUID());
            return p;
        });
        lenient().when(categoriaRepository.findByNombreIgnoreCase("CONOS")).thenReturn(Optional.of(conos));
        lenient().when(unidadMedidaRepository.findByCodigoIgnoreCase("UN")).thenReturn(Optional.of(un));
        lenient().when(factorRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private static Categoria categoria(String nombre, UUID tenant, boolean activa) {
        Categoria c = new Categoria();
        c.setId(UUID.randomUUID());
        c.setTenantId(tenant);
        c.setNombre(nombre);
        c.setActivo(activa);
        return c;
    }

    private static UnidadMedida unidad(String codigo, UUID tenant) {
        UnidadMedida u = new UnidadMedida();
        u.setId(UUID.randomUUID());
        u.setTenantId(tenant);
        u.setCodigo(codigo);
        u.setNombre(codigo);
        return u;
    }

    private static Proveedor proveedor(UUID tenant, boolean activo) {
        Proveedor p = new Proveedor();
        p.setId(UUID.randomUUID());
        p.setTenantId(tenant);
        p.setNombre("Nutresa");
        p.setActivo(activo);
        return p;
    }

    private static CrearProductoRequest crear() {
        CrearProductoRequest r = new CrearProductoRequest();
        r.setCodigoTecnico("CN-001");
        r.setNombre("Cono vainilla");
        r.setCategoria("CONOS");
        r.setPrecioVenta(new BigDecimal("2500"));
        r.setPrecioCosto(new BigDecimal("1500"));
        return r;
    }

    private static void assertEstado(Throwable e, HttpStatus esperado) {
        assertThat(e).isInstanceOfSatisfying(ResponseStatusException.class,
                ex -> assertThat(ex.getStatusCode().value()).isEqualTo(esperado.value()));
    }

    // ── sku ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("sku repetido al crear → 409 y no se guarda")
    void skuRepetidoAlCrear() {
        CrearProductoRequest req = crear();
        req.setSku("SKU-9");
        when(catalogoRepository.existsBySku("SKU-9")).thenReturn(true);

        assertThatThrownBy(() -> service.crearProducto(req, gerente)).satisfies(e -> assertEstado(e, HttpStatus.CONFLICT));
        verify(catalogoRepository, never()).save(any());
    }

    @Test
    @DisplayName("sin sku se usa el código técnico (como en V10)")
    void skuPorDefecto() {
        CatalogoProductoDTO dto = service.crearProducto(crear(), gerente);

        assertThat(dto.sku()).isEqualTo("CN-001");
        assertThat(dto.categoria()).isEqualTo("CONOS");
        assertThat(dto.categoriaId()).isEqualTo(conos.getId());
        assertThat(dto.unidadBaseCodigo()).isEqualTo("UN");
    }

    @Test
    @DisplayName("sku repetido en otro producto al editar → 409")
    void skuRepetidoAlEditar() {
        UUID id = UUID.randomUUID();
        Producto existente = new Producto();
        existente.setId(id);
        existente.setNombre("Cono");
        when(catalogoRepository.findByIdAndActivoTrue(id)).thenReturn(Optional.of(existente));
        when(catalogoRepository.existsBySkuAndIdNot("SKU-9", id)).thenReturn(true);

        EditarProductoRequest req = new EditarProductoRequest();
        req.setSku("SKU-9");

        assertThatThrownBy(() -> service.editarProducto(id, req, gerente)).satisfies(e -> assertEstado(e, HttpStatus.CONFLICT));
    }

    // ── marca, proveedor y categoría ─────────────────────────────────────

    @Test
    @DisplayName("crea con marca y proveedor activo de la distribuidora")
    void conMarcaYProveedor() {
        Proveedor nutresa = proveedor(TENANT, true);
        when(proveedorRepository.findById(nutresa.getId())).thenReturn(Optional.of(nutresa));
        CrearProductoRequest req = crear();
        req.setMarca("  Mágico Sabor ");
        req.setProveedorId(nutresa.getId());

        CatalogoProductoDTO dto = service.crearProducto(req, gerente);

        assertThat(dto.marca()).isEqualTo("Mágico Sabor");
        assertThat(dto.proveedorId()).isEqualTo(nutresa.getId());
        assertThat(dto.proveedorNombre()).isEqualTo("Nutresa");
    }

    @Test
    @DisplayName("proveedor de otra distribuidora → 404 y no se guarda")
    void proveedorAjeno() {
        Proveedor ajeno = proveedor(OTRO_TENANT, true);
        when(proveedorRepository.findById(ajeno.getId())).thenReturn(Optional.of(ajeno));
        CrearProductoRequest req = crear();
        req.setProveedorId(ajeno.getId());

        assertThatThrownBy(() -> service.crearProducto(req, gerente)).satisfies(e -> assertEstado(e, HttpStatus.NOT_FOUND));
        verify(catalogoRepository, never()).save(any());
    }

    @Test
    @DisplayName("proveedor inactivo → 400 y no se guarda")
    void proveedorInactivo() {
        Proveedor inactivo = proveedor(TENANT, false);
        when(proveedorRepository.findById(inactivo.getId())).thenReturn(Optional.of(inactivo));
        CrearProductoRequest req = crear();
        req.setProveedorId(inactivo.getId());

        assertThatThrownBy(() -> service.crearProducto(req, gerente)).satisfies(e -> assertEstado(e, HttpStatus.BAD_REQUEST));
        verify(catalogoRepository, never()).save(any());
    }

    @Test
    @DisplayName("categoriaId de otra distribuidora → 404")
    void categoriaAjena() {
        Categoria ajena = categoria("CONOS", OTRO_TENANT, true);
        when(categoriaRepository.findById(ajena.getId())).thenReturn(Optional.of(ajena));
        CrearProductoRequest req = crear();
        req.setCategoria(null);
        req.setCategoriaId(ajena.getId());

        assertThatThrownBy(() -> service.crearProducto(req, gerente)).satisfies(e -> assertEstado(e, HttpStatus.NOT_FOUND));
    }

    @Test
    @DisplayName("sin categoría ni categoriaId → 400; nombre que no existe → 400")
    void categoriaObligatoria() {
        CrearProductoRequest sin = crear();
        sin.setCategoria(null);
        assertThatThrownBy(() -> service.crearProducto(sin, gerente)).satisfies(e -> assertEstado(e, HttpStatus.BAD_REQUEST));

        CrearProductoRequest rara = crear();
        rara.setCategoria("HELADOS");
        when(categoriaRepository.findByNombreIgnoreCase("HELADOS")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.crearProducto(rara, gerente)).satisfies(e -> assertEstado(e, HttpStatus.BAD_REQUEST));
    }

    @Test
    @DisplayName("la categoría nueva (que no era del enum) también sirve")
    void categoriaNueva() {
        Categoria bebidas = categoria("BEBIDAS", TENANT, true);
        when(categoriaRepository.findById(bebidas.getId())).thenReturn(Optional.of(bebidas));
        CrearProductoRequest req = crear();
        req.setCategoria(null);
        req.setCategoriaId(bebidas.getId());

        assertThat(service.crearProducto(req, gerente).categoria()).isEqualTo("BEBIDAS");
    }

    // ── factores ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("guarda los factores hacia la unidad base (1 BOL = 6 UN)")
    void guardaFactores() {
        when(unidadMedidaRepository.findById(bol.getId())).thenReturn(Optional.of(bol));
        CrearProductoRequest req = crear();
        req.setFactores(List.of(new FactorConversionRequest(bol.getId(), new BigDecimal("6"))));

        CatalogoProductoDTO dto = service.crearProducto(req, gerente);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<FactorConversion>> guardados = ArgumentCaptor.forClass(List.class);
        verify(factorRepository).saveAll(guardados.capture());
        FactorConversion f = guardados.getValue().get(0);
        assertThat(f.getUnidadOrigen()).isSameAs(bol);
        assertThat(f.getUnidadDestino()).isSameAs(un);
        assertThat(f.getFactor()).isEqualByComparingTo("6");
        assertThat(dto.factores()).hasSize(1);
        assertThat(dto.factores().get(0).unidadOrigenCodigo()).isEqualTo("BOL");
    }

    @Test
    @DisplayName("un factor para CJ → 400: CJ sale de unidadesPorCaja")
    void cajaNoLlevaFactor() {
        when(unidadMedidaRepository.findById(cj.getId())).thenReturn(Optional.of(cj));
        CrearProductoRequest req = crear();
        req.setFactores(List.of(new FactorConversionRequest(cj.getId(), new BigDecimal("24"))));

        assertThatThrownBy(() -> service.crearProducto(req, gerente)).satisfies(e -> assertEstado(e, HttpStatus.BAD_REQUEST));
        verify(factorRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("un factor con la unidad de otra distribuidora → 404")
    void factorUnidadAjena() {
        UnidadMedida ajena = unidad("BOL", OTRO_TENANT);
        when(unidadMedidaRepository.findById(ajena.getId())).thenReturn(Optional.of(ajena));
        CrearProductoRequest req = crear();
        req.setFactores(List.of(new FactorConversionRequest(ajena.getId(), new BigDecimal("6"))));

        assertThatThrownBy(() -> service.crearProducto(req, gerente)).satisfies(e -> assertEstado(e, HttpStatus.NOT_FOUND));
    }

    @Test
    @DisplayName("editar con factores=null no toca los factores; con lista, los reemplaza")
    void editarFactores() {
        UUID id = UUID.randomUUID();
        Producto existente = new Producto();
        existente.setId(id);
        existente.setNombre("Cono");
        existente.setUnidadBase(un);
        when(catalogoRepository.findByIdAndActivoTrue(id)).thenReturn(Optional.of(existente));
        when(unidadMedidaRepository.findById(bol.getId())).thenReturn(Optional.of(bol));

        service.editarProducto(id, new EditarProductoRequest(), gerente);
        verify(factorRepository, never()).borrarPorProducto(any());

        EditarProductoRequest req = new EditarProductoRequest();
        req.setFactores(List.of(new FactorConversionRequest(bol.getId(), new BigDecimal("12"))));
        CatalogoProductoDTO dto = service.editarProducto(id, req, gerente);

        verify(factorRepository).borrarPorProducto(id);
        assertThat(dto.factores()).extracting("factor").containsExactly(new BigDecimal("12"));
    }
}
