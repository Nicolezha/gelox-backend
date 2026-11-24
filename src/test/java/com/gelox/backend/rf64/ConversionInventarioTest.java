package com.gelox.backend.rf64;

import com.gelox.backend.TestHelper;
import com.gelox.backend.dto.ConfirmarVentaRequest;
import com.gelox.backend.dto.ConfirmarVentaResponse;
import com.gelox.backend.dto.ItemEntradaRequest;
import com.gelox.backend.dto.ItemVentaRequest;
import com.gelox.backend.dto.RegistrarEntradaRequest;
import com.gelox.backend.entities.CanalVenta;
import com.gelox.backend.entities.FactorConversion;
import com.gelox.backend.entities.ItemVenta;
import com.gelox.backend.entities.MetodoPago;
import com.gelox.backend.entities.Producto;
import com.gelox.backend.entities.RolUsuario;
import com.gelox.backend.entities.UnidadMedida;
import com.gelox.backend.entities.Usuario;
import com.gelox.backend.entities.Venta;
import com.gelox.backend.exceptions.StockInsuficienteException;
import com.gelox.backend.plataforma.IdentidadDistribuidoraService;
import com.gelox.backend.repositories.FactorConversionRepository;
import com.gelox.backend.repositories.ItemPedidoProveedorRepository;
import com.gelox.backend.repositories.ItemVentaRepository;
import com.gelox.backend.repositories.MovimientoInventarioRepository;
import com.gelox.backend.repositories.PedidoProveedorRepository;
import com.gelox.backend.repositories.PerdidaRepository;
import com.gelox.backend.repositories.ProductoRepository;
import com.gelox.backend.repositories.VentaRepository;
import com.gelox.backend.services.ConversionUnidadService;
import com.gelox.backend.services.EventoSistemaService;
import com.gelox.backend.services.InventarioService;
import com.gelox.backend.services.PedidoProveedorService;
import com.gelox.backend.services.VentaService;
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

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * RF64 / FP-15 §1.6.1 fila 2 — el stock se lleva en la unidad base: CJ → UN con unidades_por_caja
 * y las demás unidades con factor_conversion, tanto en la entrada (RF23) como en la venta.
 */
@ExtendWith(MockitoExtension.class)
class ConversionInventarioTest {

    private static final UUID TENANT = UUID.randomUUID();

    @Mock ProductoRepository productoRepository;
    @Mock MovimientoInventarioRepository movimientoRepository;
    @Mock PedidoProveedorRepository pedidoRepository;
    @Mock ItemPedidoProveedorRepository itemPedidoRepository;
    @Mock PerdidaRepository perdidaRepository;
    @Mock VentaRepository ventaRepository;
    @Mock ItemVentaRepository itemVentaRepository;
    @Mock EventoSistemaService eventoSistemaService;
    @Mock FactorConversionRepository factorRepository;

    PedidoProveedorService pedidoService;
    VentaService ventaService;
    Usuario usuario;
    Producto cono;
    UnidadMedida un;
    UnidadMedida bol;

