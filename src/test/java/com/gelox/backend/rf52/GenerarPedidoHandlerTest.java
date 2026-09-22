package com.gelox.backend.rf52;

import com.gelox.backend.TestHelper;
import com.gelox.backend.dto.CrearPedidoRequest;
import com.gelox.backend.entities.RolUsuario;
import com.gelox.backend.entities.TipoIntencionVoz;
import com.gelox.backend.entities.Usuario;
import com.gelox.backend.services.PedidoProveedorService;
import com.gelox.backend.voz.ResolvedorProducto;
import com.gelox.backend.voz.VozContexto;
import com.gelox.backend.voz.VozPendiente;
import com.gelox.backend.voz.VozResultado;
import com.gelox.backend.voz.handlers.GenerarPedidoHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * RF52 — GenerarPedidoHandler (voz).
 * El bloqueo por rol no se prueba aquí: el aspecto @RequiereRol no corre en
 * un test unitario. Eso se verifica en Postman (T47-PR2).
 */
@ExtendWith(MockitoExtension.class)
class GenerarPedidoHandlerTest {

    @Mock
    ResolvedorProducto resolvedorProducto;

    @Mock
    PedidoProveedorService pedidoProveedorService;

    GenerarPedidoHandler handler;
    Usuario usuario;

    @BeforeEach
    void setUp() {
        handler = new GenerarPedidoHandler(resolvedorProducto, pedidoProveedorService);
        usuario = TestHelper.buildUsuario("uid-1", "voz@gelox-test.com", RolUsuario.ADMINISTRADOR, true);
    }

    private VozContexto ctx(String texto) {
        return new VozContexto(texto, Map.of(), 0.9, usuario, LocalDate.now());
    }

    @Test
    @DisplayName("comando literal del FP-15: devuelve 2 ítems (20 y 15 cajas) y pide confirmación")
    void comandoLiteralFp15_devuelveDosItems() {
        UUID idSoloLack = UUID.randomUUID();
        UUID idFestival = UUID.randomUUID();

        when(resolvedorProducto.resolver("solo lack")).thenReturn(List.of(
                new ResolvedorProducto.ProductoCandidato(idSoloLack, "Solo Lack", 1.0, 12)));
        when(resolvedorProducto.resolver("festival")).thenReturn(List.of(
                new ResolvedorProducto.ProductoCandidato(idFestival, "Festival", 1.0, 24)));

        VozResultado resultado = handler.interpretar(
                ctx("genera un pedido con 20 cajas de Solo Lack y 15 de Festival"));

        assertThat(resultado.ok()).isTrue();
        assertThat(resultado.textoRespuesta())
                .isEqualTo("Pedido: 20 cajas de Solo Lack y 15 cajas de Festival. ¿Confirmas?");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) resultado.datos().get("items");
        assertThat(items).hasSize(2);
        assertThat(items.get(0)).containsEntry("productoId", idSoloLack).containsEntry("cantidadCajas", 20);
        assertThat(items.get(1)).containsEntry("productoId", idFestival).containsEntry("cantidadCajas", 15);

        CrearPedidoRequest payload = (CrearPedidoRequest) resultado.payload();
        assertThat(payload.items()).hasSize(2);
    }

    @Test
    @DisplayName("producto inexistente en el catálogo: ok=false")
    void productoInexistente_devuelveOkFalso() {
        when(resolvedorProducto.resolver("marciano")).thenReturn(List.of());

        VozResultado resultado = handler.interpretar(ctx("genera un pedido con 5 cajas de marciano"));

        assertThat(resultado.ok()).isFalse();
        assertThat(resultado.payload()).isNull();
        verifyNoInteractions(pedidoProveedorService);
    }

    @Test
    @DisplayName("cantidad 0: ok=false")
    void cantidadCero_devuelveOkFalso() {
        when(resolvedorProducto.resolver("festival")).thenReturn(List.of(
                new ResolvedorProducto.ProductoCandidato(UUID.randomUUID(), "Festival", 1.0, 24)));

        VozResultado resultado = handler.interpretar(ctx("genera un pedido con 0 cajas de festival"));

        assertThat(resultado.ok()).isFalse();
        verifyNoInteractions(pedidoProveedorService);
    }

    @Test
    @DisplayName("producto ambiguo: hace una pregunta")
    void productoAmbiguo_haceUnaPregunta() {
        when(resolvedorProducto.resolver("festival")).thenReturn(List.of(
                new ResolvedorProducto.ProductoCandidato(UUID.randomUUID(), "Festival", 1.0, 24),
                new ResolvedorProducto.ProductoCandidato(UUID.randomUUID(), "Festival Mini", 1.0, 24)));

        VozResultado resultado = handler.interpretar(ctx("genera un pedido con 5 cajas de festival"));

        assertThat(resultado.ok()).isFalse();
        assertThat(resultado.textoRespuesta()).endsWith("?");
        assertThat(resultado.payload()).isNull();
        verifyNoInteractions(pedidoProveedorService);
    }

    // ── Casos extra (no forman parte de los 4 exigidos, pero cubren el resto del handler) ──

    @Test
    @DisplayName("Extra: cantidad sin unidad hereda la del ítem anterior")
    void heredaUnidadDelItemAnteriorExtra() {
        UUID idFestival = UUID.randomUUID();
        when(resolvedorProducto.resolver("festival")).thenReturn(List.of(
                new ResolvedorProducto.ProductoCandidato(idFestival, "Festival", 1.0, 24)));

        VozResultado resultado = handler.interpretar(
                ctx("genera un pedido con 8 unidades de festival y 3 de festival"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) resultado.datos().get("items");
        assertThat(items.get(0)).containsEntry("cantidadUnidades", 8).containsEntry("cantidadCajas", 0);
        assertThat(items.get(1)).containsEntry("cantidadUnidades", 3).containsEntry("cantidadCajas", 0);
    }

    @Test
    @DisplayName("Extra: ejecutar delega en PedidoProveedorService y descarta el excel")
    void ejecutarDelegaEnPedidoProveedorServiceExtra() {
        UUID pedidoId = UUID.randomUUID();
        CrearPedidoRequest payload = new CrearPedidoRequest(List.of(), "Generado por voz");
        when(pedidoProveedorService.crearPedido(payload, usuario))
                .thenReturn(Map.of("pedidoId", pedidoId, "excel", new byte[]{1, 2, 3}));

        VozPendiente pendiente = new VozPendiente(
                UUID.randomUUID(), usuario.getId(), TipoIntencionVoz.GENERAR_PEDIDO, payload, Instant.now());

        VozResultado resultado = handler.ejecutar(pendiente, usuario);

        assertThat(resultado.ok()).isTrue();
        assertThat(resultado.datos()).containsEntry("pedidoId", pedidoId);
        assertThat(resultado.datos()).containsEntry("exportUrl", "/api/inventario/pedidos/" + pedidoId + "/exportar");
        verify(pedidoProveedorService).crearPedido(payload, usuario);
    }
}
