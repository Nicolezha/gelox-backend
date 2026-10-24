package com.gelox.backend.rf54;

import com.gelox.backend.entities.AjustePresupuesto;
import com.gelox.backend.entities.DecisionItem;
import com.gelox.backend.entities.EstadoRecomendacion;
import com.gelox.backend.entities.ItemRecomendacion;
import com.gelox.backend.entities.OrigenRecomendacion;
import com.gelox.backend.entities.Producto;
import com.gelox.backend.entities.RecomendacionCompra;
import com.gelox.backend.entities.TipoEvento;
import com.gelox.backend.entities.Usuario;
import com.gelox.backend.recomendacion.DemandaRepository;
import com.gelox.backend.recomendacion.DemandaRepository.DemandaDia;
import com.gelox.backend.recomendacion.MotorRecomendacionCompra;
import com.gelox.backend.recomendacion.RecomendacionCompraService;
import com.gelox.backend.recomendacion.dto.RecomendacionDTO;
import com.gelox.backend.recomendacion.dto.RecomendacionGeneradaDTO;
import com.gelox.backend.repositories.ProductoRepository;
import com.gelox.backend.repositories.RecomendacionCompraRepository;
import com.gelox.backend.services.EventoSistemaService;
import com.gelox.backend.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * RF54 — RecomendacionCompraService: validación del presupuesto, confianza baja, guardado de la
 * recomendación con sus ítems (también los EXCLUIDO), productos excluidos y evento.
 */
@ExtendWith(MockitoExtension.class)
class RecomendacionCompraServiceTest {

    static final UUID TENANT = UUID.randomUUID();
    static final LocalDate HOY = LocalDate.now(ZoneId.of("America/Bogota"));

    @Mock ProductoRepository productoRepo;
    @Mock DemandaRepository demandaRepo;
    @Mock RecomendacionCompraRepository recomendacionRepo;
    @Mock EventoSistemaService eventoService;

    RecomendacionCompraService service;
    Usuario usuario;

    @BeforeEach
    void setUp() {
        TenantContext.set(TENANT);
        service = new RecomendacionCompraService(productoRepo, demandaRepo, recomendacionRepo,
                new MotorRecomendacionCompra(2, 7), eventoService);
        usuario = Usuario.builder().id(UUID.randomUUID()).nombre("Ayde").tenantId(TENANT).build();

        lenient().when(recomendacionRepo.save(any())).thenAnswer(inv -> {
            RecomendacionCompra r = inv.getArgument(0);
            r.setId(UUID.randomUUID());
            r.getItems().forEach(i -> i.setId(UUID.randomUUID()));
            return r;
        });
    }

    @AfterEach
    void limpiar() {
        TenantContext.clear();
    }

    static Producto producto(String nombre, int stock, int minimo, Integer unidadesCaja, String precioCosto) {
        Producto p = new Producto();
        p.setId(UUID.randomUUID());
        p.setTenantId(TENANT);
        p.setNombre(nombre);
        p.setStockActual(stock);
        p.setStockMinimo(minimo);
        p.setUnidadesPorCaja(unidadesCaja);
        p.setPrecioCosto(precioCosto == null ? null : new BigDecimal(precioCosto));
        p.setPrecioVenta(new BigDecimal("2500"));
        p.setActivo(true);
        return p;
    }

    /** Una venta por día en {@code dias} días distintos de la ventana. */
    static List<DemandaDia> ventasEnDias(Producto p, int dias) {
        List<DemandaDia> ventas = new ArrayList<>();
        for (int i = 0; i < dias; i++) ventas.add(new DemandaDia(p.getId(), HOY.minusDays(i), 1));
        return ventas;
    }

    void historial(int diasHistorial, List<DemandaDia> demanda) {
        when(demandaRepo.diasHistorial(TENANT)).thenReturn(diasHistorial);
        when(demandaRepo.demandaDiaria(TENANT, HOY.minusDays(89), HOY)).thenReturn(demanda);
    }

    RecomendacionCompra guardada() {
        ArgumentCaptor<RecomendacionCompra> captor = ArgumentCaptor.forClass(RecomendacionCompra.class);
        verify(recomendacionRepo).save(captor.capture());
        return captor.getValue();
    }

