package com.gelox.backend.rf67;

import com.gelox.backend.entities.CampoPlantilla;
import com.gelox.backend.entities.CodigoHomologacion;
import com.gelox.backend.entities.ModoPlantilla;
import com.gelox.backend.entities.PlantillaExportacion;
import com.gelox.backend.entities.Producto;
import com.gelox.backend.entities.Proveedor;
import com.gelox.backend.exceptions.CampoInvalidoException;
import com.gelox.backend.exportacion.ExportadorPedido;
import com.gelox.backend.exportacion.PlantillaExportacionService;
import com.gelox.backend.exportacion.dto.ColumnaDTO;
import com.gelox.backend.exportacion.dto.HomologacionRequest;
import com.gelox.backend.exportacion.dto.PlantillaDTO;
import com.gelox.backend.exportacion.dto.PlantillaRequest;
import com.gelox.backend.exportacion.dto.VistaPreviaDTO;
import com.gelox.backend.repositories.CodigoHomologacionRepository;
import com.gelox.backend.repositories.PlantillaExportacionRepository;
import com.gelox.backend.repositories.ProductoRepository;
import com.gelox.backend.repositories.ProveedorRepository;
import com.gelox.backend.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
 * RF67 — plantilla de exportación y códigos de homologación por proveedor (FP-15 §2.18.1):
 * proveedor de otra distribuidora → 404; columnas vacías o campo desconocido o sin valor → 400;
 * código repetido en el proveedor → 409; sin plantilla se devuelve la estándar de Nutresa.
 */
@ExtendWith(MockitoExtension.class)
class PlantillaExportacionServiceTest {

    private static final UUID TENANT = UUID.fromString("00000000-0000-0000-0000-0000000000bb");

    @Mock private ProveedorRepository proveedorRepository;
    @Mock private PlantillaExportacionRepository plantillaRepository;
    @Mock private CodigoHomologacionRepository homologacionRepository;
    @Mock private ProductoRepository productoRepository;
    @Mock private ExportadorPedido exportadorPedido;

    private PlantillaExportacionService service;
    private Proveedor proveedor;
    private Producto cono;
    private Producto paleta;

    @BeforeEach
    void preparar() {
        TenantContext.set(TENANT);
        service = new PlantillaExportacionService(proveedorRepository, plantillaRepository,
                homologacionRepository, productoRepository, exportadorPedido);

        proveedor = proveedor(TENANT);
        cono = producto("Cono Vainilla", "SKU-1");
        paleta = producto("Paleta Fresa", "SKU-2");
        lenient().when(proveedorRepository.findById(proveedor.getId())).thenReturn(Optional.of(proveedor));
        lenient().when(plantillaRepository.findByProveedorId(proveedor.getId())).thenReturn(Optional.empty());
        lenient().when(plantillaRepository.save(any(PlantillaExportacion.class))).thenAnswer(i -> i.getArgument(0));
        lenient().when(plantillaRepository.saveAndFlush(any(PlantillaExportacion.class))).thenAnswer(i -> i.getArgument(0));
        lenient().when(homologacionRepository.listarPorProveedor(proveedor.getId())).thenReturn(List.of());
        lenient().when(productoRepository.findByProveedorIdAndActivoTrueOrderByNombreAsc(proveedor.getId()))
                .thenReturn(List.of(cono, paleta));
    }

    @AfterEach
    void limpiar() {
        TenantContext.clear();
    }

    private static Proveedor proveedor(UUID tenant) {
        Proveedor p = new Proveedor();
        p.setId(UUID.randomUUID());
        p.setTenantId(tenant);
        p.setNombre("Crem Helado");
        return p;
    }

    private static Producto producto(String nombre, String sku) {
        Producto p = new Producto();
        p.setId(UUID.randomUUID());
        p.setNombre(nombre);
        p.setSku(sku);
        p.setPrecioCosto(BigDecimal.valueOf(1000));
        return p;
    }

    private static PlantillaRequest filas(List<ColumnaDTO> columnas, List<HomologacionRequest> codigos) {
        return new PlantillaRequest(ModoPlantilla.GENERAR_FILAS, null, null, null, null, null, columnas, codigos);
    }

    private static ColumnaDTO col(CampoPlantilla campo, String encabezado, String valorFijo) {
        return new ColumnaDTO(null, campo, encabezado, valorFijo);
    }

    private static void assertEstado(Runnable accion, HttpStatus esperado) {
        assertThatThrownBy(accion::run).isInstanceOfSatisfying(ResponseStatusException.class,
                e -> assertThat(e.getStatusCode().value()).isEqualTo(esperado.value()));
    }

