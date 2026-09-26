package com.gelox.backend.rf47;

import com.gelox.backend.TestHelper;
import com.gelox.backend.dto.CalcularVentaRequest;
import com.gelox.backend.dto.CalcularVentaResponse;
import com.gelox.backend.dto.CatalogoVentaDTO;
import com.gelox.backend.dto.ConfirmarVentaRequest;
import com.gelox.backend.dto.ConfirmarVentaResponse;
import com.gelox.backend.dto.ItemCalculoResultado;
import com.gelox.backend.dto.ItemVentaRequest;
import com.gelox.backend.entities.CanalVenta;
import com.gelox.backend.entities.MetodoPago;
import com.gelox.backend.entities.RolUsuario;
import com.gelox.backend.entities.TipoIntencionVoz;
import com.gelox.backend.entities.Usuario;
import com.gelox.backend.services.VentaService;
import com.gelox.backend.voz.ResolvedorProducto;
import com.gelox.backend.voz.VozContexto;
import com.gelox.backend.voz.VozPendiente;
import com.gelox.backend.voz.VozResultado;
import com.gelox.backend.voz.handlers.RegistrarVentaHandler;
import com.gelox.backend.voz.handlers.PendienteProducto;
import com.gelox.backend.voz.handlers.RegistrarVentaRuralFlujo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * RF47 — registrar venta de ventanilla por voz: comando literal del FP-15,
 * stock insuficiente, agregado secuencial y precio distinto al de catálogo.
 * Festival y Solo Lack se venden por caja de 1 unidad para que los montos
 * se lean directo: 3 cajas × $2500 = $7500.
 */
@ExtendWith(MockitoExtension.class)
class RegistrarVentaHandlerTest {

    private static final String COMANDO_FP15 = "registra tres cajas de Festival a 2.500, canal ventanilla";

    @Mock
    ResolvedorProducto resolvedorProducto;

    @Mock
    VentaService ventaService;

    @Mock
    RegistrarVentaRuralFlujo registrarVentaRuralFlujo;

    RegistrarVentaHandler handler;
    Usuario usuario;
    UUID idFestival;
    UUID idSoloLack;

    @BeforeEach
    void setUp() {
        handler = new RegistrarVentaHandler(resolvedorProducto, ventaService, registrarVentaRuralFlujo);
        usuario = TestHelper.buildUsuario("uid-1", "voz@gelox-test.com", RolUsuario.ENCARGADO_VENTAS, true);
        idFestival = UUID.randomUUID();
        idSoloLack = UUID.randomUUID();
    }

    private VozContexto ctx(String texto) {
        return new VozContexto(texto, Map.of(), 0.9, usuario, LocalDate.now());
    }

    private VozContexto ctxConPendiente(String texto, Object payloadAnterior) {
        VozPendiente pendiente = new VozPendiente(UUID.randomUUID(), usuario.getId(),
                TipoIntencionVoz.REGISTRAR_VENTA, payloadAnterior, Instant.now().plusSeconds(15));
        return new VozContexto(texto, Map.of(), 0.9, usuario, LocalDate.now(), pendiente);
    }

    private void resuelve(String fragmento, UUID id, String nombre) {
        when(resolvedorProducto.resolver(fragmento)).thenReturn(List.of(
                new ResolvedorProducto.ProductoCandidato(id, nombre, 1.0, 1)));
    }

    private CatalogoVentaDTO enCatalogo(UUID id, String nombre, String precio, int stock) {
        return new CatalogoVentaDTO(id, nombre.toUpperCase(), nombre, null, new BigDecimal(precio), stock, stock > 0, 1);
    }

    private ItemCalculoResultado calculado(UUID id, int cajas, String precio, String subtotal) {
        return new ItemCalculoResultado(id, cajas, 0, new BigDecimal(precio), new BigDecimal(subtotal));
    }

