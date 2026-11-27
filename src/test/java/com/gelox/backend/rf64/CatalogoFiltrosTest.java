package com.gelox.backend.rf64;

import com.gelox.backend.catalogo.CatalogoRepository;
import com.gelox.backend.entities.Categoria;
import com.gelox.backend.entities.PlantillaExportacion;
import com.gelox.backend.entities.Producto;
import com.gelox.backend.entities.Proveedor;
import com.gelox.backend.repositories.ProveedorRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** RF63 / RF64 — consultas de filtros del catálogo y resumen por proveedor. */
@DataJpaTest(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"
})
class CatalogoFiltrosTest {

    private static final UUID TENANT = UUID.randomUUID();
    private static final UUID NINGUNO = new UUID(0, 0);

    @Autowired CatalogoRepository catalogoRepository;
    @Autowired ProveedorRepository proveedorRepository;
    @Autowired EntityManager em;

    Categoria conos;
    Categoria bebidas;
    Proveedor nutresa;
    Proveedor alpina;

    @BeforeEach
    void setUp() {
        em.createNativeQuery("SET REFERENTIAL_INTEGRITY FALSE").executeUpdate();
        conos = categoria("CONOS");
        bebidas = categoria("BEBIDAS");
        nutresa = proveedor("Nutresa");
        alpina = proveedor("Alpina");

        producto("Cono vainilla", "CN-1", "Crem Helado", conos, nutresa, 0, true);
        producto("Cono mora", "CN-2", "Crem Helado", conos, nutresa, 8, true);
        producto("Avena", "BB-1", "Alpina", bebidas, alpina, 15, true);
        producto("Jugo", "BB-2", null, bebidas, alpina, 90, true);
        producto("Cono retirado", "CN-9", null, conos, nutresa, 0, false);

        PlantillaExportacion plantilla = new PlantillaExportacion();
        plantilla.setTenantId(TENANT);
        plantilla.setProveedor(nutresa);
        plantilla.setNombre("Pedido Nutresa");
        em.persist(plantilla);
        em.flush();
    }

    private Categoria categoria(String nombre) {
        Categoria c = new Categoria();
        c.setTenantId(TENANT);
        c.setNombre(nombre);
        em.persist(c);
        return c;
    }

    private Proveedor proveedor(String nombre) {
        Proveedor p = new Proveedor();
        p.setTenantId(TENANT);
        p.setNombre(nombre);
        em.persist(p);
        return p;
    }

    /** Todos con stock mínimo 10 y medio 20. */
    private void producto(String nombre, String sku, String marca, Categoria categoria, Proveedor proveedor,
                          int stock, boolean activo) {
        Producto p = new Producto();
        p.setTenantId(TENANT);
        p.setNombre(nombre);
        p.setCodigoTecnico("T-" + sku);
        p.setSku(sku);
        p.setMarca(marca);
        p.setCategoria(categoria);
        p.setProveedor(proveedor);
        p.setPrecioVenta(BigDecimal.TEN);
        p.setPrecioCosto(BigDecimal.ONE);
        p.setStockActual(stock);
        p.setStockMinimo(10);
        p.setStockMedio(20);
        p.setActivo(activo);
        em.persist(p);
    }

    private List<String> nombres(Categoria categoria, Proveedor proveedor, String q, String stock) {
        return catalogoRepository.buscar(
                        categoria == null, categoria == null ? NINGUNO : categoria.getId(),
                        proveedor == null, proveedor == null ? NINGUNO : proveedor.getId(),
                        q, stock, PageRequest.of(0, 50))
                .map(Producto::getNombre).getContent();
    }

    @Test
    @DisplayName("sin filtros: todos los activos por nombre")
    void sinFiltros() {
        assertThat(nombres(null, null, "", "")).containsExactly("Avena", "Cono mora", "Cono vainilla", "Jugo");
    }

    @Test
    @DisplayName("por categoría y por proveedor")
    void porCategoriaYProveedor() {
        assertThat(nombres(conos, null, "", "")).containsExactly("Cono mora", "Cono vainilla");
        assertThat(nombres(null, alpina, "", "")).containsExactly("Avena", "Jugo");
        assertThat(nombres(conos, alpina, "", "")).isEmpty();
    }

    @Test
    @DisplayName("la búsqueda mira nombre, SKU, código técnico y marca, sin distinguir mayúsculas")
    void busqueda() {
        assertThat(nombres(null, null, "VAINI", "")).containsExactly("Cono vainilla");
        assertThat(nombres(null, null, "bb-2", "")).containsExactly("Jugo");
        assertThat(nombres(null, null, "t-cn-2", "")).containsExactly("Cono mora");
        assertThat(nombres(null, null, "crem", "")).containsExactly("Cono mora", "Cono vainilla");
    }

    @Test
    @DisplayName("stock BAJO, MEDIO y SIN")
    void stock() {
        assertThat(nombres(null, null, "", "SIN")).containsExactly("Cono vainilla");
        assertThat(nombres(null, null, "", "BAJO")).containsExactly("Cono mora", "Cono vainilla");
        assertThat(nombres(null, null, "", "MEDIO")).containsExactly("Avena", "Cono mora", "Cono vainilla");
    }

    @Test
    @DisplayName("la paginación cuenta solo lo filtrado")
    void paginacion() {
        Page<Producto> pagina = catalogoRepository.buscar(
                false, conos.getId(), true, NINGUNO, "", "", PageRequest.of(1, 1));

        assertThat(pagina.getTotalElements()).isEqualTo(2);
        assertThat(pagina.getContent()).extracting(Producto::getNombre).containsExactly("Cono vainilla");
    }

    @Test
    @DisplayName("resumen por proveedor: solo productos activos y el nombre de su plantilla")
    void resumenPorProveedor() {
        List<UUID> ids = List.of(nutresa.getId(), alpina.getId());

        assertThat(proveedorRepository.contarProductosActivos(ids))
                .extracting(f -> f[0], f -> f[1])
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple(nutresa.getId(), 2L),
                        org.assertj.core.groups.Tuple.tuple(alpina.getId(), 2L));
        assertThat(proveedorRepository.nombresDePlantillas(ids))
                .extracting(f -> f[0], f -> f[1])
                .containsExactly(org.assertj.core.groups.Tuple.tuple(nutresa.getId(), "Pedido Nutresa"));
    }
}
