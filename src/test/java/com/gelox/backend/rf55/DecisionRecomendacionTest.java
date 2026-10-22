package com.gelox.backend.rf55;

import com.gelox.backend.entities.AjustePresupuesto;
import com.gelox.backend.entities.DecisionItem;
import com.gelox.backend.entities.EstadoRecomendacion;
import com.gelox.backend.entities.ItemRecomendacion;
import com.gelox.backend.entities.RecomendacionCompra;
import com.gelox.backend.recomendacion.DecisionRecomendacionService;
import com.gelox.backend.recomendacion.dto.DecidirItemRequest;
import com.gelox.backend.recomendacion.dto.ItemDTO;
import com.gelox.backend.recomendacion.dto.RecomendacionDTO;
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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import static com.gelox.backend.recomendacion.RecomendacionTestData.TENANT;
import static com.gelox.backend.recomendacion.RecomendacionTestData.item;
import static com.gelox.backend.recomendacion.RecomendacionTestData.producto;
import static com.gelox.backend.recomendacion.RecomendacionTestData.recomendacion;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * RF55 — DecisionRecomendacionService.decidir y aprobarTodo.
 */
@ExtendWith(MockitoExtension.class)
class DecisionRecomendacionTest {

    @Mock RecomendacionCompraRepository recomendacionRepo;
    @Mock PedidoProveedorRepository pedidoRepo;
    @Mock ItemPedidoProveedorRepository itemPedidoRepo;
    @Mock PedidoProveedorService pedidoService;
    @Mock EventoSistemaService eventoService;

    DecisionRecomendacionService service;

    ItemRecomendacion festival;   // 10 cajas sugeridas, $1000
    ItemRecomendacion soloLack;   // 5 cajas sugeridas, $500
    ItemRecomendacion agotado;    // EXCLUIDO: 0 cajas sugeridas
    RecomendacionCompra recomendacion;

    @BeforeEach
    void setUp() {
        TenantContext.set(TENANT);
        service = new DecisionRecomendacionService(recomendacionRepo, pedidoRepo, itemPedidoRepo,
                pedidoService, eventoService);
        lenient().when(recomendacionRepo.save(any())).thenAnswer(i -> i.getArgument(0));

        festival = item(producto("Festival", 12), 1, 10, "1000.00");
        soloLack = item(producto("Solo Lack", 12), 2, 5, "500.00");
        agotado = item(producto("Aloha Mango", 12), 3, 0, "0.00", AjustePresupuesto.EXCLUIDO);
        recomendacion = recomendacion(TENANT, EstadoRecomendacion.GENERADA, festival, soloLack, agotado);
        lenient().when(recomendacionRepo.findById(recomendacion.getId())).thenReturn(Optional.of(recomendacion));
    }

    @AfterEach
    void limpiarTenant() {
        TenantContext.clear();
    }

    private ItemDTO decidir(ItemRecomendacion item, DecidirItemRequest req) {
        return service.decidir(recomendacion.getId(), item.getId(), req);
    }

    // ---- Las tres decisiones ----

    @Test
    @DisplayName("APROBADO: aprueba las cajas sugeridas, suma el costo y deja EN_REVISION")
    void aprobar() {
        ItemDTO dto = decidir(festival, new DecidirItemRequest(DecisionItem.APROBADO, null, null));

        assertThat(dto.decision()).isEqualTo(DecisionItem.APROBADO);
        assertThat(dto.cajasAprobadas()).isEqualTo(10);
        assertThat(recomendacion.getCostoAprobado()).isEqualByComparingTo("1000.00");
        assertThat(recomendacion.getEstado()).isEqualTo(EstadoRecomendacion.EN_REVISION);
    }

    @Test
    @DisplayName("MODIFICADO: usa las cajas indicadas y el costo aprobado es proporcional")
    void modificar() {
        ItemDTO dto = decidir(festival, new DecidirItemRequest(DecisionItem.MODIFICADO, 4, null));

        assertThat(dto.decision()).isEqualTo(DecisionItem.MODIFICADO);
        assertThat(dto.cajasAprobadas()).isEqualTo(4);
        assertThat(recomendacion.getCostoAprobado()).isEqualByComparingTo("400.00");   // 1000 × 4 / 10
    }

