package com.gelox.backend.rf63;

import com.gelox.backend.dto.PedidoDetalleDTO;
import com.gelox.backend.dto.PedidoResumenDTO;
import com.gelox.backend.entities.EstadoPedido;
import com.gelox.backend.entities.PedidoProveedor;
import com.gelox.backend.entities.Proveedor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * T197 — la lista y el detalle de pedidos entregan el proveedor de cada pedido
 * (lo muestran ReportePedido.jsx y DetallePedido.jsx).
 */
class PedidoProveedorContratoTest {

    private static PedidoProveedor pedido(Proveedor proveedor) {
        return PedidoProveedor.builder()
                .id(UUID.randomUUID()).fecha(LocalDate.of(2026, 11, 25)).estado(EstadoPedido.PENDIENTE)
                .proveedor(proveedor).items(new ArrayList<>()).build();
    }

    @Test
    @DisplayName("el detalle del pedido trae el id y el nombre del proveedor")
    void detalleConProveedor() {
        Proveedor proveedor = new Proveedor();
        proveedor.setId(UUID.randomUUID());
        proveedor.setNombre("Crem Helado");

        PedidoDetalleDTO dto = PedidoDetalleDTO.from(pedido(proveedor));

        assertThat(dto.proveedorId()).isEqualTo(proveedor.getId());
        assertThat(dto.proveedorNombre()).isEqualTo("Crem Helado");
    }

    @Test
    @DisplayName("un pedido sin proveedor responde con el proveedor nulo")
    void detalleSinProveedor() {
        PedidoDetalleDTO dto = PedidoDetalleDTO.from(pedido(null));

        assertThat(dto.proveedorId()).isNull();
        assertThat(dto.proveedorNombre()).isNull();
    }

    @Test
    @DisplayName("la fila de la consulta con fechas lee el proveedor en las dos últimas columnas")
    void resumenDesdeFila() {
        UUID id = UUID.randomUUID();
        UUID proveedorId = UUID.randomUUID();

        PedidoResumenDTO dto = PedidoResumenDTO.fromRow(new Object[]{
                id.toString(), LocalDate.of(2026, 11, 25), "PENDIENTE", null, 4, 7, proveedorId.toString(), "Nutresa"});

        assertThat(dto.totalCajas()).isEqualTo(4);
        assertThat(dto.totalUnidades()).isEqualTo(7);
        assertThat(dto.proveedorId()).isEqualTo(proveedorId);
        assertThat(dto.proveedorNombre()).isEqualTo("Nutresa");
    }
}