    // ══════════════════════════════════════════════════════════════════════

    @ParameterizedTest(name = "presupuesto {0} → 400")
    @CsvSource({"0", "-1000"})
    @DisplayName("Presupuesto ≤ 0 → «El presupuesto debe ser mayor a cero»")
    void presupuestoNoPositivo(String presupuesto) {
        assertThatThrownBy(() -> service.generar(new BigDecimal(presupuesto), OrigenRecomendacion.PANTALLA, usuario))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("El presupuesto debe ser mayor a cero");
        verify(recomendacionRepo, never()).save(any());
    }

    @Test
    @DisplayName("Presupuesto ausente → mismo error")
    void presupuestoNulo() {
        assertThatThrownBy(() -> service.generar(null, null, usuario))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("El presupuesto debe ser mayor a cero");
    }

    @ParameterizedTest(name = "historial {0} días, {1} días con ventas → confianzaBaja = {2}")
    @CsvSource({
            "89, 25, true",    // historial corto
            "120, 19, true",   // pocos días con ventas
            "90, 20, false",
            "400, 60, false"
    })
    @DisplayName("Confianza baja por cualquiera de los dos criterios")
    void confianzaBaja(int diasHistorial, int diasConVentas, boolean esperado) {
        Producto p = producto("Paleta", 0, 10, 12, "20000");
        when(productoRepo.findAll()).thenReturn(List.of(p));
        historial(diasHistorial, ventasEnDias(p, diasConVentas));

        RecomendacionGeneradaDTO dto = service.generar(new BigDecimal("100000"), OrigenRecomendacion.PANTALLA, usuario);

        assertThat(dto.confianzaBaja()).isEqualTo(esperado);
        assertThat(dto.diasHistorial()).isEqualTo(diasHistorial);
        assertThat(dto.diasConVentas()).isEqualTo(diasConVentas);
        assertThat(guardada().getConfianzaBaja()).isEqualTo(esperado);
    }

    @Test
    @DisplayName("Guarda GENERADA con todas las variables, incluido el ítem EXCLUIDO; los excluidos solo se devuelven")
    void guardaRecomendacionCompleta() {
        Producto barato = producto("Barato", 0, 5, 24, "30000");
        Producto caro = producto("Caro", 1, 5, 24, "90000");
        Producto sinCosto = producto("Sin costo", 0, 5, 24, null);
        when(productoRepo.findAll()).thenReturn(List.of(barato, caro, sinCosto));
        historial(200, List.of());

        RecomendacionGeneradaDTO dto = service.generar(new BigDecimal("50000"), null, usuario);

        RecomendacionCompra r = guardada();
        assertThat(r.getTenantId()).isEqualTo(TENANT);
        assertThat(r.getUsuario()).isEqualTo(usuario);
        assertThat(r.getEstado()).isEqualTo(EstadoRecomendacion.GENERADA);
        assertThat(r.getOrigen()).isEqualTo(OrigenRecomendacion.PANTALLA);
        assertThat(r.getPresupuesto()).isEqualByComparingTo("50000");
        assertThat(r.getCostoTotal()).isEqualByComparingTo("30000");
        assertThat(r.getItems()).hasSize(2);
        assertThat(r.getItems()).allSatisfy(i -> {
            assertThat(i.getRecomendacion()).isSameAs(r);
            assertThat(i.getDecision()).isEqualTo(DecisionItem.PENDIENTE);
            assertThat(i.getStockSeguridad()).isNotNull();
            assertThat(i.getPuntoReorden()).isNotNull();
            assertThat(i.getJustificacion()).isNotBlank();
        });

        ItemRecomendacion excluido = r.getItems().stream()
                .filter(i -> i.getAjustePresupuesto() == AjustePresupuesto.EXCLUIDO).findFirst().orElseThrow();
        assertThat(excluido.getProducto()).isEqualTo(caro);
        assertThat(excluido.getCajasSugeridas()).isZero();

        assertThat(dto.items()).hasSize(2);
        assertThat(dto.items().get(0).prioridad()).isEqualTo(1);
        assertThat(dto.costoAprobado()).isEqualByComparingTo("0");
        assertThat(dto.productosExcluidos()).singleElement().satisfies(e -> {
            assertThat(e.productoId()).isEqualTo(sinCosto.getId());
            assertThat(e.nombre()).isEqualTo("Sin costo");
            assertThat(e.motivo()).isEqualTo("Sin precio de costo");
        });
    }