    @BeforeEach
    void setUp() {
        TenantContext.set(TENANT);
        usuario = TestHelper.buildUsuario("uid-a", "a@b.com", RolUsuario.ADMINISTRADOR, true);

        ConversionUnidadService conversion = new ConversionUnidadService(factorRepository);
        InventarioService inventario = new InventarioService(productoRepository, movimientoRepository, eventoSistemaService);
        pedidoService = new PedidoProveedorService(pedidoRepository, itemPedidoRepository, productoRepository,
                movimientoRepository, perdidaRepository, eventoSistemaService,
                mock(IdentidadDistribuidoraService.class), conversion);
        ventaService = new VentaService(ventaRepository, itemVentaRepository, productoRepository,
                eventoSistemaService, inventario, conversion);

        un = new UnidadMedida();
        un.setId(UUID.randomUUID());
        un.setCodigo("UN");
        bol = new UnidadMedida();
        bol.setId(UUID.randomUUID());
        bol.setCodigo("BOL");

        cono = new Producto();
        cono.setId(UUID.randomUUID());
        cono.setTenantId(TENANT);
        cono.setCodigoTecnico("CN-001");
        cono.setNombre("Cono vainilla");
        cono.setPrecioVenta(new BigDecimal("1000"));
        cono.setPrecioCosto(new BigDecimal("24000"));   // por caja
        cono.setUnidadesPorCaja(24);
        cono.setUnidadBase(un);
        cono.setStockActual(0);
        cono.setActivo(true);

        lenient().when(productoRepository.findById(cono.getId())).thenReturn(Optional.of(cono));
        lenient().when(productoRepository.findByIdWithLock(cono.getId())).thenReturn(Optional.of(cono));
        lenient().when(productoRepository.findByIdInWithLock(List.of(cono.getId()))).thenReturn(List.of(cono));
        lenient().when(productoRepository.save(any(Producto.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(ventaRepository.save(any(Venta.class))).thenAnswer(inv -> {
            Venta v = inv.getArgument(0);
            v.setId(UUID.randomUUID());
            return v;
        });
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private void entrada(Integer cajas, Integer unidades, String unidad, Integer cantidadUnidad) {
        pedidoService.registrarEntrada(new RegistrarEntradaRequest(null,
                List.of(new ItemEntradaRequest(cono.getId(), cajas, unidades, null, unidad, cantidadUnidad))), usuario);
    }

    private ConfirmarVentaResponse venta(Integer cajas, Integer unidades, String unidad, Integer cantidadUnidad) {
        return ventaService.confirmarVenta(new ConfirmarVentaRequest(CanalVenta.VENTANILLA, MetodoPago.EFECTIVO,
                List.of(new ItemVentaRequest(cono.getId(), cajas, unidades, unidad, cantidadUnidad))), usuario);
    }

    private void factorBolsa(String factor) {
        FactorConversion f = new FactorConversion();
        f.setUnidadOrigen(bol);
        f.setUnidadDestino(un);
        f.setFactor(new BigDecimal(factor));
        when(factorRepository.findHaciaBase(cono.getId(), "BOL", un.getId())).thenReturn(Optional.of(f));
    }

    @Test
    @DisplayName("entrada de 2 cajas × 24 y venta de 30 uds. deja 18 en la unidad base")
    void entradaDeDosCajasYVentaDeTreintaDejaDieciocho() {
        entrada(2, 0, null, null);
        assertThat(cono.getStockActual()).isEqualTo(48);

        ConfirmarVentaResponse respuesta = venta(0, 30, null, null);

        assertThat(cono.getStockActual()).isEqualTo(18);
        assertThat(respuesta.total()).isEqualByComparingTo("30000");
    }

    @Test
    @DisplayName("vender 1 caja y 6 unidades descuenta 30 (CJ → UN con unidades_por_caja)")
    void ventaEnCajasYUnidades() {
        cono.setStockActual(48);

        venta(1, 6, null, null);

        assertThat(cono.getStockActual()).isEqualTo(18);
    }

    @Test
    @DisplayName("más que el stock en la unidad base → StockInsuficiente y el stock no cambia")
    void ventaSinStock() {
        cono.setStockActual(18);

        assertThatThrownBy(() -> venta(1, 0, null, null)).isInstanceOf(StockInsuficienteException.class);

        assertThat(cono.getStockActual()).isEqualTo(18);
    }

    @Test
    @DisplayName("entrada de 3 BOL con 1 BOL = 6 UN suma 18 (factor_conversion)")
    void entradaEnOtraUnidad() {
        factorBolsa("6");

        entrada(0, 0, "BOL", 3);

        assertThat(cono.getStockActual()).isEqualTo(18);
    }

    @Test
    @DisplayName("entrada que mezcla cajas, unidades y otra unidad: 1×24 + 2 + 2 BOL×6 = 38")
    void entradaMixta() {
        factorBolsa("6");

        entrada(1, 2, "BOL", 2);

        assertThat(cono.getStockActual()).isEqualTo(38);
    }

    @Test
    @DisplayName("venta de 4 BOL (1 BOL = 6 UN) descuenta 24 y registra el ítem en unidades base")
    void ventaEnOtraUnidad() {
        cono.setStockActual(48);
        factorBolsa("6");

        ConfirmarVentaResponse respuesta = venta(0, 0, "BOL", 4);

        assertThat(cono.getStockActual()).isEqualTo(24);
        assertThat(respuesta.total()).isEqualByComparingTo("24000");
        ArgumentCaptor<ItemVenta> item = ArgumentCaptor.forClass(ItemVenta.class);
        verify(itemVentaRepository).save(item.capture());
        assertThat(item.getValue().getCantidadCajas()).isZero();
        assertThat(item.getValue().getCantidadUnidades()).isEqualTo(24);
    }

    @Test
    @DisplayName("la unidad base cuenta uno a uno y CJ usa unidades_por_caja aunque llegue como 'unidad'")
    void unidadBaseYCaja() {
        entrada(0, 0, "UN", 5);
        entrada(0, 0, "cj", 1);

        assertThat(cono.getStockActual()).isEqualTo(29);
    }

    @Test
    @DisplayName("otra unidad sin factor declarado → 400 y el stock no cambia")
    void sinFactor() {
        when(factorRepository.findHaciaBase(eq(cono.getId()), eq("BOL"), any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> entrada(0, 0, "BOL", 3)).satisfies(e -> assertThat(e)
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        ex -> assertThat(ex.getStatusCode().value()).isEqualTo(HttpStatus.BAD_REQUEST.value())));
        assertThat(cono.getStockActual()).isZero();
    }

    @Test
    @DisplayName("una cantidad que no da unidades enteras (3 × 2,5) → 400")
    void resultadoNoEntero() {
        factorBolsa("2.5");

        assertThatThrownBy(() -> entrada(0, 0, "BOL", 3)).isInstanceOf(ResponseStatusException.class);
        assertThat(cono.getStockActual()).isZero();
    }

    @Test
    @DisplayName("unidad sin cantidad (o al revés) → 400")
    void unidadYCantidadJuntas() {
        assertThatThrownBy(() -> entrada(0, 0, "BOL", null)).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> entrada(0, 0, null, 3)).isInstanceOf(ResponseStatusException.class);
    }
}
