package com.gelox.backend.rf55;

import com.gelox.backend.TestHelper;
import com.gelox.backend.entities.AjustePresupuesto;
import com.gelox.backend.entities.DecisionItem;
import com.gelox.backend.entities.EstadoRecomendacion;
import com.gelox.backend.entities.OrigenRecomendacion;
import com.gelox.backend.entities.RecomendacionCompra;
import com.gelox.backend.entities.RolUsuario;
import com.gelox.backend.entities.UrgenciaRecomendacion;
import com.gelox.backend.entities.Usuario;
import com.gelox.backend.recomendacion.DecisionRecomendacionService;
import com.gelox.backend.recomendacion.RecomendacionCompraService;
import com.gelox.backend.recomendacion.dto.DecidirItemRequest;
import com.gelox.backend.recomendacion.dto.ItemDTO;
import com.gelox.backend.recomendacion.dto.PedidoGeneradoDTO;
import com.gelox.backend.recomendacion.dto.RecomendacionDTO;
import com.gelox.backend.recomendacion.dto.RecomendacionGeneradaDTO;
import com.gelox.backend.repositories.RecomendacionCompraRepository;
import com.gelox.backend.security.RequiereRol;
import com.gelox.backend.tenant.TenantContext;
import com.gelox.backend.voz.ResolvedorProducto;
import com.gelox.backend.voz.ResolvedorProducto.ProductoCandidato;
import com.gelox.backend.voz.VozContexto;
import com.gelox.backend.voz.VozPendiente;
import com.gelox.backend.voz.VozResultado;
import com.gelox.backend.voz.handlers.ConsultarRecomendacionVozHandler;
import com.gelox.backend.voz.handlers.RecomendacionVozHandler;
import org.junit.jupiter.api.AfterEach;
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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * T115-PR1 — cada fila de la tabla 2.11.5 del FP-15: respuesta hablada y estado final.
 * Recomendación de ejemplo: 3 productos por $118.720.
 */
@ExtendWith(MockitoExtension.class)
class RecomendacionVozHandlerTest {

    private static final UUID TENANT = UUID.randomUUID();

    @Mock RecomendacionCompraService recomendacionService;
    @Mock DecisionRecomendacionService decisionService;
    @Mock RecomendacionCompraRepository recomendacionRepo;
    @Mock ResolvedorProducto resolvedor;

    RecomendacionVozHandler decidir;
    ConsultarRecomendacionVozHandler consultar;
    Usuario usuario;

    final UUID recomendacionId = UUID.randomUUID();
    final ItemDTO limon = item("Aloha Paleta Limón", 1, UrgenciaRecomendacion.CRITICO, "34940");
    final ItemDTO mango = item("Aloha Mango Biche", 2, UrgenciaRecomendacion.ALERTA, "48840");
    final ItemDTO frutos = item("Aloha Paleta Frutos Rojos Mix", 3, UrgenciaRecomendacion.ESTABLE, "34940");

    @BeforeEach
    void setUp() {
        TenantContext.set(TENANT);
        decidir = new RecomendacionVozHandler(recomendacionService, decisionService, recomendacionRepo, resolvedor);
        consultar = new ConsultarRecomendacionVozHandler(recomendacionService);
        usuario = TestHelper.buildUsuario("uid-1", "admin@gelox-test.com", RolUsuario.ADMINISTRADOR, true);
    }

    @AfterEach
    void limpiar() {
        TenantContext.clear();
    }

    // ── Datos ────────────────────────────────────────────────────────────

    private static ItemDTO item(String nombre, int prioridad, UrgenciaRecomendacion urgencia, String costo) {
        return new ItemDTO(UUID.randomUUID(), UUID.randomUUID(), nombre, prioridad, urgencia, null, null,
                null, null, null, 0, null, 24, 1, AjustePresupuesto.COMPLETO, new BigDecimal(costo), null, null,
                DecisionItem.PENDIENTE, null, null, null);
    }

