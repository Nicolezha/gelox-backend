package com.gelox.backend.rf67;

import com.gelox.backend.TestHelper;
import com.gelox.backend.entities.CampoPlantilla;
import com.gelox.backend.entities.CodigoHomologacion;
import com.gelox.backend.entities.EstadoPedido;
import com.gelox.backend.entities.ItemPedidoProveedor;
import com.gelox.backend.entities.ModoPlantilla;
import com.gelox.backend.entities.PedidoProveedor;
import com.gelox.backend.entities.PlantillaColumna;
import com.gelox.backend.entities.PlantillaExportacion;
import com.gelox.backend.entities.Producto;
import com.gelox.backend.entities.Proveedor;
import com.gelox.backend.exportacion.ExportadorPedido;
import com.gelox.backend.plataforma.IdentidadDistribuidoraService;
import com.gelox.backend.repositories.CodigoHomologacionRepository;
import com.gelox.backend.repositories.PlantillaExportacionRepository;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * RF67 — ExportadorPedido con los dos modos de FP-15 §2.18.1: GENERAR_FILAS (libro nuevo con las
 * columnas de la plantilla), RELLENAR_CATALOGO (catalogo.xlsx de Nutresa buscando el código en la
 * homologación), la plantilla estándar cuando el proveedor no tiene una, y el aviso de productos
 * sin código (el pedido se exporta igual).
 */
class ExportadorPedidoTest {

    private final PlantillaExportacionRepository plantillaRepo = mock(PlantillaExportacionRepository.class);
    private final CodigoHomologacionRepository homologacionRepo = mock(CodigoHomologacionRepository.class);
    private final DataFormatter formato = new DataFormatter();

    private ExportadorPedido exportador;
    private Proveedor proveedor;
    private final List<CodigoHomologacion> codigosDelProveedor = new ArrayList<>();

    @BeforeEach
    void preparar() {
        IdentidadDistribuidoraService identidad = mock(IdentidadDistribuidoraService.class);
        ExportadorPedido real = TestHelper.exportador(identidad);
        // Mismo exportador, pero con los repositorios de este test.
        exportador = new ExportadorPedido(plantillaRepo, homologacionRepo, identidad);
        org.springframework.test.util.ReflectionTestUtils.setField(exportador, "plantillaExcel",
                org.springframework.test.util.ReflectionTestUtils.getField(real, "plantillaExcel"));

        proveedor = new Proveedor();
        proveedor.setId(UUID.randomUUID());
        proveedor.setNombre("Crem Helado");
        when(homologacionRepo.listarPorProveedor(proveedor.getId())).thenReturn(codigosDelProveedor);
    }

    private static Producto producto(String nombre, String sku, String codigoTecnico, Integer porCaja) {
        Producto p = new Producto();
        p.setId(UUID.randomUUID());
        p.setNombre(nombre);
        p.setSku(sku);
        p.setCodigoTecnico(codigoTecnico);
        p.setUnidadesPorCaja(porCaja);
        return p;
    }

    private static PedidoProveedor pedido(Object... productoCajasUnidades) {
        PedidoProveedor pedido = PedidoProveedor.builder()
                .id(UUID.randomUUID()).fecha(LocalDate.of(2026, 11, 24)).estado(EstadoPedido.PENDIENTE).build();
        List<ItemPedidoProveedor> items = new ArrayList<>();
        for (int i = 0; i < productoCajasUnidades.length; i += 3) {
            items.add(ItemPedidoProveedor.builder().pedido(pedido)
                    .producto((Producto) productoCajasUnidades[i])
                    .cantidadCajas((Integer) productoCajasUnidades[i + 1])
                    .cantidadUnidades((Integer) productoCajasUnidades[i + 2])
                    .precioUnitario(BigDecimal.valueOf(1000)).build());
        }
        pedido.setItems(items);
        return pedido;
    }

    private void homologar(Producto p, String codigo) {
        CodigoHomologacion h = new CodigoHomologacion();
        h.setProveedor(proveedor);
        h.setProducto(p);
        h.setCodigo(codigo);
        codigosDelProveedor.add(h);
    }

