package com.gelox.backend.rf47;

import com.gelox.backend.TestHelper;
import com.gelox.backend.dto.CalcularVentaResponse;
import com.gelox.backend.dto.CatalogoVentaDTO;
import com.gelox.backend.dto.ItemCalculoResultado;
import com.gelox.backend.entities.RolUsuario;
import com.gelox.backend.entities.TipoIntencionVoz;
import com.gelox.backend.entities.Usuario;
import com.gelox.backend.services.VentaService;
import com.gelox.backend.ventas.rural.ClienteRuralService;
import com.gelox.backend.ventas.rural.VentaRuralService;
import com.gelox.backend.ventas.rural.dto.ClienteRuralDTO;
import com.gelox.backend.ventas.rural.dto.ConfirmarPedidoRuralRequest;
import com.gelox.backend.ventas.rural.dto.ItemPedidoRuralRequest;
import com.gelox.backend.ventas.rural.dto.PedidoRuralResponse;
import com.gelox.backend.voz.ResolvedorProducto;
import com.gelox.backend.voz.VozContexto;
import com.gelox.backend.voz.VozPendiente;
import com.gelox.backend.voz.VozResultado;
import com.gelox.backend.voz.handlers.RegistrarVentaRuralFlujo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * RF48 — venta rural por voz: destinatario existente, nuevo y ambiguo,
 * envío dicho en palabras y comando sin destinatario.
 */
@ExtendWith(MockitoExtension.class)
class RegistrarVentaRuralFlujoTest {

    /** 2 cajas × 12 unidades × $625 = $15000 en productos. */
    private static final BigDecimal SUBTOTAL_FESTIVAL = new BigDecimal("15000.00");

    @Mock
    ResolvedorProducto resolvedorProducto;

    @Mock
    VentaService ventaService;

    @Mock
    ClienteRuralService clienteRuralService;

    @Mock
    VentaRuralService ventaRuralService;

    RegistrarVentaRuralFlujo flujo;
    Usuario usuario;
    UUID idFestival;

    @BeforeEach
    void setUp() {
        flujo = new RegistrarVentaRuralFlujo(resolvedorProducto, ventaService, clienteRuralService, ventaRuralService);
        usuario = TestHelper.buildUsuario("uid-1", "voz@gelox-test.com", RolUsuario.ENCARGADO_VENTAS, true);
        idFestival = UUID.randomUUID();
    }

    private VozContexto ctx(String texto) {
        return new VozContexto(texto, Map.of(), 0.9, usuario, LocalDate.now());
    }

    private ClienteRuralDTO cliente(String nombre, String corregimiento) {
        return new ClienteRuralDTO(UUID.randomUUID(), nombre, "3001234567", null, null, corregimiento, true);
    }

    /** Festival se resuelve, está en catálogo con stock y calcularVenta da $15000. */
    private void stubFestivalConStock() {
        when(resolvedorProducto.resolver("festival")).thenReturn(List.of(
                new ResolvedorProducto.ProductoCandidato(idFestival, "Festival", 1.0, 12)));
        when(ventaService.getCatalogo()).thenReturn(List.of(new CatalogoVentaDTO(
                idFestival, "FEST-001", "Festival", null, new BigDecimal("625.00"), 100, true, 12)));
        when(ventaService.calcularVenta(any())).thenReturn(new CalcularVentaResponse(
                List.of(new ItemCalculoResultado(idFestival, 2, 0, new BigDecimal("625.00"), SUBTOTAL_FESTIVAL)),
                SUBTOTAL_FESTIVAL));
    }