    @Test
    @DisplayName("Registra el evento RECOMENDACION_GENERADA con el usuario")
    void registraEvento() {
        Producto p = producto("Paleta", 0, 5, 24, "30000");
        when(productoRepo.findAll()).thenReturn(List.of(p));
        historial(200, List.of());

        service.generar(new BigDecimal("50000"), OrigenRecomendacion.VOZ, usuario);

        verify(eventoService).registrarEvento(eq(TipoEvento.RECOMENDACION_GENERADA), anyString(), eq(usuario.getId()));
        assertThat(guardada().getOrigen()).isEqualTo(OrigenRecomendacion.VOZ);
    }

    @Test
    @DisplayName("Ningún producto requiere compra → se guarda con la lista vacía")
    void sinProductosQueComprar() {
        Producto conStock = producto("Con stock", 100, 5, 24, "30000");
        when(productoRepo.findAll()).thenReturn(List.of(conStock));
        historial(200, List.of());

        RecomendacionGeneradaDTO dto = service.generar(new BigDecimal("50000"), OrigenRecomendacion.PANTALLA, usuario);

        assertThat(dto.items()).isEmpty();
        assertThat(dto.costoTotal()).isEqualByComparingTo("0");
        assertThat(guardada().getItems()).isEmpty();
    }

    @Test
    @DisplayName("Productos de otra distribuidora no entran aunque lleguen del repositorio")
    void ignoraOtroTenant() {
        Producto propio = producto("Propio", 0, 5, 24, "30000");
        Producto ajeno = producto("Ajeno", 0, 5, 24, "30000");
        ajeno.setTenantId(UUID.randomUUID());
        when(productoRepo.findAll()).thenReturn(List.of(propio, ajeno));
        historial(200, List.of());

        RecomendacionGeneradaDTO dto = service.generar(new BigDecimal("500000"), OrigenRecomendacion.PANTALLA, usuario);

        assertThat(dto.items()).extracting(i -> i.productoNombre()).containsExactly("Propio");
    }

    @Test
    @DisplayName("Sin distribuidora en la petición → 403")
    void sinTenant() {
        TenantContext.clear();
        assertThatThrownBy(() -> service.generar(new BigDecimal("50000"), OrigenRecomendacion.PANTALLA, usuario))
                .hasMessageContaining("Sin distribuidora");
    }

    @Test
    @DisplayName("recomendacionDelDia busca la de hoy del tenant en GENERADA o EN_REVISION")
    void recomendacionDelDia() {
        RecomendacionCompra hoy = RecomendacionCompra.builder()
                .id(UUID.randomUUID()).tenantId(TENANT).estado(EstadoRecomendacion.EN_REVISION)
                .origen(OrigenRecomendacion.VOZ).presupuesto(new BigDecimal("80000"))
                .costoTotal(new BigDecimal("69880")).build();
        when(recomendacionRepo.buscarUltima(eq(TENANT),
                eq(List.of(EstadoRecomendacion.GENERADA, EstadoRecomendacion.EN_REVISION)),
                eq(HOY.atStartOfDay()), eq(HOY.plusDays(1).atStartOfDay())))
                .thenReturn(Optional.of(hoy));

        Optional<RecomendacionDTO> dto = service.recomendacionDelDia();

        assertThat(dto).hasValueSatisfying(r -> {
            assertThat(r.id()).isEqualTo(hoy.getId());
            assertThat(r.costoTotal()).isEqualByComparingTo("69880");
        });
    }

    @Test
    @DisplayName("recomendacionDelDia sin recomendación de hoy → vacío")
    void recomendacionDelDiaVacia() {
        when(recomendacionRepo.buscarUltima(any(), any(), any(), any())).thenReturn(Optional.empty());
        assertThat(service.recomendacionDelDia()).isEmpty();
    }
}
