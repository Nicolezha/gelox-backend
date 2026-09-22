package com.gelox.backend.rf53;

import com.gelox.backend.TestHelper;
import com.gelox.backend.dto.AccionPedido;
import com.gelox.backend.dto.ModificarPedidoRequest;
import com.gelox.backend.entities.EstadoPedido;
import com.gelox.backend.entities.ItemPedidoProveedor;
import com.gelox.backend.entities.PedidoProveedor;
import com.gelox.backend.entities.Producto;
import com.gelox.backend.entities.RolUsuario;
import com.gelox.backend.entities.TipoIntencionVoz;
import com.gelox.backend.entities.Usuario;
import com.gelox.backend.repositories.PedidoProveedorRepository;
import com.gelox.backend.services.PedidoProveedorService;
import com.gelox.backend.voz.ResolvedorProducto;
import com.gelox.backend.voz.VozContexto;
import com.gelox.backend.voz.VozPendiente;
import com.gelox.backend.voz.VozResultado;
import com.gelox.backend.voz.handlers.ModificarPedidoHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ModificarPedidoHandlerTest {

    @Mock
    PedidoProveedorRepository pedidoRepository;

    @Mock
    ResolvedorProducto resolvedorProducto;

    @Mock
    PedidoProveedorService pedidoProveedorService;

    ModificarPedidoHandler handler;
    Usuario usuario;
    Producto festival;

    @BeforeEach
    void setUp() {
        handler = new ModificarPedidoHandler(pedidoRepository, resolvedorProducto, pedidoProveedorService);
        usuario = TestHelper.buildUsuario("uid-1", "voz@gelox-test.com", RolUsuario.ADMINISTRADOR, true);

        festival = new Producto();
        festival.setId(UUID.randomUUID());
        festival.setCodigoTecnico("FEST-001");
        festival.setNombre("Festival");
    }

    private VozContexto ctx(String texto) {
        return new VozContexto(texto, Map.of(), 0.9, usuario, LocalDate.now());
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
                .build();

        pedido.setItems(new ArrayList<>(List.of(item)));
        return pedido;
    }

    @Test
    @DisplayName("sin pedidos pendientes: ok=false")
    void sinPedidosPendientes_devuelveOkFalso() {
        when(pedidoRepository.findByEstadoOrderByFechaDesc(EstadoPedido.PENDIENTE)).thenReturn(List.of());

        VozResultado resultado = handler.interpretar(ctx("al pedido pendiente agregale 10 de festival"));

        assertThat(resultado.ok()).isFalse();
        assertThat(resultado.textoRespuesta()).isEqualTo("No hay pedidos pendientes.");
        verifyNoInteractions(resolvedorProducto, pedidoProveedorService);
    }

    @Test
    @DisplayName("producto ausente del pedido: ok=false y no cambia nada")
    void productoAusenteDelPedido_devuelveOkFalso() {
        PedidoProveedor pedido = pedidoPendienteConFestival(15);
        when(pedidoRepository.findByEstadoOrderByFechaDesc(EstadoPedido.PENDIENTE)).thenReturn(List.of(pedido));

        UUID idSoloLack = UUID.randomUUID();
        when(resolvedorProducto.resolver("solo lack")).thenReturn(List.of(
                new ResolvedorProducto.ProductoCandidato(idSoloLack, "Solo Lack", 1.0, 12)));

        VozResultado resultado = handler.interpretar(ctx("quita de solo lack del pedido pendiente"));

        assertThat(resultado.ok()).isFalse();
        assertThat(resultado.textoRespuesta()).contains("Solo Lack").contains("no está en el pedido");
        verifyNoInteractions(pedidoProveedorService);
    }

    @Test
    @DisplayName("agregar: el resumen trae la cantidadNueva correcta")
    void agregar_elResumenTraeCantidadNuevaCorrecta() {
        PedidoProveedor pedido = pedidoPendienteConFestival(15);
        when(pedidoRepository.findByEstadoOrderByFechaDesc(EstadoPedido.PENDIENTE)).thenReturn(List.of(pedido));
        when(resolvedorProducto.resolver("festival")).thenReturn(List.of(
                new ResolvedorProducto.ProductoCandidato(festival.getId(), "Festival", 1.0, 24)));

        VozResultado resultado = handler.interpretar(ctx("al pedido pendiente agregale 10 de festival"));

        assertThat(resultado.ok()).isTrue();
        assertThat(resultado.textoRespuesta()).contains("10 cajas de Festival").contains("quedarían 25");
        assertThat(resultado.datos()).containsEntry("cantidadAnterior", 15);
        assertThat(resultado.datos()).containsEntry("cantidadNueva", 25);
        assertThat(resultado.datos()).containsEntry("accion", "AGREGAR");
        verifyNoInteractions(pedidoProveedorService);
    }

    @Test
    @DisplayName("eliminar: hace el resumen y no está en el pedido responde ok=false")
    void eliminar_generaResumen() {
        PedidoProveedor pedido = pedidoPendienteConFestival(15);
        when(pedidoRepository.findByEstadoOrderByFechaDesc(EstadoPedido.PENDIENTE)).thenReturn(List.of(pedido));
        when(resolvedorProducto.resolver("festival")).thenReturn(List.of(
                new ResolvedorProducto.ProductoCandidato(festival.getId(), "Festival", 1.0, 24)));

        VozResultado resultado = handler.interpretar(ctx("al pedido pendiente quita de festival"));

        assertThat(resultado.ok()).isTrue();
        assertThat(resultado.textoRespuesta()).contains("se elimina Festival");
        assertThat(resultado.datos()).containsEntry("accion", "ELIMINAR");
        assertThat(resultado.datos()).containsEntry("cantidadNueva", 0);
    }

    @Test
    @DisplayName("actualizar: fija el valor absoluto en el resumen")
    void actualizar_generaResumen() {
        PedidoProveedor pedido = pedidoPendienteConFestival(15);
        when(pedidoRepository.findByEstadoOrderByFechaDesc(EstadoPedido.PENDIENTE)).thenReturn(List.of(pedido));
        when(resolvedorProducto.resolver("festival")).thenReturn(List.of(
                new ResolvedorProducto.ProductoCandidato(festival.getId(), "Festival", 1.0, 24)));

        VozResultado resultado = handler.interpretar(ctx("al pedido pendiente actualiza a 8 unidades de festival"));

        assertThat(resultado.ok()).isTrue();
        assertThat(resultado.textoRespuesta()).contains("8 unidades");
        assertThat(resultado.datos()).containsEntry("accion", "ACTUALIZAR");
        assertThat(resultado.datos()).containsEntry("cantidadNueva", 8);
    }

    @Test
    @DisplayName("ejecutar delega en modificarPedidoPendiente y devuelve exportUrl")
    void ejecutar_delegaEnModificarPedidoPendiente() {
        PedidoProveedor pedido = pedidoPendienteConFestival(15);
        when(pedidoRepository.findByEstadoOrderByFechaDesc(EstadoPedido.PENDIENTE)).thenReturn(List.of(pedido));
        when(resolvedorProducto.resolver("festival")).thenReturn(List.of(
                new ResolvedorProducto.ProductoCandidato(festival.getId(), "Festival", 1.0, 24)));

        // El payload real sale de interpretar(): es privado (Payload), así que se
        // obtiene con una interpretación real en vez de construirlo desde el test.
        VozResultado interpretado = handler.interpretar(ctx("al pedido pendiente agregale 10 de festival"));
        VozPendiente pendiente = new VozPendiente(
                UUID.randomUUID(), usuario.getId(), TipoIntencionVoz.MODIFICAR_PEDIDO,
                interpretado.payload(), Instant.now());

        VozResultado resultado = handler.ejecutar(pendiente, usuario);

        ModificarPedidoRequest esperado = new ModificarPedidoRequest(AccionPedido.AGREGAR, festival.getId(), 10, 0);
        verify(pedidoProveedorService).modificarPedidoPendiente(pedido.getId(), esperado, usuario);
        assertThat(resultado.ok()).isTrue();
        assertThat(resultado.datos()).containsEntry("pedidoId", pedido.getId());
        assertThat(resultado.datos()).containsEntry("exportUrl", "/api/inventario/pedidos/" + pedido.getId() + "/exportar");
    }
}
