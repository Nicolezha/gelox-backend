package com.gelox.backend.rf57;

import com.gelox.backend.catalogo.dto.PagedResponse;
import com.gelox.backend.entities.EstadoPedido;
import com.gelox.backend.entities.EstadoRecomendacion;
import com.gelox.backend.entities.ItemPedidoProveedor;
import com.gelox.backend.entities.ItemRecomendacion;
import com.gelox.backend.entities.PedidoProveedor;
import com.gelox.backend.entities.Producto;
import com.gelox.backend.entities.RecomendacionCompra;
import com.gelox.backend.recomendacion.DecisionRecomendacionService;
import com.gelox.backend.recomendacion.dto.MetricasRecomendacionDTO;
import com.gelox.backend.recomendacion.dto.RecomendacionResumenDTO;
import com.gelox.backend.repositories.ItemPedidoProveedorRepository;
import com.gelox.backend.repositories.PedidoProveedorRepository;
import com.gelox.backend.repositories.RecomendacionCompraRepository;
import com.gelox.backend.services.EventoSistemaService;
import com.gelox.backend.services.PedidoProveedorService;
import com.gelox.backend.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collection;
import java.util.List;

import static com.gelox.backend.recomendacion.RecomendacionTestData.TENANT;
import static com.gelox.backend.recomendacion.RecomendacionTestData.item;
import static com.gelox.backend.recomendacion.RecomendacionTestData.pedido;
import static com.gelox.backend.recomendacion.RecomendacionTestData.producto;
import static com.gelox.backend.recomendacion.RecomendacionTestData.recomendacion;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * RF57 — DecisionRecomendacionService.historial y metricas, con dos recomendaciones de prueba.
 */
@ExtendWith(MockitoExtension.class)
class MetricasRecomendacionTest {

    @Mock RecomendacionCompraRepository recomendacionRepo;
    @Mock PedidoProveedorRepository pedidoRepo;
    @Mock ItemPedidoProveedorRepository itemPedidoRepo;
    @Mock PedidoProveedorService pedidoService;
    @Mock EventoSistemaService eventoService;

    DecisionRecomendacionService service;

    Producto festival;
    Producto soloLack;
    RecomendacionCompra conPedido;     // pedido RECIBIDO
    RecomendacionCompra sinPedido;

    @BeforeEach
    void setUp() {
        TenantContext.set(TENANT);
        service = new DecisionRecomendacionService(recomendacionRepo, pedidoRepo, itemPedidoRepo,
                pedidoService, eventoService);

        festival = producto("Festival", 12);
        soloLack = producto("Solo Lack", 12);

        // Recomendación A: Festival aprobado (10 de 10), Solo Lack modificado (5 de 10), uno rechazado
        ItemRecomendacion aprobado = item(festival, 1, 10, "1000.00");
        aprobado.aprobar();
        ItemRecomendacion modificado = item(soloLack, 2, 10, "500.00");
        modificado.modificar(5);
        ItemRecomendacion rechazado = item(producto("Aloha", 12), 3, 4, "300.00");
        rechazado.rechazar("Sin demanda");
        conPedido = recomendacion(TENANT, EstadoRecomendacion.PEDIDO_GENERADO, aprobado, modificado, rechazado);

        // Recomendación B: sin pedido, un aprobado y un pendiente
        ItemRecomendacion aprobadoB = item(festival, 1, 6, "600.00");
        aprobadoB.aprobar();
        ItemRecomendacion pendienteB = item(soloLack, 2, 3, "300.00");
        sinPedido = recomendacion(TENANT, EstadoRecomendacion.EN_REVISION, aprobadoB, pendienteB);
    }

    @AfterEach
    void limpiarTenant() {
        TenantContext.clear();
    }

    private ItemPedidoProveedor recibido(Producto producto, int unidadesRecibidas) {
        return ItemPedidoProveedor.builder().producto(producto).cantidadRecibida(unidadesRecibidas).build();
    }

    private void stubRecomendaciones() {
        when(recomendacionRepo.buscarConItems(eq(TENANT), any(), any())).thenReturn(List.of(conPedido, sinPedido));
    }

    // ---- Métricas ----

    @Test
    @DisplayName("cuenta generadas, ítems y decisiones; tasaAprobacion = (aprobados + modificados) / ítems")
    void conteos() {
        stubRecomendaciones();

        MetricasRecomendacionDTO m = service.metricas(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31));