    private static ItemDTO conDecision(ItemDTO i, DecisionItem decision, Integer cajasAprobadas) {
        return new ItemDTO(i.id(), i.productoId(), i.productoNombre(), i.prioridad(), i.urgencia(), i.score(),
                i.demandaDiaria(), i.factorEstacionalidad(), i.stockSeguridad(), i.puntoReorden(), i.stockActual(),
                i.coberturaDias(), i.unidadesSugeridas(), i.cajasSugeridas(), i.ajustePresupuesto(), i.costoEstimado(),
                i.margenEsperado(), i.justificacion(), decision, cajasAprobadas, i.motivoRechazo(), i.fechaDecision());
    }

    private RecomendacionDTO recomendacion(List<ItemDTO> items, String costoAprobado) {
        return new RecomendacionDTO(recomendacionId, EstadoRecomendacion.GENERADA, OrigenRecomendacion.VOZ,
                new BigDecimal("2000000"), new BigDecimal("118720"), new BigDecimal(costoAprobado), false, 90, 30,
                null, null, null, items);
    }

    private void hayRecomendacion(List<ItemDTO> items, String costoAprobado) {
        when(recomendacionService.recomendacionDelDia()).thenReturn(Optional.of(recomendacion(items, costoAprobado)));
    }

    private void hayRecomendacionPendiente() {
        hayRecomendacion(List.of(limon, mango, frutos), "0");
    }

    private void resuelve(String fragmento, UUID productoId, String nombre) {
        when(resolvedor.resolver(fragmento)).thenReturn(List.of(new ProductoCandidato(productoId, nombre, 0.95, 24)));
    }

    private VozContexto ctx(Map<String, String> slots) {
        return new VozContexto("frase", slots, 0.95, usuario, LocalDate.now());
    }

    private VozResultado confirmar(VozResultado interpretado) {
        VozPendiente pendiente = new VozPendiente(UUID.randomUUID(), usuario.getId(),
                decidir.tipo(), interpretado.payload(), Instant.now().plusSeconds(15));
        return decidir.ejecutar(pendiente, usuario);
    }

    // ── CONSULTAR_RECOMENDACION ──────────────────────────────────────────

    @Test
    @DisplayName("«¿Qué me recomiendas pedir con dos millones?» → genera con origen VOZ y narra los 3 primeros")
    void consultar_conPresupuesto_generaYNarra() {
        when(recomendacionService.recomendacionDelDia()).thenReturn(Optional.empty());
        when(recomendacionService.generar(new BigDecimal("2000000"), OrigenRecomendacion.VOZ, usuario))
                .thenReturn(new RecomendacionGeneradaDTO(recomendacionId, EstadoRecomendacion.GENERADA,
                        OrigenRecomendacion.VOZ, new BigDecimal("2000000"), new BigDecimal("118720"), BigDecimal.ZERO,
                        false, 90, 30, null, List.of(limon, mango, frutos), List.of()));

        VozResultado r = consultar.interpretar(ctx(Map.of("presupuesto", "2000000")));

        assertThat(r.ok()).isTrue();
        assertThat(consultar.requiereConfirmacion()).isFalse();
        assertThat(r.textoRespuesta()).isEqualTo("Te recomiendo 3 productos por $118.720. "
                + "Primero, crítico: Aloha Paleta Limón, 1 caja. "
                + "Segundo, alerta: Aloha Mango Biche, 1 caja. "
                + "Tercero, estable: Aloha Paleta Frutos Rojos Mix, 1 caja.");
    }

    @Test
    @DisplayName("con recomendación de hoy no genera otra: la narra")
    void consultar_conRecomendacionDeHoy_noGenera() {
        hayRecomendacionPendiente();

        VozResultado r = consultar.interpretar(ctx(Map.of("presupuesto", "500000")));

        assertThat(r.textoRespuesta()).startsWith("Te recomiendo 3 productos por $118.720.");
        verify(recomendacionService, never()).generar(any(), any(), any());
    }

