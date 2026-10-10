package com.gelox.backend.services;

import com.gelox.backend.entities.EstadoPedido;
import com.gelox.backend.entities.ItemPedidoProveedor;
import com.gelox.backend.entities.PedidoProveedor;
import com.gelox.backend.entities.Producto;
import com.gelox.backend.repositories.PedidoProveedorRepository;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * T47 — reexportar el Excel de un pedido ya creado.
 */
@ExtendWith(MockitoExtension.class)
class PedidoProveedorServiceExportarTest {

    @Mock
    PedidoProveedorRepository pedidoRepo;

    @InjectMocks
    PedidoProveedorService service;

    @Test
    void pedidoInexistente_lanza404() {
        UUID id = UUID.randomUUID();
        when(pedidoRepo.findByIdWithItems(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.exportarExcelPedido(id))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode().value()).isEqualTo(HttpStatus.NOT_FOUND.value()));
    }

    @Test
    void pedidoExistente_devuelveUnXlsxValidoConSusItems() throws Exception {
        ReflectionTestUtils.setField(service, "plantillaExcel", new ClassPathResource("templates/catalogo.xlsx"));

        Producto producto = new Producto();
        producto.setId(UUID.randomUUID());
        producto.setCodigoTecnico("SKU-TEST-001");
        producto.setNombre("Producto de prueba");

        PedidoProveedor pedido = PedidoProveedor.builder()
                .id(UUID.randomUUID())
                .fecha(LocalDate.now())
                .estado(EstadoPedido.PENDIENTE)
                .build();
        ItemPedidoProveedor item = ItemPedidoProveedor.builder()
                .pedido(pedido)
                .producto(producto)
                .cantidadCajas(10)
                .cantidadUnidades(0)
                .precioUnitario(BigDecimal.TEN)
                .build();
        pedido.setItems(List.of(item));

        when(pedidoRepo.findByIdWithItems(pedido.getId())).thenReturn(Optional.of(pedido));

        byte[] excel = service.exportarExcelPedido(pedido.getId());

        assertThat(excel).isNotEmpty();
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(excel))) {
            Sheet sheet = wb.getSheetAt(0);
            assertThat(sheet).isNotNull();
            assertThat(sheet.getLastRowNum()).isGreaterThan(0);
        }
    }

    @Test
    void lasCantidadesQuedanEnLaUnidadDeCadaFilaYLaPlantillaNoCambia() throws Exception {
        ReflectionTestUtils.setField(service, "plantillaExcel", new ClassPathResource("templates/catalogo.xlsx"));

        PedidoProveedor pedido = PedidoProveedor.builder()
                .id(UUID.randomUUID()).fecha(LocalDate.now()).estado(EstadoPedido.PENDIENTE).build();
        pedido.setItems(List.of(
                item(pedido, producto("1082924", 60), 3, 0),       // fila CJ: 3 cajas
                item(pedido, producto("1074922", 60), 2, 120),     // fila CJ: 2 cajas + 120 sueltas = 4 cajas
                item(pedido, producto("1082990", null), 0, 7),     // fila UN: 7 unidades
                item(pedido, producto("SKU-NUEVO", 24), 4, 0)));   // sin fila en la plantilla
        when(pedidoRepo.findByIdWithItems(pedido.getId())).thenReturn(Optional.of(pedido));

        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(service.exportarExcelPedido(pedido.getId())));
             XSSFWorkbook tpl = new XSSFWorkbook(new ClassPathResource("templates/catalogo.xlsx").getInputStream())) {
            Sheet sheet = wb.getSheetAt(0);
            assertThat(cantidades(sheet, "1082924")).containsExactly("CJ=3");
            assertThat(cantidades(sheet, "1074922")).containsExactly("CJ=4");
            assertThat(cantidades(sheet, "1082990")).containsExactly("UN=7");
            assertThat(cantidades(sheet, "SKU-NUEVO")).isEmpty();

            // Se sube a Nutresa tal cual: mismas filas y todo igual salvo la columna Cantidad (D).
            Sheet original = tpl.getSheetAt(0);
            assertThat(sheet.getLastRowNum()).isEqualTo(original.getLastRowNum());
            org.apache.poi.ss.usermodel.DataFormatter f = new org.apache.poi.ss.usermodel.DataFormatter();
            for (org.apache.poi.ss.usermodel.Row r : original) {
                for (var c : r) {
                    if (c.getColumnIndex() == 3) continue;
                    assertThat(f.formatCellValue(sheet.getRow(r.getRowNum()).getCell(c.getColumnIndex())))
                            .as("celda %s", c.getAddress()).isEqualTo(f.formatCellValue(c));
                }
            }
        }
    }

    /** "UM=cantidad" de cada fila del SKU que tenga cantidad. */
    private static List<String> cantidades(Sheet sheet, String sku) {
        List<String> out = new java.util.ArrayList<>();
        for (org.apache.poi.ss.usermodel.Row row : sheet) {
            var c0 = row.getCell(0);
            var c3 = row.getCell(3);
            if (c0 == null || c3 == null || c3.getCellType() != org.apache.poi.ss.usermodel.CellType.NUMERIC) continue;
            String s = c0.getCellType() == org.apache.poi.ss.usermodel.CellType.NUMERIC
                    ? String.valueOf((long) c0.getNumericCellValue()) : c0.getStringCellValue().trim();
            if (s.equals(sku) && c3.getNumericCellValue() > 0) {
                out.add(row.getCell(2).getStringCellValue() + "=" + (int) c3.getNumericCellValue());
            }
        }
        return out;
    }

    private static Producto producto(String sku, Integer unidadesPorCaja) {
        Producto p = new Producto();
        p.setId(UUID.randomUUID());
        p.setCodigoTecnico(sku);
        p.setNombre("Producto " + sku);
        p.setUnidadesPorCaja(unidadesPorCaja);
        return p;
    }

    private static ItemPedidoProveedor item(PedidoProveedor pedido, Producto p, int cajas, int unidades) {
        return ItemPedidoProveedor.builder().pedido(pedido).producto(p)
                .cantidadCajas(cajas).cantidadUnidades(unidades).precioUnitario(BigDecimal.TEN).build();
    }
}