        assertThat(m.generadas()).isEqualTo(2);
        assertThat(m.itemsSugeridos()).isEqualTo(5);
        assertThat(m.aprobados()).isEqualTo(2);
        assertThat(m.modificados()).isEqualTo(1);
        assertThat(m.rechazados()).isEqualTo(1);
        assertThat(m.tasaAprobacion()).isEqualTo(0.6);
    }

    @Test
    @DisplayName("precisionCantidad con pedido recibido compara cajas pedidas y recibidas con las sugeridas")
    void precisionConPedidoRecibido() {
        stubRecomendaciones();
        PedidoProveedor recibidoPedido = pedido(EstadoPedido.RECIBIDO);
        conPedido.setPedidoProveedor(recibidoPedido);
        // Festival: 120 uds = 10 cajas; Solo Lack: 36 uds = 3 cajas
        when(itemPedidoRepo.findByPedidoIdWithProducto(recibidoPedido.getId()))
                .thenReturn(List.of(recibido(festival, 120), recibido(soloLack, 36)));

        MetricasRecomendacionDTO m = service.metricas(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31));

        // Festival: pedidas 1 − 0/10 = 1.0 y recibidas 1.0 · Solo Lack: pedidas 1 − 5/10 = 0.5 y recibidas 1 − 7/10 = 0.3
        // Promedio de los 4 términos: (1.0 + 1.0 + 0.5 + 0.3) / 4 = 0.7
        assertThat(m.precisionCantidad()).isEqualTo(0.7);
    }

    @Test
    @DisplayName("pedido aún no recibido: solo entran las cajas pedidas")
    void precisionConPedidoPendiente() {
        stubRecomendaciones();
        conPedido.setPedidoProveedor(pedido(EstadoPedido.PENDIENTE));

        MetricasRecomendacionDTO m = service.metricas(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31));

        assertThat(m.precisionCantidad()).isEqualTo(0.75);   // (1.0 + 0.5) / 2
    }

    @Test
    @DisplayName("sin pedidos no hay datos de precisión (null)")
    void precisionSinPedidos() {
        stubRecomendaciones();

        MetricasRecomendacionDTO m = service.metricas(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31));

        assertThat(m.precisionCantidad()).isNull();
    }

    @Test
    @DisplayName("sin recomendaciones en el rango las métricas quedan en cero")
    void rangoVacio() {
        when(recomendacionRepo.buscarConItems(eq(TENANT), any(), any())).thenReturn(List.of());

        MetricasRecomendacionDTO m = service.metricas(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31));

        assertThat(m.generadas()).isZero();
        assertThat(m.itemsSugeridos()).isZero();
        assertThat(m.tasaAprobacion()).isZero();
        assertThat(m.precisionCantidad()).isNull();
    }

    @Test
    @DisplayName("el rango incluye el día final completo y por defecto usa hoy en Bogotá")
    void rangoDeFechas() {
        when(recomendacionRepo.buscarConItems(eq(TENANT), any(), any())).thenReturn(List.of());

        service.metricas(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31));
        verify(recomendacionRepo).buscarConItems(TENANT,
                LocalDateTime.of(2026, 10, 1, 0, 0), LocalDateTime.of(2026, 11, 1, 0, 0));

        service.metricas(null, null);
        LocalDate hoy = LocalDate.now(ZoneId.of("America/Bogota"));
        verify(recomendacionRepo).buscarConItems(TENANT,
                hoy.minusDays(30).atStartOfDay(), hoy.plusDays(1).atStartOfDay());
    }

    @Test
    @DisplayName("fecha inicial posterior a la final → 400")
    void rangoInvalido() {
        assertThatThrownBy(() -> service.metricas(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 10, 1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.historial(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 10, 1), null, 0, 20))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ---- Historial ----

    @Test
    @DisplayName("el historial pagina las recomendaciones del tenant con el conteo de decisiones")
    @SuppressWarnings("unchecked")
    void historial() {
        Pageable pageable = PageRequest.of(0, 20);
        when(recomendacionRepo.buscar(eq(TENANT), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(conPedido, sinPedido), pageable, 2));

        PagedResponse<RecomendacionResumenDTO> respuesta =
                service.historial(null, null, null, 0, 20);

        assertThat(respuesta.totalElements()).isEqualTo(2);
        assertThat(respuesta.content()).hasSize(2);
        RecomendacionResumenDTO primera = respuesta.content().get(0);
        assertThat(primera.totalItems()).isEqualTo(3);
        assertThat(primera.aprobados()).isEqualTo(1);
        assertThat(primera.modificados()).isEqualTo(1);
        assertThat(primera.rechazados()).isEqualTo(1);
        RecomendacionResumenDTO segunda = respuesta.content().get(1);
        assertThat(segunda.pendientes()).isEqualTo(1);

        ArgumentCaptor<Collection<EstadoRecomendacion>> estados = ArgumentCaptor.forClass(Collection.class);
        verify(recomendacionRepo).buscar(eq(TENANT), estados.capture(), any(), any(), any());
        assertThat(estados.getValue()).containsExactlyInAnyOrder(EstadoRecomendacion.values());
    }

    @Test
    @DisplayName("el filtro de estado se pasa al repositorio")
    @SuppressWarnings("unchecked")
    void historialFiltraPorEstado() {
        when(recomendacionRepo.buscar(eq(TENANT), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        service.historial(null, null, EstadoRecomendacion.DESCARTADA, 0, 20);

        ArgumentCaptor<Collection<EstadoRecomendacion>> estados = ArgumentCaptor.forClass(Collection.class);
        verify(recomendacionRepo).buscar(eq(TENANT), estados.capture(), any(), any(), any());
        assertThat(estados.getValue()).containsExactly(EstadoRecomendacion.DESCARTADA);
    }
}