    @Test
    @DisplayName("destinatario existente (1 coincidencia): usa el cliente y total = productos + envío")
    void destinatarioExistente_usaClienteRural() {
        stubFestivalConStock();
        ClienteRuralDTO marta = cliente("Marta Pérez", "Vereda El Carmen");
        when(clienteRuralService.listarClientes("Marta")).thenReturn(List.of(marta));

        VozResultado resultado = flujo.interpretar(ctx("vende dos cajas de Festival para doña Marta, envío 8.000"));

        assertThat(resultado.ok()).isTrue();
        RegistrarVentaRuralFlujo.Payload payload = (RegistrarVentaRuralFlujo.Payload) resultado.payload();
        assertThat(payload.clienteRuralId()).isEqualTo(marta.id());
        assertThat(payload.nombreDestinatario()).isEqualTo("Marta Pérez");
        assertThat(payload.costoEnvio()).isEqualByComparingTo("8000");
        assertThat(payload.items()).containsExactly(new ItemPedidoRuralRequest(idFestival, 2, 0));

        assertThat(resultado.datos()).containsEntry("canal", "RURAL").containsEntry("destinatario", "Marta Pérez");
        assertThat((BigDecimal) resultado.datos().get("totalProductos")).isEqualByComparingTo("15000");
        assertThat((BigDecimal) resultado.datos().get("total")).isEqualByComparingTo("23000");
        assertThat(resultado.textoRespuesta())
                .isEqualTo("Pedido rural para Marta Pérez: 2 cajas de Festival. Envío $8000. Total $23000. ¿Confirmas?");
    }

    @Test
    @DisplayName("destinatario nuevo (0 coincidencias): sin clienteRuralId y lo indica en el texto")
    void destinatarioNuevo_sinClienteRural() {
        stubFestivalConStock();
        when(clienteRuralService.listarClientes("José Pérez")).thenReturn(List.of());

        VozResultado resultado = flujo.interpretar(ctx("vende dos cajas de Festival para don José Pérez, envío 8.000"));

        assertThat(resultado.ok()).isTrue();
        RegistrarVentaRuralFlujo.Payload payload = (RegistrarVentaRuralFlujo.Payload) resultado.payload();
        assertThat(payload.clienteRuralId()).isNull();
        assertThat(payload.nombreDestinatario()).isEqualTo("José Pérez");
        assertThat(resultado.textoRespuesta()).startsWith("Pedido rural para José Pérez (destinatario nuevo):");
    }

    @Test
    @DisplayName("destinatario ambiguo (2 coincidencias): ok=false, sin payload y lista los clientes")
    void destinatarioAmbiguo_pideNombreCompleto() {
        when(resolvedorProducto.resolver("festival")).thenReturn(List.of(
                new ResolvedorProducto.ProductoCandidato(idFestival, "Festival", 1.0, 12)));
        when(clienteRuralService.listarClientes("Marta")).thenReturn(List.of(
                cliente("Marta Pérez", "Vereda El Carmen"), cliente("Marta Díaz", null)));

        VozResultado resultado = flujo.interpretar(ctx("vende dos cajas de Festival para doña Marta, envío 8.000"));

        assertThat(resultado.ok()).isFalse();
        assertThat(resultado.payload()).isNull();
        assertThat(resultado.textoRespuesta()).isEqualTo(
                "Encontré varios clientes: Marta Pérez (Vereda El Carmen), Marta Díaz. Repite el pedido con el nombre completo.");
        assertThat((List<?>) resultado.datos().get("clientes")).hasSize(2);
        verifyNoInteractions(ventaService, ventaRuralService);
    }

    @Test
    @DisplayName("envío hablado \"ocho mil\": costoEnvio = 8000")
    void envioHablado_ochoMil() {
        stubFestivalConStock();
        when(clienteRuralService.listarClientes("Marta")).thenReturn(List.of(cliente("Marta Pérez", null)));

        VozResultado resultado = flujo.interpretar(ctx("vende dos cajas de Festival para doña Marta envío ocho mil"));

        assertThat(resultado.ok()).isTrue();
        assertThat((BigDecimal) resultado.datos().get("costoEnvio")).isEqualByComparingTo("8000");
        assertThat((BigDecimal) resultado.datos().get("total")).isEqualByComparingTo("23000");
    }

