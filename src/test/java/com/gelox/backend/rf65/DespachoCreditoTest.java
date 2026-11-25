package com.gelox.backend.rf65;

import com.gelox.backend.TestHelper;
import com.gelox.backend.credito.CreditoBloqueadoException;
import com.gelox.backend.credito.CreditoService;
import com.gelox.backend.credito.dto.PoliticaCreditoDTO;
import com.gelox.backend.dto.CrearDespachoRequest;
import com.gelox.backend.dto.CrearDespachoResponse;
import com.gelox.backend.dto.ItemDespachoRequest;
import com.gelox.backend.entities.Comerciante;
import com.gelox.backend.entities.PoliticaVencimiento;
import com.gelox.backend.entities.Producto;
import com.gelox.backend.entities.RolUsuario;
import com.gelox.backend.entities.Usuario;
import com.gelox.backend.plataforma.IdentidadDistribuidoraService;
import com.gelox.backend.repositories.ComercianteRepository;
import com.gelox.backend.repositories.ItemPlanillaRepository;
import com.gelox.backend.repositories.PlanillaComercianteRepository;
import com.gelox.backend.repositories.ProductoRepository;
import com.gelox.backend.services.EventoSistemaService;
import com.gelox.backend.services.InventarioService;
import com.gelox.backend.services.PlanillaService;
import com.gelox.backend.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * RF65 — al crear un despacho se evalúa el crédito del comerciante: con
 * política BLOQUEAR y crédito excedido se rechaza (409); con SOLO_ALERTA se
 * despacha igual, pero la respuesta trae advertencias (FP-15 §2.17.1).
 */
@ExtendWith(MockitoExtension.class)
class DespachoCreditoTest {

    private static final UUID TENANT = UUID.randomUUID();

    @Mock ComercianteRepository comercianteRepository;
    @Mock PlanillaComercianteRepository planillaRepository;
    @Mock ItemPlanillaRepository itemPlanillaRepository;
    @Mock ProductoRepository productoRepository;
    @Mock InventarioService inventarioService;
    @Mock EventoSistemaService eventoSistemaService;
    @Mock IdentidadDistribuidoraService identidadService;
    @Mock CreditoService creditoService;

    @InjectMocks
    PlanillaService planillaService;

    Comerciante comerciante;
    Producto producto;
    Usuario usuario;

    @BeforeEach
    void setUp() {
        TenantContext.set(TENANT);
        comerciante = Comerciante.builder().id(UUID.randomUUID()).tenantId(TENANT).nombre("Doña Rosa").activo(true).build();
        producto = new Producto();
        producto.setId(UUID.randomUUID());
        producto.setNombre("Bolis de mango");
        producto.setActivo(true);
        producto.setStockActual(100);
        usuario = TestHelper.buildUsuario("uid-ventas", "ventas@gelox-test.com", RolUsuario.ENCARGADO_VENTAS, true);

        when(comercianteRepository.findById(comerciante.getId())).thenReturn(Optional.of(comerciante));
        org.mockito.Mockito.lenient().when(productoRepository.findByIdInWithLock(List.of(producto.getId())))
                .thenReturn(List.of(producto));
        org.mockito.Mockito.lenient().when(planillaRepository.save(any()))
                .thenAnswer(inv -> {
                    var p = (com.gelox.backend.entities.PlanillaComerciante) inv.getArgument(0);
                    if (p.getId() == null) p.setId(UUID.randomUUID());
                    return p;
                });
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    CrearDespachoRequest request(BigDecimal precioPublico) {
        return new CrearDespachoRequest(comerciante.getId(), LocalDate.now(),
                List.of(new ItemDespachoRequest(producto.getId(), 10, BigDecimal.valueOf(1000), 0, precioPublico)));
    }

    @Test
    @DisplayName("comerciante sin exceder el crédito: despacha sin advertencias")
    void sinExcederCredito_despachaSinAdvertencias() {
        when(creditoService.evaluar(comerciante.getId())).thenReturn(
                new CreditoService.Evaluacion(politica(PoliticaVencimiento.BLOQUEAR), BigDecimal.ZERO, 0, false));

        CrearDespachoResponse response = planillaService.crearDespacho(request(null), usuario);

        assertThat(response.advertencias()).isEmpty();
    }

    @Test
    @DisplayName("crédito excedido con política BLOQUEAR: 409 y no se despacha nada")
    void creditoExcedidoConBloquear_lanza409YNoDespacha() {
        when(creditoService.evaluar(comerciante.getId())).thenReturn(
                new CreditoService.Evaluacion(politica(PoliticaVencimiento.BLOQUEAR),
                        BigDecimal.valueOf(80000), 0, true));

        assertThatThrownBy(() -> planillaService.crearDespacho(request(null), usuario))
                .isInstanceOf(CreditoBloqueadoException.class);

        verify(itemPlanillaRepository, never()).save(any());
        verify(inventarioService, never()).descontarStock(any(), any(Integer.class), any(), any(), any());
    }

    @Test
    @DisplayName("crédito excedido con política SOLO_ALERTA: despacha igual pero con advertencias")
    void creditoExcedidoConSoloAlerta_despachaConAdvertencias() {
        when(creditoService.evaluar(comerciante.getId())).thenReturn(
                new CreditoService.Evaluacion(politica(PoliticaVencimiento.SOLO_ALERTA),
                        BigDecimal.valueOf(80000), 1, true));

        CrearDespachoResponse response = planillaService.crearDespacho(request(null), usuario);

        assertThat(response.advertencias()).hasSize(1);
        assertThat(response.advertencias().get(0).deuda()).isEqualByComparingTo("80000");
        assertThat(response.advertencias().get(0).planillasVencidas()).isEqualTo(1);
    }

    @Test
    @DisplayName("el precio al público del ítem se persiste en el despacho")
    void persisteElPrecioAlPublicoDelItem() {
        when(creditoService.evaluar(comerciante.getId())).thenReturn(
                new CreditoService.Evaluacion(politica(PoliticaVencimiento.SOLO_ALERTA), BigDecimal.ZERO, 0, false));

        planillaService.crearDespacho(request(BigDecimal.valueOf(1500)), usuario);

        var captor = org.mockito.ArgumentCaptor.forClass(com.gelox.backend.entities.ItemPlanilla.class);
        verify(itemPlanillaRepository).save(captor.capture());
        assertThat(captor.getValue().getPrecioPublico()).isEqualByComparingTo("1500");
    }

    private PoliticaCreditoDTO politica(PoliticaVencimiento vencimiento) {
        return new PoliticaCreditoDTO(BigDecimal.valueOf(50000), 5, vencimiento, false);
    }
}