    @Test
    @DisplayName("MODIFICADO con 0 cajas queda RECHAZADO")
    void modificarConCeroCajasRechaza() {
        ItemDTO dto = decidir(festival, new DecidirItemRequest(DecisionItem.MODIFICADO, 0, null));

        assertThat(dto.decision()).isEqualTo(DecisionItem.RECHAZADO);
        assertThat(recomendacion.getCostoAprobado()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("RECHAZADO: el motivo es opcional")
    void rechazar() {
        ItemDTO conMotivo = decidir(festival, new DecidirItemRequest(DecisionItem.RECHAZADO, null, "Hay stock"));
        ItemDTO sinMotivo = decidir(soloLack, new DecidirItemRequest(DecisionItem.RECHAZADO, null, null));

        assertThat(conMotivo.decision()).isEqualTo(DecisionItem.RECHAZADO);
        assertThat(conMotivo.motivoRechazo()).isEqualTo("Hay stock");
        assertThat(sinMotivo.decision()).isEqualTo(DecisionItem.RECHAZADO);
        assertThat(sinMotivo.motivoRechazo()).isNull();
    }

    @Test
    @DisplayName("el costo aprobado se recalcula con cada decisión")
    void costoAprobadoSeRecalcula() {
        decidir(festival, new DecidirItemRequest(DecisionItem.APROBADO, null, null));
        decidir(soloLack, new DecidirItemRequest(DecisionItem.MODIFICADO, 2, null));   // 500 × 2 / 5 = 200
        assertThat(recomendacion.getCostoAprobado()).isEqualByComparingTo("1200.00");

        decidir(festival, new DecidirItemRequest(DecisionItem.RECHAZADO, null, null));
        assertThat(recomendacion.getCostoAprobado()).isEqualByComparingTo("200.00");
    }

    // ---- Validaciones (400) ----

    @Test
    @DisplayName("APROBADO en un ítem EXCLUIDO → 400 «Indica las cajas»")
    void aprobarExcluido() {
        assertThatThrownBy(() -> decidir(agotado, new DecidirItemRequest(DecisionItem.APROBADO, null, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Indica las cajas");
    }

    @Test
    @DisplayName("MODIFICADO sin cajas → 400 «Indica las cajas»")
    void modificarSinCajas() {
        assertThatThrownBy(() -> decidir(festival, new DecidirItemRequest(DecisionItem.MODIFICADO, null, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Indica las cajas");
    }

    @Test
    @DisplayName("PENDIENTE no es una decisión válida")
    void decisionPendiente() {
        assertThatThrownBy(() -> decidir(festival, new DecidirItemRequest(DecisionItem.PENDIENTE, null, null)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ---- Tenant y pertenencia (404) y estado (409) ----

    @Test
    @DisplayName("recomendación de otro tenant → 404")
    void recomendacionDeOtroTenant() {
        RecomendacionCompra ajena = recomendacion(UUID.randomUUID(), EstadoRecomendacion.GENERADA, festival);
        when(recomendacionRepo.findById(ajena.getId())).thenReturn(Optional.of(ajena));

        assertThatThrownBy(() -> service.decidir(ajena.getId(), festival.getId(),
                new DecidirItemRequest(DecisionItem.APROBADO, null, null)))
                .isInstanceOf(NoSuchElementException.class);
        assertThatThrownBy(() -> service.obtener(ajena.getId()))
                .isInstanceOf(NoSuchElementException.class);
        assertThatThrownBy(() -> service.aprobarTodo(ajena.getId()))
                .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    @DisplayName("ítem que no pertenece a la recomendación → 404")
    void itemDeOtraRecomendacion() {
        ItemRecomendacion ajeno = item(producto("Otro", 6), 1, 3, "300.00");

        assertThatThrownBy(() -> service.decidir(recomendacion.getId(), ajeno.getId(),
                new DecidirItemRequest(DecisionItem.APROBADO, null, null)))
                .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    @DisplayName("recomendación inexistente → 404")
    void recomendacionInexistente() {
        UUID id = UUID.randomUUID();
        when(recomendacionRepo.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.obtener(id)).isInstanceOf(NoSuchElementException.class);
    }

    @Test
    @DisplayName("recomendación ya convertida en pedido → 409")
    void yaConvertidaEnPedido() {
        recomendacion.setEstado(EstadoRecomendacion.PEDIDO_GENERADO);

        assertThatThrownBy(() -> decidir(festival, new DecidirItemRequest(DecisionItem.APROBADO, null, null)))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> {
                    assertThat(e.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(e.getReason()).isEqualTo("La recomendación ya se convirtió en pedido");
                });
        assertThatThrownBy(() -> service.aprobarTodo(recomendacion.getId()))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
    }

    // ---- aprobarTodo ----

    @Test
    @DisplayName("aprobarTodo aprueba los PENDIENTE con cajas y omite los EXCLUIDO")
    void aprobarTodoOmiteExcluidos() {
        RecomendacionDTO dto = service.aprobarTodo(recomendacion.getId());

        assertThat(festival.getDecision()).isEqualTo(DecisionItem.APROBADO);
        assertThat(soloLack.getDecision()).isEqualTo(DecisionItem.APROBADO);
        assertThat(agotado.getDecision()).isEqualTo(DecisionItem.PENDIENTE);
        assertThat(dto.costoAprobado()).isEqualByComparingTo("1500.00");
        assertThat(dto.estado()).isEqualTo(EstadoRecomendacion.EN_REVISION);
    }

    @Test
    @DisplayName("aprobarTodo no cambia lo que el usuario ya decidió")
    void aprobarTodoRespetaDecisiones() {
        decidir(festival, new DecidirItemRequest(DecisionItem.MODIFICADO, 4, null));

        service.aprobarTodo(recomendacion.getId());

        assertThat(festival.getDecision()).isEqualTo(DecisionItem.MODIFICADO);
        assertThat(festival.getCajasAprobadas()).isEqualTo(4);
        assertThat(soloLack.getDecision()).isEqualTo(DecisionItem.APROBADO);
    }

    @Test
    @DisplayName("el detalle devuelve los ítems ordenados por prioridad")
    void detalleOrdenadoPorPrioridad() {
        recomendacion.getItems().clear();
        recomendacion.getItems().addAll(java.util.List.of(agotado, soloLack, festival));

        RecomendacionDTO dto = service.obtener(recomendacion.getId());

        assertThat(dto.items()).extracting(ItemDTO::prioridad).containsExactly(1, 2, 3);
    }
}