    @Test
    @DisplayName("CP-67-07: un proveedor de otra distribuidora responde 404")
    void proveedorDeOtraDistribuidoraEs404() {
        Proveedor ajeno = proveedor(UUID.randomUUID());
        when(proveedorRepository.findById(ajeno.getId())).thenReturn(Optional.of(ajeno));

        assertEstado(() -> service.obtener(ajeno.getId()), HttpStatus.NOT_FOUND);
        assertEstado(() -> service.guardar(ajeno.getId(),
                filas(List.of(col(CampoPlantilla.SKU, "SKU", null)), null)), HttpStatus.NOT_FOUND);
        verify(plantillaRepository, never()).save(any());
    }

    @Test
    @DisplayName("CP-67-08: sin plantilla propia se devuelve la estándar de Nutresa y los productos sin código")
    void sinPlantillaDevuelveLaEstandar() {
        PlantillaDTO dto = service.obtener(proveedor.getId());

        assertThat(dto.personalizada()).isFalse();
        assertThat(dto.modo()).isEqualTo(ModoPlantilla.RELLENAR_CATALOGO);
        assertThat(dto.filaEncabezado()).isEqualTo(4);
        assertThat(dto.encabezadoCodigo()).isEqualTo("Producto/SKU");
        assertThat(dto.homologaciones()).extracting("codigo").containsExactly("", "");
    }

