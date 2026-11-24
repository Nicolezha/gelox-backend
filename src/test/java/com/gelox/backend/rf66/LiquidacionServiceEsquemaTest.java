package com.gelox.backend.rf66;

import com.gelox.backend.TestHelper;
import com.gelox.backend.credito.CalculoLiquidacionResolver;
import com.gelox.backend.credito.ComisionCalculo;
import com.gelox.backend.credito.CreditoService;
import com.gelox.backend.credito.EsquemaLiquidacionService;
import com.gelox.backend.credito.MargenCalculo;
import com.gelox.backend.credito.TarifaFijaCalculo;
import com.gelox.backend.dto.DevolucionItemDTO;
import com.gelox.backend.dto.LiquidacionRequestDTO;
import com.gelox.backend.dto.LiquidacionResponseDTO;
import com.gelox.backend.entities.Comerciante;
import com.gelox.backend.entities.EsquemaLiquidacion;
import com.gelox.backend.entities.ItemPlanilla;
import com.gelox.backend.entities.PlanillaComerciante;
import com.gelox.backend.entities.RolUsuario;
import com.gelox.backend.entities.TipoEsquemaLiquidacion;
import com.gelox.backend.entities.Usuario;
import com.gelox.backend.repositories.ItemPlanillaRepository;
import com.gelox.backend.repositories.PlanillaComercianteRepository;
import com.gelox.backend.services.EventoSistemaService;
import com.gelox.backend.services.InventarioService;
import com.gelox.backend.services.LiquidacionService;
import com.gelox.backend.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * RF66 — liquidarPlanilla aplica el esquema vigente (FP-15 §2.17.2). MARGEN
 * debe reproducir exactamente el total histórico (precioVenta * unidades
 * vendidas); COMISION y TARIFA_FIJA cambian cuánto se factura y calculan la
 * ganancia del comerciante cuando hay precio al público.
 */
@ExtendWith(MockitoExtension.class)
class LiquidacionServiceEsquemaTest {

    private static final UUID TENANT = UUID.randomUUID();

    @Mock PlanillaComercianteRepository planillaRepository;
    @Mock ItemPlanillaRepository itemPlanillaRepository;
    @Mock InventarioService inventarioService;
    @Mock EventoSistemaService eventoSistemaService;
    @Mock EsquemaLiquidacionService esquemaLiquidacionService;
    @Mock CreditoService creditoService;

    LiquidacionService liquidacionService;

    PlanillaComerciante planilla;
    ItemPlanilla item;
    Usuario usuario;