    @Test
    @DisplayName("sin destinatario: pregunta para quién es, guarda el contexto y no consulta nada")
    void sinDestinatario_preguntaParaQuien() {
        VozResultado resultado = flujo.interpretar(ctx("vende dos cajas de Festival, envío 8.000"));

        assertThat(resultado.ok()).isTrue();
        assertThat(resultado.payload()).isEqualTo(new RegistrarVentaRuralFlujo.PendienteDestinatario(
                "vende dos cajas de Festival, envío 8.000"));
        assertThat(resultado.textoRespuesta()).isEqualTo("¿Para quién es el pedido rural?");
        verifyNoInteractions(resolvedorProducto, ventaService, clienteRuralService, ventaRuralService);
    }

    @Test
    @DisplayName("responder solo el nombre completa el pedido que esperaba destinatario")
    void respuestaConNombre_completaElPedido() {
        stubFestivalConStock();
        ClienteRuralDTO marta = cliente("Marta Pérez", "Vereda El Carmen");
        when(clienteRuralService.listarClientes("Marta")).thenReturn(List.of(marta));
        VozPendiente pendiente = new VozPendiente(UUID.randomUUID(), usuario.getId(), TipoIntencionVoz.REGISTRAR_VENTA,
                new RegistrarVentaRuralFlujo.PendienteDestinatario("vende dos cajas de Festival, envío 8.000"),
                Instant.now().plusSeconds(30));

        VozResultado resultado = flujo.interpretar(new VozContexto(
                "doña Marta", Map.of(), 0.9, usuario, LocalDate.now(), pendiente));

        assertThat(resultado.ok()).isTrue();
        assertThat(resultado.payload()).isInstanceOf(RegistrarVentaRuralFlujo.Payload.class);
        assertThat((BigDecimal) resultado.datos().get("costoEnvio")).isEqualByComparingTo("8000");
    }

    @Test
    @DisplayName("sin la palabra cajas/unidades: el error dice qué falta")
    void sinCajasNiUnidades_errorEspecifico() {
        VozResultado resultado = flujo.interpretar(ctx("vende dos de Festival para doña Marta"));

        assertThat(resultado.ok()).isFalse();
        assertThat(resultado.textoRespuesta()).startsWith("Falta decir si son cajas o unidades.");
    }

    @Test
    @DisplayName("ejecutar delega en confirmarPedidoRural con el payload interpretado")
    void ejecutar_delegaEnConfirmarPedidoRural() {
        UUID clienteId = UUID.randomUUID();
        List<ItemPedidoRuralRequest> items = List.of(new ItemPedidoRuralRequest(idFestival, 2, 0));
        RegistrarVentaRuralFlujo.Payload payload =
                new RegistrarVentaRuralFlujo.Payload(clienteId, "Marta Pérez", new BigDecimal("8000"), items);
        VozPendiente pendiente = new VozPendiente(
                UUID.randomUUID(), usuario.getId(), TipoIntencionVoz.REGISTRAR_VENTA, payload, Instant.now());

        UUID ventaId = UUID.randomUUID();
        UUID pedidoRuralId = UUID.randomUUID();
        ConfirmarPedidoRuralRequest esperado = new ConfirmarPedidoRuralRequest(
                clienteId, "Marta Pérez", null, null, null, new BigDecimal("8000"), items);
        when(ventaRuralService.confirmarPedidoRural(esperado, usuario)).thenReturn(new PedidoRuralResponse(
                ventaId, pedidoRuralId, LocalDateTime.now(), new BigDecimal("23000.00"), new BigDecimal("8000"),
                "PENDIENTE", Map.of(), List.of()));

        VozResultado resultado = flujo.ejecutar(pendiente, usuario);

        verify(ventaRuralService).confirmarPedidoRural(esperado, usuario);
        assertThat(resultado.ok()).isTrue();
        assertThat(resultado.textoRespuesta()).isEqualTo("Pedido rural registrado por $23000.");
        assertThat(resultado.datos()).containsEntry("ventaId", ventaId).containsEntry("pedidoRuralId", pedidoRuralId);
    }
}