    @Test
    @DisplayName("comando literal del FP-15: ventanilla, Festival, 3 cajas, efectivo y total $7500")
    void comandoLiteralFp15_resumenConTotal() {
        resuelve("festival", idFestival, "Festival");
        when(ventaService.getCatalogo()).thenReturn(List.of(enCatalogo(idFestival, "Festival", "2500.00", 50)));
        when(ventaService.calcularVenta(any())).thenReturn(new CalcularVentaResponse(
                List.of(calculado(idFestival, 3, "2500.00", "7500.00")), new BigDecimal("7500.00")));

        VozResultado resultado = handler.interpretar(ctx(COMANDO_FP15));

        assertThat(resultado.ok()).isTrue();
        assertThat(resultado.payload()).isNotNull();
        assertThat(resultado.textoRespuesta())
                .isEqualTo("Venta ventanilla: 3 cajas de Festival. Total $7500. Pago: efectivo. ¿Confirmas?");
        assertThat(resultado.datos()).containsEntry("canal", "VENTANILLA").containsEntry("metodoPago", "EFECTIVO");
        assertThat((BigDecimal) resultado.datos().get("total")).isEqualByComparingTo("7500");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) resultado.datos().get("items");
        assertThat(items).hasSize(1);
        assertThat(items.get(0)).containsEntry("productoId", idFestival).containsEntry("cajas", 3)
                .containsEntry("unidades", 0);
        verifyNoInteractions(registrarVentaRuralFlujo);
    }

    @Test
    @DisplayName("stock insuficiente: ok=false con disponible/solicitado y sin payload (no queda pendiente)")
    void stockInsuficiente_okFalsoSinPayload() {
        resuelve("festival", idFestival, "Festival");
        when(ventaService.getCatalogo()).thenReturn(List.of(enCatalogo(idFestival, "Festival", "2500.00", 2)));

        VozResultado resultado = handler.interpretar(ctx(COMANDO_FP15));

        assertThat(resultado.ok()).isFalse();
        assertThat(resultado.payload()).isNull();
        assertThat(resultado.textoRespuesta()).isEqualTo("Stock insuficiente para Festival. Disponible: 2, solicitado: 3");
        verify(ventaService, never()).calcularVenta(any());
    }

    @Test
    @DisplayName("agregado secuencial: \"agrega 2 cajas de Solo Lack\" suma al pendiente y recalcula con ambos")
    void agregadoSecuencial_unSoloResumenConAmbosProductos() {
        resuelve("festival", idFestival, "Festival");
        resuelve("solo lack", idSoloLack, "Solo Lack");
        when(ventaService.getCatalogo()).thenReturn(List.of(
                enCatalogo(idFestival, "Festival", "2500.00", 50),
                enCatalogo(idSoloLack, "Solo Lack", "2000.00", 50)));
        when(ventaService.calcularVenta(any())).thenReturn(
                new CalcularVentaResponse(List.of(calculado(idFestival, 3, "2500.00", "7500.00")),
                        new BigDecimal("7500.00")),
                new CalcularVentaResponse(List.of(
                        calculado(idFestival, 3, "2500.00", "7500.00"),
                        calculado(idSoloLack, 2, "2000.00", "4000.00")), new BigDecimal("11500.00")));

        VozResultado primero = handler.interpretar(ctx("registra 3 cajas de Festival"));
        VozResultado agregado = handler.interpretar(ctxConPendiente("agrega 2 cajas de Solo Lack", primero.payload()));

        assertThat(agregado.ok()).isTrue();
        assertThat(agregado.textoRespuesta()).isEqualTo(
                "Venta ventanilla: 3 cajas de Festival y 2 cajas de Solo Lack. Total $11500. Pago: efectivo. ¿Confirmas?");
        assertThat((BigDecimal) agregado.datos().get("total")).isEqualByComparingTo("11500");

        ArgumentCaptor<CalcularVentaRequest> calculos = ArgumentCaptor.forClass(CalcularVentaRequest.class);
        verify(ventaService, times(2)).calcularVenta(calculos.capture());
        assertThat(calculos.getAllValues().get(1).items())
                .extracting(i -> i.productoId() + ":" + i.cajas())
                .containsExactly(idFestival + ":3", idSoloLack + ":2");

        // Confirmar el pendiente acumulado registra una sola venta con los dos productos.
        when(ventaService.confirmarVenta(any(), any())).thenReturn(new ConfirmarVentaResponse(
                UUID.randomUUID(), "VENTANILLA", LocalDateTime.now(), "CONFIRMADA", List.of(),
                new BigDecimal("11500.00"), "EFECTIVO"));
        handler.ejecutar(new VozPendiente(UUID.randomUUID(), usuario.getId(), TipoIntencionVoz.REGISTRAR_VENTA,
                agregado.payload(), Instant.now().plusSeconds(15)), usuario);

        verify(ventaService).confirmarVenta(new ConfirmarVentaRequest(CanalVenta.VENTANILLA, MetodoPago.EFECTIVO,
                List.of(new ItemVentaRequest(idFestival, 3, 0), new ItemVentaRequest(idSoloLack, 2, 0))), usuario);
    }

    @Test
    @DisplayName("el precio se acepta con 'de', 'por' y '$': no se traga como parte del producto")
    void precioConVariantes_noSeTragaEnElProducto() {
        resuelve("festival", idFestival, "Festival");
        when(ventaService.getCatalogo()).thenReturn(List.of(enCatalogo(idFestival, "Festival", "2500.00", 50)));
        when(ventaService.calcularVenta(any())).thenReturn(new CalcularVentaResponse(
                List.of(calculado(idFestival, 3, "2500.00", "7500.00")), new BigDecimal("7500.00")));

        for (String precio : List.of("de 3.000", "por 3.000", "$3.000", "a $3.000")) {
            VozResultado resultado = handler.interpretar(ctx("registra tres cajas de Festival " + precio));
            assertThat(resultado.ok()).as(precio).isTrue();
        }
    }

    @Test
    @DisplayName("producto ambiguo: pregunta cuál, guarda el contexto y la respuesta 'fresa' completa la venta")
    void productoAmbiguo_guardaContextoYSeResuelveConLaRespuesta() {
        UUID idFresa = UUID.randomUUID();
        UUID idLimon = UUID.randomUUID();
        when(resolvedorProducto.resolver("aloha paleta")).thenReturn(List.of(
                new ResolvedorProducto.ProductoCandidato(idFresa, "Aloha Paleta Fresa", 0.9, 1),
                new ResolvedorProducto.ProductoCandidato(idLimon, "Aloha Paleta Limon", 0.9, 1)));

        VozResultado pregunta = handler.interpretar(ctx("Registra 3 cajas de Aloha Paleta"));

        assertThat(pregunta.ok()).isTrue();
        assertThat(pregunta.textoRespuesta()).isEqualTo("¿Aloha Paleta Fresa o Aloha Paleta Limon?");
        assertThat(pregunta.datos()).containsEntry("requiereAclaracion", true);
        assertThat(pregunta.payload()).isEqualTo(
                new PendienteProducto("Registra 3 cajas de Aloha Paleta", "aloha paleta", false, null));

        resuelve("aloha paleta fresa", idFresa, "Aloha Paleta Fresa");
        when(ventaService.getCatalogo()).thenReturn(List.of(enCatalogo(idFresa, "Aloha Paleta Fresa", "2500.00", 50)));
        when(ventaService.calcularVenta(any())).thenReturn(new CalcularVentaResponse(
                List.of(calculado(idFresa, 3, "2500.00", "7500.00")), new BigDecimal("7500.00")));

        VozResultado resuelto = handler.interpretar(ctxConPendiente("Fresa", pregunta.payload()));

        assertThat(resuelto.ok()).isTrue();
        assertThat(resuelto.textoRespuesta()).contains("3 cajas de Aloha Paleta Fresa");
    }

    @Test
    @DisplayName("PendienteProducto: completa con nombre parcial o completo, con tildes y mayúsculas del original")
    void pendienteProducto_completar() {
        PendienteProducto p = new PendienteProducto("Vende 2 cajas de Paleta Limón para Marta", "paleta limon", true, null);

        assertThat(p.completar("Fresa")).isEqualTo("Vende 2 cajas de paleta limon Fresa para Marta");
        assertThat(p.completar("paleta limon fresa.")).isEqualTo("Vende 2 cajas de paleta limon fresa para Marta");
    }

    @Test
    @DisplayName("sin cantidad, sin cajas/unidades o sin producto: el error dice qué falta")
    void erroresEspecificosSegunLoQueFalta() {
        assertThat(handler.interpretar(ctx("registra cajas de Festival")).textoRespuesta())
                .startsWith("No entendí la cantidad.");
        assertThat(handler.interpretar(ctx("registra tres de Festival")).textoRespuesta())
                .startsWith("Falta decir si son cajas o unidades.");
        assertThat(handler.interpretar(ctx("registra tres cajas")).textoRespuesta())
                .startsWith("Falta el producto.");
    }

    @Test
    @DisplayName("precio distinto al catálogo: avisa y usa el de catálogo")
    void precioDistintoAlCatalogo_avisaYUsaCatalogo() {
        resuelve("festival", idFestival, "Festival");
        when(ventaService.getCatalogo()).thenReturn(List.of(enCatalogo(idFestival, "Festival", "2500.00", 50)));
        when(ventaService.calcularVenta(any())).thenReturn(new CalcularVentaResponse(
                List.of(calculado(idFestival, 3, "2500.00", "7500.00")), new BigDecimal("7500.00")));

        VozResultado resultado = handler.interpretar(ctx("registra tres cajas de Festival a 3.000, canal ventanilla"));

        assertThat(resultado.ok()).isTrue();
        assertThat(resultado.textoRespuesta())
                .contains("Total $7500.")
                .contains("El precio de catálogo de Festival es 2500 y se usará ese.");
        assertThat((BigDecimal) resultado.datos().get("total")).isEqualByComparingTo("7500");
    }

    @Test
    @DisplayName("ejecutar: confirma la venta y responde con el total")
    void ejecutar_confirmaVenta() {
        resuelve("festival", idFestival, "Festival");
        when(ventaService.getCatalogo()).thenReturn(List.of(enCatalogo(idFestival, "Festival", "2500.00", 50)));
        when(ventaService.calcularVenta(any())).thenReturn(new CalcularVentaResponse(
                List.of(calculado(idFestival, 3, "2500.00", "7500.00")), new BigDecimal("7500.00")));
        VozResultado interpretado = handler.interpretar(ctx(COMANDO_FP15));

        UUID ventaId = UUID.randomUUID();
        ConfirmarVentaRequest esperado = new ConfirmarVentaRequest(CanalVenta.VENTANILLA, MetodoPago.EFECTIVO,
                List.of(new ItemVentaRequest(idFestival, 3, 0)));
        when(ventaService.confirmarVenta(esperado, usuario)).thenReturn(new ConfirmarVentaResponse(
                ventaId, "VENTANILLA", LocalDateTime.now(), "CONFIRMADA", List.of(), new BigDecimal("7500.00"), "EFECTIVO"));

        VozResultado resultado = handler.ejecutar(new VozPendiente(UUID.randomUUID(), usuario.getId(),
                TipoIntencionVoz.REGISTRAR_VENTA, interpretado.payload(), Instant.now().plusSeconds(15)), usuario);

        assertThat(resultado.ok()).isTrue();
        assertThat(resultado.textoRespuesta()).isEqualTo("Venta registrada por $7500.");
        assertThat(resultado.datos()).containsEntry("ventaId", ventaId);
    }
}
