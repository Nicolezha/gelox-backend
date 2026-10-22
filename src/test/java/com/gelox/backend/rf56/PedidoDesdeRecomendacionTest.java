package com.gelox.backend.rf56;

import com.gelox.backend.TestHelper;
import com.gelox.backend.dto.CrearPedidoRequest;
import com.gelox.backend.dto.ItemPedidoRequest;
import com.gelox.backend.entities.DecisionItem;
import com.gelox.backend.entities.EstadoPedido;
import com.gelox.backend.entities.EstadoRecomendacion;
import com.gelox.backend.entities.ItemRecomendacion;
import com.gelox.backend.entities.PedidoProveedor;
import com.gelox.backend.entities.RecomendacionCompra;
import com.gelox.backend.entities.RolUsuario;
import com.gelox.backend.entities.TipoEvento;
import com.gelox.backend.entities.Usuario;
import com.gelox.backend.recomendacion.DecisionRecomendacionService;
import com.gelox.backend.recomendacion.dto.PedidoGeneradoDTO;
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
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import static com.gelox.backend.recomendacion.RecomendacionTestData.TENANT;
import static com.gelox.backend.recomendacion.RecomendacionTestData.item;
import static com.gelox.backend.recomendacion.RecomendacionTestData.pedido;
import static com.gelox.backend.recomendacion.RecomendacionTestData.producto;
import static com.gelox.backend.recomendacion.RecomendacionTestData.recomendacion;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * RF56 — DecisionRecomendacionService.generarPedido.
 */
@ExtendWith(MockitoExtension.class)
class PedidoDesdeRecomendacionTest {

    @Mock RecomendacionCompraRepository recomendacionRepo;
    @Mock PedidoProveedorRepository pedidoRepo;
    @Mock ItemPedidoProveedorRepository itemPedidoRepo;
    @Mock PedidoProveedorService pedidoService;
    @Mock EventoSistemaService eventoService;

    DecisionRecomendacionService service;
    Usuario usuario;

    ItemRecomendacion festival;
    ItemRecomendacion soloLack;
    RecomendacionCompra recomendacion;
    PedidoProveedor pedido;

    @BeforeEach
    void setUp() {
        TenantContext.set(TENANT);
        service = new DecisionRecomendacionService(recomendacionRepo, pedidoRepo, itemPedidoRepo,
                pedidoService, eventoService);
        usuario = TestHelper.buildUsuario("uid-1", "admin@gelox-test.com", RolUsuario.ADMINISTRADOR, true);
        usuario.setId(UUID.randomUUID());

        festival = item(producto("Festival", 12), 1, 10, "1000.00");
        soloLack = item(producto("Solo Lack", 12), 2, 5, "500.00");
        recomendacion = recomendacion(TENANT, EstadoRecomendacion.EN_REVISION, festival, soloLack);

        pedido = pedido(EstadoPedido.PENDIENTE);
        lenient().when(recomendacionRepo.findById(recomendacion.getId())).thenReturn(Optional.of(recomendacion));
        lenient().when(recomendacionRepo.save(any())).thenAnswer(i -> i.getArgument(0));
        lenient().when(pedidoRepo.findById(pedido.getId())).thenReturn(Optional.of(pedido));
        lenient().when(pedidoService.crearPedido(any(), any()))
                .thenReturn(Map.of("pedidoId", pedido.getId(), "excel", new byte[0]));
    }

    @AfterEach
    void limpiarTenant() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("genera el pedido con los ítems aprobados y modificados, y guarda el vínculo")
    void generaElPedido() {
        festival.aprobar();
        soloLack.modificar(2);

        PedidoGeneradoDTO dto = service.generarPedido(recomendacion.getId(), usuario);

        ArgumentCaptor<CrearPedidoRequest> captor = ArgumentCaptor.forClass(CrearPedidoRequest.class);
        verify(pedidoService).crearPedido(captor.capture(), any());
        assertThat(captor.getValue().items()).containsExactlyInAnyOrder(
                new ItemPedidoRequest(festival.getProducto().getId(), 10, 0),
                new ItemPedidoRequest(soloLack.getProducto().getId(), 2, 0));
        assertThat(captor.getValue().notas())
                .isEqualTo("Generado desde recomendación #" + recomendacion.getId().toString().substring(0, 8));

        assertThat(dto.pedidoId()).isEqualTo(pedido.getId());
        assertThat(dto.exportUrl()).isEqualTo("/api/inventario/pedidos/" + pedido.getId() + "/exportar");
        assertThat(recomendacion.getPedidoProveedor()).isSameAs(pedido);
        assertThat(recomendacion.getEstado()).isEqualTo(EstadoRecomendacion.PEDIDO_GENERADO);
        verify(eventoService).registrarEvento(
                org.mockito.ArgumentMatchers.eq(TipoEvento.PEDIDO_DESDE_RECOMENDACION), anyString(),
                org.mockito.ArgumentMatchers.eq(usuario.getId()));
    }

    @Test
    @DisplayName("los ítems rechazados o pendientes no entran al pedido")
    void soloAprobadosEntranAlPedido() {
        festival.aprobar();
        soloLack.rechazar("No hace falta");

        service.generarPedido(recomendacion.getId(), usuario);

        ArgumentCaptor<CrearPedidoRequest> captor = ArgumentCaptor.forClass(CrearPedidoRequest.class);
        verify(pedidoService).crearPedido(captor.capture(), any());
        assertThat(captor.getValue().items()).hasSize(1);
        assertThat(captor.getValue().items().get(0).productoId()).isEqualTo(festival.getProducto().getId());
    }

    @Test
    @DisplayName("sin ítems aprobados → 400 «Aprueba al menos un producto»")
    void sinAprobados() {
        soloLack.rechazar(null);
        assertThat(festival.getDecision()).isEqualTo(DecisionItem.PENDIENTE);

        assertThatThrownBy(() -> service.generarPedido(recomendacion.getId(), usuario))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Aprueba al menos un producto");
        verify(pedidoService, never()).crearPedido(any(), any());
    }

    @Test
    @DisplayName("generar dos veces → la segunda responde 409")
    void dosVeces() {
        festival.aprobar();

        service.generarPedido(recomendacion.getId(), usuario);

        assertThatThrownBy(() -> service.generarPedido(recomendacion.getId(), usuario))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> {
                    assertThat(e.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(e.getReason()).isEqualTo("La recomendación ya se convirtió en pedido");
                });
    }

    @Test
    @DisplayName("recomendación de otro tenant → 404 y no se crea el pedido")
    void otroTenant() {
        RecomendacionCompra ajena = recomendacion(UUID.randomUUID(), EstadoRecomendacion.EN_REVISION, festival);
        festival.aprobar();
        when(recomendacionRepo.findById(ajena.getId())).thenReturn(Optional.of(ajena));

        assertThatThrownBy(() -> service.generarPedido(ajena.getId(), usuario))
                .isInstanceOf(NoSuchElementException.class);
        verify(pedidoService, never()).crearPedido(any(), any());
    }
}