    @Test
    @DisplayName("«¿Qué me recomiendas pedir?» sin recomendación de hoy → pide el presupuesto (INACTIVO)")
    void consultar_sinPresupuestoNiRecomendacion() {
        when(recomendacionService.recomendacionDelDia()).thenReturn(Optional.empty());

        VozResultado r = consultar.interpretar(ctx(Map.of()));

        assertThat(r.ok()).isFalse();
        assertThat(r.textoRespuesta()).isEqualTo("No hay una recomendación de hoy. Dime con cuánto presupuesto la genero.");
    }

    // ── DECIDIR_RECOMENDACION ────────────────────────────────────────────

    @Test
    @DisplayName("«Aprueba el Mango Biche» → confirma con cajas y costo, y aprueba")
    void aprobar() {
        hayRecomendacionPendiente();
        resuelve("mango biche", mango.productoId(), mango.productoNombre());

        VozResultado r = decidir.interpretar(ctx(Map.of("accion", "APROBAR", "producto", "mango biche")));

        assertThat(decidir.requiereConfirmacion()).isTrue();
        assertThat(r.textoRespuesta()).isEqualTo("Aprobar Aloha Mango Biche, 1 caja por $48.840. ¿Confirmas?");
        assertThat(confirmar(r).textoRespuesta()).isEqualTo("Listo, aprobado.");
        verify(decisionService).decidir(recomendacionId, mango.id(), new DecidirItemRequest(DecisionItem.APROBADO, null, null));
    }

    @Test
    @DisplayName("«Quita el Frutos Rojos» → rechaza")
    void rechazar() {
        hayRecomendacionPendiente();
        resuelve("frutos rojos", frutos.productoId(), frutos.productoNombre());

        VozResultado r = decidir.interpretar(ctx(Map.of("accion", "RECHAZAR", "producto", "frutos rojos")));

        assertThat(r.textoRespuesta()).isEqualTo("Rechazar Aloha Paleta Frutos Rojos Mix. ¿Confirmas?");
        assertThat(confirmar(r).textoRespuesta()).isEqualTo("Listo, rechazado.");
        ArgumentCaptor<DecidirItemRequest> req = ArgumentCaptor.forClass(DecidirItemRequest.class);
        verify(decisionService).decidir(eq(recomendacionId), eq(frutos.id()), req.capture());
        assertThat(req.getValue().decision()).isEqualTo(DecisionItem.RECHAZADO);
    }

    @Test
    @DisplayName("«Cambia el Mango Biche a 3 cajas» → modifica")
    void modificar() {
        hayRecomendacionPendiente();
        resuelve("mango biche", mango.productoId(), mango.productoNombre());

        VozResultado r = decidir.interpretar(ctx(Map.of("accion", "MODIFICAR", "producto", "mango biche", "cantidad", "3")));

        assertThat(r.textoRespuesta()).isEqualTo("Cambiar Aloha Mango Biche de 1 a 3 cajas. ¿Confirmas?");
        assertThat(confirmar(r).textoRespuesta()).isEqualTo("Listo, modificado.");
        verify(decisionService).decidir(recomendacionId, mango.id(), new DecidirItemRequest(DecisionItem.MODIFICADO, 3, null));
    }

    @Test
    @DisplayName("modificar a 0 cajas se registra como rechazo")
    void modificarACero_esRechazo() {
        hayRecomendacionPendiente();
        resuelve("mango biche", mango.productoId(), mango.productoNombre());

        VozResultado r = decidir.interpretar(ctx(Map.of("accion", "MODIFICAR", "producto", "mango biche", "cantidad", "0")));

        assertThat(confirmar(r).textoRespuesta()).isEqualTo("Listo, rechazado.");
        verify(decisionService).decidir(recomendacionId, mango.id(), new DecidirItemRequest(DecisionItem.MODIFICADO, 0, null));
    }

    @Test
    @DisplayName("«Aprueba todo» → aprueba los pendientes")
    void aprobarTodo() {
        hayRecomendacionPendiente();

        VozResultado r = decidir.interpretar(ctx(Map.of("accion", "APROBAR_TODO")));

        assertThat(r.textoRespuesta()).isEqualTo("Aprobar los 3 productos por $118.720. ¿Confirmas?");
        confirmar(r);
        verify(decisionService).aprobarTodo(recomendacionId);
    }