    @Test
    @DisplayName("CP-67-09: GENERAR_FILAS sin columnas responde 400")
    void sinColumnasEs400() {
        assertEstado(() -> service.guardar(proveedor.getId(), filas(List.of(), null)), HttpStatus.BAD_REQUEST);
        assertEstado(() -> service.guardar(proveedor.getId(), filas(null, null)), HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("CP-67-10: columna sin campo, sin encabezado o de texto fijo sin valor responde 400")
    void columnaInvalidaEs400() {
        assertEstado(() -> service.guardar(proveedor.getId(), filas(List.of(col(null, "X", null)), null)),
                HttpStatus.BAD_REQUEST);
        assertEstado(() -> service.guardar(proveedor.getId(),
                filas(List.of(col(CampoPlantilla.SKU, " ", null)), null)), HttpStatus.BAD_REQUEST);
        assertEstado(() -> service.guardar(proveedor.getId(),
                filas(List.of(col(CampoPlantilla.TEXTO_FIJO, "Cliente", " ")), null)), HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("CP-67-11: guarda la plantilla con las columnas numeradas en el orden recibido")
    void guardaLaPlantilla() {
        PlantillaDTO dto = service.guardar(proveedor.getId(), filas(List.of(
                col(CampoPlantilla.CODIGO_PROVEEDOR, "Código", null),
                col(CampoPlantilla.TEXTO_FIJO, "Cliente", "CLI-778")), null));

        assertThat(dto.personalizada()).isTrue();
        assertThat(dto.modo()).isEqualTo(ModoPlantilla.GENERAR_FILAS);
        assertThat(dto.columnas()).extracting("orden").containsExactly(1, 2);
        assertThat(dto.columnas()).extracting("campo")
                .containsExactly(CampoPlantilla.CODIGO_PROVEEDOR, CampoPlantilla.TEXTO_FIJO);
        assertThat(dto.columnas().get(1).valorFijo()).isEqualTo("CLI-778");
    }

    @Test
    @DisplayName("CP-48: dos columnas con el mismo orden responden 409 indicando el campo orden y no guardan")
    void ordenRepetidoEs409() {
        PlantillaRequest req = filas(List.of(
                new ColumnaDTO(1, CampoPlantilla.CODIGO_PROVEEDOR, "Código", null),
                new ColumnaDTO(2, CampoPlantilla.NOMBRE_PRODUCTO, "Producto", null),
                new ColumnaDTO(2, CampoPlantilla.CANTIDAD_CAJAS, "Cajas", null)), null);

        assertThatThrownBy(() -> service.guardar(proveedor.getId(), req))
                .isInstanceOfSatisfying(CampoInvalidoException.class, e -> {
                    assertThat(e.getStatusCode().value()).isEqualTo(HttpStatus.CONFLICT.value());
                    assertThat(e.getCampo()).isEqualTo("orden");
                    assertThat(e.getReason()).contains("2");
                });
        assertEstado(() -> service.vistaPrevia(proveedor.getId(), req), HttpStatus.CONFLICT);
        verify(plantillaRepository, never()).save(any());
    }

    @Test
    @DisplayName("CP-48: un orden menor que 1 responde 400")
    void ordenMenorQueUnoEs400() {
        assertEstado(() -> service.guardar(proveedor.getId(), filas(List.of(
                new ColumnaDTO(0, CampoPlantilla.CODIGO_PROVEEDOR, "Código", null)), null)), HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("CP-48: con orden en todas las columnas se guardan en ese orden, numeradas 1, 2, 3")
    void respetaElOrdenRecibido() {
        PlantillaDTO dto = service.guardar(proveedor.getId(), filas(List.of(
                new ColumnaDTO(3, CampoPlantilla.CANTIDAD_CAJAS, "Cajas", null),
                new ColumnaDTO(1, CampoPlantilla.CODIGO_PROVEEDOR, "Código", null),
                new ColumnaDTO(2, CampoPlantilla.NOMBRE_PRODUCTO, "Producto", null)), null));

        assertThat(dto.columnas()).extracting("orden").containsExactly(1, 2, 3);
        assertThat(dto.columnas()).extracting("campo").containsExactly(
                CampoPlantilla.CODIGO_PROVEEDOR, CampoPlantilla.NOMBRE_PRODUCTO, CampoPlantilla.CANTIDAD_CAJAS);
    }

    @Test
    @DisplayName("CP-67-12: RELLENAR_CATALOGO pide la fila y los encabezados de código y cantidad")
    void rellenarCatalogoValidaSusCampos() {
        assertEstado(() -> service.guardar(proveedor.getId(),
                new PlantillaRequest(ModoPlantilla.RELLENAR_CATALOGO, null, 0, "Producto/SKU", "Cantidad", null, null, null)),
                HttpStatus.BAD_REQUEST);
        assertEstado(() -> service.guardar(proveedor.getId(),
                new PlantillaRequest(ModoPlantilla.RELLENAR_CATALOGO, null, 4, "Producto/SKU", " ", null, null, null)),
                HttpStatus.BAD_REQUEST);

        PlantillaDTO dto = service.guardar(proveedor.getId(),
                new PlantillaRequest(ModoPlantilla.RELLENAR_CATALOGO, null, 4, "Producto/SKU", "Cantidad", "Unidad de Medida", null, null));
        assertThat(dto.filaEncabezado()).isEqualTo(4);
        assertThat(dto.columnas()).isEmpty();
    }

    @Test
    @DisplayName("CP-67-13: dos productos con el mismo código en el proveedor responde 409")
    void codigoRepetidoEs409() {
        List<HomologacionRequest> repetidos = List.of(
                new HomologacionRequest(cono.getId(), "ABC-1"),
                new HomologacionRequest(paleta.getId(), "abc-1"));

        assertEstado(() -> service.guardarHomologaciones(proveedor.getId(), repetidos), HttpStatus.CONFLICT);
        verify(homologacionRepository, never()).saveAndFlush(any(CodigoHomologacion.class));
    }

    @Test
    @DisplayName("CP-67-14: un código ya usado por otro producto del proveedor responde 409")
    void codigoDeOtroProductoEs409() {
        Producto otro = producto("Otro", "SKU-9");
        CodigoHomologacion existente = new CodigoHomologacion();
        existente.setProveedor(proveedor);
        existente.setProducto(otro);
        existente.setCodigo("ABC-1");
        when(homologacionRepository.listarPorProveedor(proveedor.getId())).thenReturn(List.of(existente));

        assertEstado(() -> service.guardarHomologaciones(proveedor.getId(),
                List.of(new HomologacionRequest(cono.getId(), "ABC-1"))), HttpStatus.CONFLICT);
    }

    @Test
    @DisplayName("CP-67-15: guarda un código nuevo y quita el de un producto con código vacío")
    void guardaYQuitaCodigos() {
        CodigoHomologacion deLaPaleta = new CodigoHomologacion();
        deLaPaleta.setProveedor(proveedor);
        deLaPaleta.setProducto(paleta);
        deLaPaleta.setCodigo("PF-30");
        when(homologacionRepository.listarPorProveedor(proveedor.getId())).thenReturn(List.of(deLaPaleta));
        when(productoRepository.findById(cono.getId())).thenReturn(Optional.of(cono));
        cono.setTenantId(TENANT);

        service.guardarHomologaciones(proveedor.getId(), List.of(
                new HomologacionRequest(cono.getId(), "CV-24"),
                new HomologacionRequest(paleta.getId(), " ")));

        verify(homologacionRepository).delete(deLaPaleta);
        verify(homologacionRepository).saveAndFlush(any(CodigoHomologacion.class));
    }

    @Test
    @DisplayName("CP-67-16: la vista previa valida la plantilla y no guarda nada")
    void vistaPreviaNoGuarda() {
        when(exportadorPedido.previsualizar(any(), any(), any()))
                .thenReturn(new VistaPreviaDTO(List.of("Código"), List.of(List.of("CV-24")), List.of()));

        VistaPreviaDTO previa = service.vistaPrevia(proveedor.getId(),
                filas(List.of(col(CampoPlantilla.CODIGO_PROVEEDOR, "Código", null)), null));

        assertThat(previa.encabezados()).containsExactly("Código");
        verify(plantillaRepository, never()).save(any());
        assertEstado(() -> service.vistaPrevia(proveedor.getId(), filas(List.of(), null)), HttpStatus.BAD_REQUEST);
    }
}
