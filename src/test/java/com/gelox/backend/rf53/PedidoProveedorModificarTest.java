package com.gelox.backend.rf53;

import com.gelox.backend.TestHelper;
import com.gelox.backend.dto.AccionPedido;
import com.gelox.backend.dto.ModificarPedidoRequest;
import com.gelox.backend.entities.EstadoPedido;
import com.gelox.backend.entities.ItemPedidoProveedor;
import com.gelox.backend.entities.PedidoProveedor;
import com.gelox.backend.entities.Producto;
import com.gelox.backend.entities.RolUsuario;
import com.gelox.backend.entities.Usuario;
import com.gelox.backend.repositories.PedidoProveedorRepository;
import com.gelox.backend.repositories.ProductoRepository;
import com.gelox.backend.services.EventoSistemaService;
import com.gelox.backend.services.PedidoProveedorService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * RF53 — PedidoProveedorService.modificarPedidoPendiente.
 */
@ExtendWith(MockitoExtension.class)
class PedidoProveedorModificarTest {

    @Mock
    PedidoProveedorRepository pedidoRepo;

    @Mock
    ProductoRepository productoRepo;

    @Mock
    EventoSistemaService eventoService;

    PedidoProveedorService service;

    Usuario usuario;
    Producto festival;
    Producto soloLack;

    @BeforeEach
    void setUp() {
        service = new PedidoProveedorService(pedidoRepo, null, productoRepo, null, null, eventoService);
        ReflectionTestUtils.setField(service, "plantillaExcel", new ClassPathResource("templates/catalogo.xlsx"));

        usuario = TestHelper.buildUsuario("uid-1", "voz@gelox-test.com", RolUsuario.ADMINISTRADOR, true);

        festival = new Producto();
        festival.setId(UUID.randomUUID());
        festival.setCodigoTecnico("FEST-001");
        festival.setNombre("Festival");
        festival.setPrecioCosto(BigDecimal.valueOf(1000));

        soloLack = new Producto();
        soloLack.setId(UUID.randomUUID());
        soloLack.setCodigoTecnico("SL-001");
        soloLack.setNombre("Solo Lack");
        soloLack.setPrecioCosto(BigDecimal.valueOf(2000));

        lenient().when(pedidoRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private PedidoProveedor pedidoPendienteConFestival(int cajasExistentes) {
        PedidoProveedor pedido = PedidoProveedor.builder()
                .id(UUID.randomUUID())
                .fecha(LocalDate.now())
                .estado(EstadoPedido.PENDIENTE)
                .build();

        ItemPedidoProveedor item = ItemPedidoProveedor.builder()
                .pedido(pedido)
                .producto(festival)
                .cantidadCajas(cajasExistentes)
                .cantidadUnidades(0)
                .precioUnitario(festival.getPrecioCosto())
                .build();

        pedido.setItems(new ArrayList<>(List.of(item)));
        return pedido;
    }

    @Test
    @DisplayName("AGREGAR suma a un ítem existente")
    void agregar_sumaAUnoExistente() {
        PedidoProveedor pedido = pedidoPendienteConFestival(15);
        when(pedidoRepo.findByIdWithItems(pedido.getId())).thenReturn(Optional.of(pedido));
        when(productoRepo.findById(festival.getId())).thenReturn(Optional.of(festival));

        ModificarPedidoRequest req = new ModificarPedidoRequest(AccionPedido.AGREGAR, festival.getId(), 10, 0);
        service.modificarPedidoPendiente(pedido.getId(), req, usuario);

        assertThat(pedido.getItems()).hasSize(1);
        assertThat(pedido.getItems().get(0).getCantidadCajas()).isEqualTo(25);
    }

    @Test
    @DisplayName("AGREGAR un producto nuevo crea el ítem")
    void agregar_productoNuevo() {
        PedidoProveedor pedido = pedidoPendienteConFestival(15);
        when(pedidoRepo.findByIdWithItems(pedido.getId())).thenReturn(Optional.of(pedido));
        when(productoRepo.findById(soloLack.getId())).thenReturn(Optional.of(soloLack));

        ModificarPedidoRequest req = new ModificarPedidoRequest(AccionPedido.AGREGAR, soloLack.getId(), 20, 0);
        service.modificarPedidoPendiente(pedido.getId(), req, usuario);

        assertThat(pedido.getItems()).hasSize(2);
        ItemPedidoProveedor nuevo = pedido.getItems().stream()
                .filter(i -> i.getProducto().getId().equals(soloLack.getId()))
                .findFirst().orElseThrow();
        assertThat(nuevo.getCantidadCajas()).isEqualTo(20);
        assertThat(nuevo.getCantidadRecibida()).isEqualTo(0);
        assertThat(nuevo.getPrecioUnitario()).isEqualByComparingTo(soloLack.getPrecioCosto());
    }

    @Test
    @DisplayName("ELIMINAR el último ítem responde 409")
    void eliminar_ultimoItem_409() {
        PedidoProveedor pedido = pedidoPendienteConFestival(15);
        when(pedidoRepo.findByIdWithItems(pedido.getId())).thenReturn(Optional.of(pedido));
        when(productoRepo.findById(festival.getId())).thenReturn(Optional.of(festival));

        ModificarPedidoRequest req = new ModificarPedidoRequest(AccionPedido.ELIMINAR, festival.getId(), null, null);

        assertThatThrownBy(() -> service.modificarPedidoPendiente(pedido.getId(), req, usuario))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode().value()).isEqualTo(409));
    }

    @Test
    @DisplayName("ACTUALIZAR un pedido RECIBIDO responde 409")
    void actualizar_pedidoRecibido_409() {
        PedidoProveedor pedido = pedidoPendienteConFestival(15);
        pedido.setEstado(EstadoPedido.RECIBIDO);
        when(pedidoRepo.findByIdWithItems(pedido.getId())).thenReturn(Optional.of(pedido));

        ModificarPedidoRequest req = new ModificarPedidoRequest(AccionPedido.ACTUALIZAR, festival.getId(), 5, 0);

        assertThatThrownBy(() -> service.modificarPedidoPendiente(pedido.getId(), req, usuario))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode().value()).isEqualTo(409));
    }

    @Test
    @DisplayName("producto que no está en el pedido responde 400 (IllegalArgumentException)")
    void actualizar_productoQueNoEsta_400() {
        PedidoProveedor pedido = pedidoPendienteConFestival(15);
        when(pedidoRepo.findByIdWithItems(pedido.getId())).thenReturn(Optional.of(pedido));
        when(productoRepo.findById(soloLack.getId())).thenReturn(Optional.of(soloLack));

        ModificarPedidoRequest req = new ModificarPedidoRequest(AccionPedido.ACTUALIZAR, soloLack.getId(), 5, 0);

        assertThatThrownBy(() -> service.modificarPedidoPendiente(pedido.getId(), req, usuario))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