    @BeforeEach
    void setUp() {
        TenantContext.set(TENANT);
        CalculoLiquidacionResolver resolver = new CalculoLiquidacionResolver(
                List.of(new MargenCalculo(), new ComisionCalculo(), new TarifaFijaCalculo()));
        liquidacionService = new LiquidacionService(planillaRepository, itemPlanillaRepository, inventarioService,
                eventoSistemaService, esquemaLiquidacionService, resolver, creditoService);

        Comerciante comerciante = Comerciante.builder().id(UUID.randomUUID()).tenantId(TENANT).nombre("Doña Rosa").build();
        usuario = TestHelper.buildUsuario("uid-ventas", "ventas@gelox-test.com", RolUsuario.ENCARGADO_VENTAS, true);
        planilla = PlanillaComerciante.builder()
                .id(UUID.randomUUID()).tenantId(TENANT).comerciante(comerciante).usuario(usuario)
                .fecha(LocalDate.now()).cerrada(false).build();
        item = ItemPlanilla.builder()
                .id(UUID.randomUUID()).planilla(planilla)
                .unidadesDespachadas(10).unidadesDevueltas(0)
                .precioVenta(BigDecimal.valueOf(1000))
                .build();

        when(planillaRepository.findById(planilla.getId())).thenReturn(Optional.of(planilla));
        when(itemPlanillaRepository.findByIdAndPlanillaId(item.getId(), planilla.getId())).thenReturn(Optional.of(item));
        when(planillaRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(creditoService.plazoDiasEfectivo(any())).thenReturn(5);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    EsquemaLiquidacion esquema(TipoEsquemaLiquidacion tipo, BigDecimal valor) {
        return EsquemaLiquidacion.builder().id(UUID.randomUUID()).tenantId(TENANT).tipo(tipo).valor(valor).build();
    }

    LiquidacionRequestDTO devolver(int unidadesDevueltas, BigDecimal efectivoRecibido) {
        return new LiquidacionRequestDTO(List.of(new DevolucionItemDTO(item.getId(), unidadesDevueltas)), efectivoRecibido);
    }

    @Test
    @DisplayName("MARGEN reproduce exactamente el total histórico: precioVenta * unidadesVendidas")
    void margen_reproduceElTotalHistorico() {
        when(esquemaLiquidacionService.vigente()).thenReturn(esquema(TipoEsquemaLiquidacion.MARGEN, BigDecimal.ZERO));

        LiquidacionResponseDTO response = liquidacionService.liquidarPlanilla(planilla.getId(), devolver(2, null), usuario);

        // 8 unidades vendidas * 1000 = 8000, igual al cálculo de siempre
        assertThat(response.gananciaTotal()).isEqualByComparingTo("8000");
        assertThat(response.esquemaAplicado()).isEqualTo(TipoEsquemaLiquidacion.MARGEN);
        assertThat(response.gananciaComercianteTotal()).isNull();
        assertThat(response.efectivoRecibido()).isEqualByComparingTo("8000");
        assertThat(response.fechaLimitePago()).isEqualTo(LocalDate.now().plusDays(5));
        assertThat(planilla.getTotalGanancia()).isEqualByComparingTo("8000");
    }

    @Test
    @DisplayName("COMISION factura el porcentaje configurado sobre el precio al público del ítem")
    void comision_facturaElPorcentajeConfigurado() {
        item.setPrecioPublico(BigDecimal.valueOf(1500));
        when(esquemaLiquidacionService.vigente())
                .thenReturn(esquema(TipoEsquemaLiquidacion.COMISION, BigDecimal.valueOf(20)));

        LiquidacionResponseDTO response = liquidacionService.liquidarPlanilla(planilla.getId(), devolver(0, null), usuario);

        // venta al público = 10 * 1500 = 15000; comisión 20% = 3000; ganancia comerciante = 12000
        assertThat(response.gananciaTotal()).isEqualByComparingTo("3000");
        assertThat(response.gananciaComercianteTotal()).isEqualByComparingTo("12000");
    }

    @Test
    @DisplayName("TARIFA_FIJA factura un monto fijo por unidad vendida, sin importar el precio")
    void tarifaFija_facturaMontoFijoPorUnidad() {
        when(esquemaLiquidacionService.vigente())
                .thenReturn(esquema(TipoEsquemaLiquidacion.TARIFA_FIJA, BigDecimal.valueOf(100)));

        LiquidacionResponseDTO response = liquidacionService.liquidarPlanilla(planilla.getId(), devolver(0, null), usuario);

        assertThat(response.gananciaTotal()).isEqualByComparingTo("1000");
    }

    @Test
    @DisplayName("sin efectivoRecibido indicado, se asume pago completo (igual a la ganancia total)")
    void sinEfectivoRecibido_asumePagoCompleto() {
        when(esquemaLiquidacionService.vigente()).thenReturn(esquema(TipoEsquemaLiquidacion.MARGEN, BigDecimal.ZERO));

        LiquidacionResponseDTO response = liquidacionService.liquidarPlanilla(planilla.getId(), devolver(0, null), usuario);

        assertThat(response.efectivoRecibido()).isEqualByComparingTo(response.gananciaTotal());
        assertThat(planilla.getEfectivoRecibido()).isEqualByComparingTo(response.gananciaTotal());
    }

    @Test
    @DisplayName("con efectivoRecibido parcial, se guarda el valor indicado (queda saldo pendiente)")
    void conEfectivoRecibidoParcial_guardaElValorIndicado() {
        when(esquemaLiquidacionService.vigente()).thenReturn(esquema(TipoEsquemaLiquidacion.MARGEN, BigDecimal.ZERO));

        LiquidacionResponseDTO response = liquidacionService.liquidarPlanilla(
                planilla.getId(), devolver(0, BigDecimal.valueOf(4000)), usuario);

        assertThat(response.gananciaTotal()).isEqualByComparingTo("10000");
        assertThat(response.efectivoRecibido()).isEqualByComparingTo("4000");
        assertThat(planilla.getEfectivoRecibido()).isEqualByComparingTo("4000");
    }

    @Test
    @DisplayName("la fecha límite de pago es la fecha de cierre más el plazo de la política efectiva")
    void fechaLimitePago_esFechaCierreMasPlazo() {
        when(esquemaLiquidacionService.vigente()).thenReturn(esquema(TipoEsquemaLiquidacion.MARGEN, BigDecimal.ZERO));
        when(creditoService.plazoDiasEfectivo(planilla.getComerciante().getId())).thenReturn(15);

        LiquidacionResponseDTO response = liquidacionService.liquidarPlanilla(planilla.getId(), devolver(0, null), usuario);

        assertThat(response.fechaLimitePago()).isEqualTo(LocalDate.now().plusDays(15));
    }
}
