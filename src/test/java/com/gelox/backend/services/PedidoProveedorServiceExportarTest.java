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
}