    @Test
    @DisplayName("«Genera el pedido» → genera con lo aprobado y devuelve pedidoId y exportUrl")
    void generarPedido() {
        hayRecomendacion(List.of(conDecision(limon, DecisionItem.APROBADO, 1), conDecision(mango, DecisionItem.APROBADO, 1),
                conDecision(frutos, DecisionItem.APROBADO, 1)), "118720");
        UUID pedidoId = UUID.randomUUID();
        when(decisionService.generarPedido(recomendacionId, usuario))
                .thenReturn(new PedidoGeneradoDTO(pedidoId, "/api/inventario/pedidos/" + pedidoId + "/exportar"));

        VozResultado r = decidir.interpretar(ctx(Map.of("accion", "GENERAR_PEDIDO")));

        assertThat(r.textoRespuesta()).isEqualTo("Generar el pedido con 3 productos aprobados por $118.720. ¿Confirmas?");
        VozResultado ejecutado = confirmar(r);
        assertThat(ejecutado.textoRespuesta()).isEqualTo("Pedido registrado. Ya puedes descargar el archivo.");
        assertThat(ejecutado.datos()).containsEntry("pedidoId", pedidoId)
                .containsEntry("exportUrl", "/api/inventario/pedidos/" + pedidoId + "/exportar");
    }

    @Test
    @DisplayName("«Aprueba el Festival» (no está en la recomendación) → INACTIVO")
    void productoFueraDeLaRecomendacion() {
        hayRecomendacionPendiente();
        resuelve("festival", UUID.randomUUID(), "Festival");

        VozResultado r = decidir.interpretar(ctx(Map.of("accion", "APROBAR", "producto", "festival")));

        assertThat(r.ok()).isFalse();
        assertThat(r.textoRespuesta()).isEqualTo("Ese producto no está en la recomendación de hoy.");
    }

    @Test
    @DisplayName("«Genera el pedido» sin ítems aprobados → INACTIVO")
    void generarPedidoSinAprobados() {
        hayRecomendacionPendiente();

        VozResultado r = decidir.interpretar(ctx(Map.of("accion", "GENERAR_PEDIDO")));

        assertThat(r.ok()).isFalse();
        assertThat(r.textoRespuesta()).isEqualTo("Aprueba al menos un producto antes de generar el pedido.");
    }

    @Test
    @DisplayName("decisión sobre una recomendación ya convertida en pedido → INACTIVO")
    void recomendacionYaConvertida() {
        when(recomendacionService.recomendacionDelDia()).thenReturn(Optional.empty());
        when(recomendacionRepo.buscarUltima(eq(TENANT), eq(List.of(EstadoRecomendacion.PEDIDO_GENERADO)), any(), any()))
                .thenReturn(Optional.of(new RecomendacionCompra()));

        VozResultado r = decidir.interpretar(ctx(Map.of("accion", "APROBAR", "producto", "mango biche")));

        assertThat(r.ok()).isFalse();
        assertThat(r.textoRespuesta()).isEqualTo("La recomendación ya se convirtió en pedido; no se puede modificar.");
    }

    @Test
    @DisplayName("solo Gerente/Administrador y Encargado de Inventarios (un Encargado de Ventas recibe 403)")
    void roles() throws Exception {
        for (Class<?> handler : List.of(RecomendacionVozHandler.class, ConsultarRecomendacionVozHandler.class)) {
            for (String metodo : List.of("interpretar", "ejecutar")) {
                RequiereRol rol = java.util.Arrays.stream(handler.getDeclaredMethods())
                        .filter(m -> m.getName().equals(metodo)).findFirst().orElseThrow()
                        .getAnnotation(RequiereRol.class);
                assertThat(rol.value()).as(handler.getSimpleName() + "." + metodo)
                        .containsExactlyInAnyOrder("ADMINISTRADOR", "ENCARGADO_INVENTARIO");
            }
        }
    }
}