    private PlantillaExportacion plantillaFilas(PlantillaColumna... columnas) {
        PlantillaExportacion p = new PlantillaExportacion();
        p.setProveedor(proveedor);
        p.setNombre("Pedido Crem Helado");
        p.setModo(ModoPlantilla.GENERAR_FILAS);
        int orden = 1;
        for (PlantillaColumna c : columnas) {
            c.setOrden(orden++);
            c.setPlantilla(p);
            p.getColumnas().add(c);
        }
        when(plantillaRepo.findByProveedorId(proveedor.getId())).thenReturn(Optional.of(p));
        return p;
    }

    private static PlantillaColumna columna(CampoPlantilla campo, String encabezado, String valorFijo) {
        PlantillaColumna c = new PlantillaColumna();
        c.setCampo(campo);
        c.setEncabezado(encabezado);
        c.setValorFijo(valorFijo);
        return c;
    }

    @Test
    @DisplayName("CP-67-01: GENERAR_FILAS escribe las columnas en el orden configurado y una fila por producto")
    void generarFilas() throws Exception {
        Producto cono = producto("Cono Vainilla", "SKU-1", "T1", 24);
        Producto paleta = producto("Paleta Fresa", "SKU-2", "T2", 30);
        homologar(cono, "CV-24");
        homologar(paleta, "PF-30");
        plantillaFilas(
                columna(CampoPlantilla.CODIGO_PROVEEDOR, "Código", null),
                columna(CampoPlantilla.NOMBRE_PRODUCTO, "Producto", null),
                columna(CampoPlantilla.CANTIDAD_CAJAS, "Cajas", null),
                columna(CampoPlantilla.PRECIO_UNITARIO, "Precio", null),
                columna(CampoPlantilla.SUBTOTAL, "Total", null),
                columna(CampoPlantilla.TEXTO_FIJO, "Cliente", "CLI-778"));

        ExportadorPedido.Resultado r = exportador.generar(pedido(cono, 2, 0, paleta, 1, 0), proveedor);

        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(r.contenido()))) {
            Sheet hoja = wb.getSheetAt(0);
            assertThat(texto(hoja.getRow(0))).containsExactly("Código", "Producto", "Cajas", "Precio", "Total", "Cliente");
            assertThat(hoja.getLastRowNum()).isEqualTo(2); // encabezado + 2 productos
            assertThat(texto(hoja.getRow(1))).containsExactly("CV-24", "Cono Vainilla", "2", "1000", "48000", "CLI-778");
            assertThat(texto(hoja.getRow(2))).containsExactly("PF-30", "Paleta Fresa", "1", "1000", "30000", "CLI-778");
        }
        assertThat(r.productosSinCodigo()).isEmpty();
    }

    @Test
    @DisplayName("CP-67-02: un producto sin código no se omite: el pedido sale igual y se avisa cuál falta")
    void productoSinCodigoNoSeOmite() throws Exception {
        Producto conCodigo = producto("Cono Vainilla", "SKU-1", "T1", 24);
        Producto sinCodigo = producto("Bombón Almendra", "SKU-5", "T5", 20);
        homologar(conCodigo, "CV-24");
        plantillaFilas(
                columna(CampoPlantilla.CODIGO_PROVEEDOR, "Código", null),
                columna(CampoPlantilla.NOMBRE_PRODUCTO, "Producto", null));

        ExportadorPedido.Resultado r = exportador.generar(pedido(conCodigo, 1, 0, sinCodigo, 1, 0), proveedor);

        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(r.contenido()))) {
            assertThat(wb.getSheetAt(0).getLastRowNum()).isEqualTo(2); // los dos productos están
        }
        assertThat(r.productosSinCodigo()).containsExactly("Bombón Almendra");
    }

    @Test
    @DisplayName("CP-67-03: RELLENAR_CATALOGO busca el código en la homologación y escribe la cantidad")
    void rellenarCatalogoConHomologacion() throws Exception {
        // catalogo.xlsx de Nutresa: la fila del código 1082924 es de cajas (CJ).
        Producto helado = producto("Aloha Paleta Limón", "SKU-A", "OTRO-CODIGO-TECNICO", 60);
        homologar(helado, "1082924");
        PlantillaExportacion p = ExportadorPedido.plantillaEstandar();
        p.setProveedor(proveedor);
        when(plantillaRepo.findByProveedorId(proveedor.getId())).thenReturn(Optional.of(p));

        ExportadorPedido.Resultado r = exportador.generar(pedido(helado, 3, 0), proveedor);

        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(r.contenido()))) {
            assertThat(cantidades(wb.getSheetAt(0), "1082924")).containsExactly("CJ=3");
        }
        assertThat(r.productosSinCodigo()).isEmpty();
        assertThat(r.productosNoUbicados()).isEmpty();
    }

    @Test
    @DisplayName("CP-67-04: un código que no está en el archivo base se avisa como no ubicado")
    void codigoQueNoEstaEnElArchivoBase() throws Exception {
        Producto raro = producto("Producto nuevo", "SKU-N", "X", 10);
        homologar(raro, "CODIGO-QUE-NO-EXISTE");
        PlantillaExportacion p = ExportadorPedido.plantillaEstandar();
        p.setProveedor(proveedor);
        when(plantillaRepo.findByProveedorId(proveedor.getId())).thenReturn(Optional.of(p));

        ExportadorPedido.Resultado r = exportador.generar(pedido(raro, 2, 0), proveedor);

        assertThat(r.productosNoUbicados()).containsExactly("Producto nuevo");
        assertThat(r.productosSinCodigo()).isEmpty();
    }

    @Test
    @DisplayName("CP-67-05: sin plantilla del proveedor se usa la estándar de Nutresa")
    void sinPlantillaUsaLaEstandar() throws Exception {
        Producto helado = producto("Aloha Paleta Limón", "SKU-A", "T", 60);
        homologar(helado, "1082924");
        when(plantillaRepo.findByProveedorId(proveedor.getId())).thenReturn(Optional.empty());

        ExportadorPedido.Resultado r = exportador.generar(pedido(helado, 2, 0), proveedor);

        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(r.contenido()))) {
            assertThat(cantidades(wb.getSheetAt(0), "1082924")).containsExactly("CJ=2");
        }
    }

    @Test
    @DisplayName("CP-67-06: pedido sin proveedor (anterior a RF63) usa el código técnico como antes")
    void pedidoSinProveedorUsaCodigoTecnico() throws Exception {
        Producto helado = producto("Aloha Paleta Limón", "SKU-A", "1082924", 60);

        ExportadorPedido.Resultado r = exportador.generar(pedido(helado, 4, 0), null);

        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(r.contenido()))) {
            assertThat(cantidades(wb.getSheetAt(0), "1082924")).containsExactly("CJ=4");
        }
    }

    /* ── Utilidades ── */

    private List<String> texto(Row fila) {
        List<String> valores = new ArrayList<>();
        for (int c = 0; c < fila.getLastCellNum(); c++) valores.add(formato.formatCellValue(fila.getCell(c)));
        return valores;
    }

    /** "UM=cantidad" de cada fila del código que tenga cantidad (columnas A = código, C = unidad, D = cantidad). */
    private static List<String> cantidades(Sheet sheet, String codigo) {
        List<String> out = new ArrayList<>();
        DataFormatter f = new DataFormatter();
        for (Row row : sheet) {
            var c0 = row.getCell(0);
            var c3 = row.getCell(3);
            if (c0 == null || c3 == null || c3.getCellType() != org.apache.poi.ss.usermodel.CellType.NUMERIC) continue;
            if (f.formatCellValue(c0).trim().equals(codigo) && c3.getNumericCellValue() > 0) {
                out.add(row.getCell(2).getStringCellValue() + "=" + (int) c3.getNumericCellValue());
            }
        }
        return out;
    }
}
