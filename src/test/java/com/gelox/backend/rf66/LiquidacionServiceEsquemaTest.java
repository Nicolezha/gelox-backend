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
import jakarta.validation.Validation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * RF66 — liquidarPlanilla aplica el esquema vigente a TODA la planilla
 * (FP-15 §2.17.2), espejo del contrato de PlanillaDiaria.jsx. MARGEN sin
 * precio al público debe reproducir exactamente el total histórico
 * (precioVenta * unidadesVendidas).
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

        lenient().when(planillaRepository.findById(planilla.getId())).thenReturn(Optional.of(planilla));
        lenient().when(itemPlanillaRepository.findByIdAndPlanillaId(item.getId(), planilla.getId())).thenReturn(Optional.of(item));
        lenient().when(planillaRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(creditoService.plazoDiasEfectivo(any())).thenReturn(5);
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
    @DisplayName("MARGEN sin precio al público reproduce exactamente el total histórico: precioVenta * unidadesVendidas")
    void margen_sinPrecioPublico_reproduceElTotalHistorico() {
        when(esquemaLiquidacionService.vigente()).thenReturn(esquema(TipoEsquemaLiquidacion.MARGEN, BigDecimal.ZERO));

        LiquidacionResponseDTO response = liquidacionService.liquidarPlanilla(planilla.getId(), devolver(2, null), usuario);

        // 8 unidades vendidas * 1000 = 8000, igual al cálculo de siempre
        assertThat(response.montoCobrado()).isEqualByComparingTo("8000");
        assertThat(response.esquemaAplicado()).isEqualTo(TipoEsquemaLiquidacion.MARGEN);
        assertThat(response.gananciaComerciante()).isNull();
        assertThat(response.efectivoRecibido()).isEqualByComparingTo("8000");
        assertThat(response.saldo()).isEqualByComparingTo("0");
        assertThat(response.fechaLimitePago()).isNull();
        assertThat(planilla.getTotalGanancia()).isEqualByComparingTo("8000");
    }

    @Test
    @DisplayName("MARGEN con precio al público: la ganancia del comerciante es la diferencia de precios")
    void margen_conPrecioPublico_calculaGananciaComerciante() {
        item.setPrecioPublico(BigDecimal.valueOf(1500));
        when(esquemaLiquidacionService.vigente()).thenReturn(esquema(TipoEsquemaLiquidacion.MARGEN, BigDecimal.ZERO));

        LiquidacionResponseDTO response = liquidacionService.liquidarPlanilla(planilla.getId(), devolver(0, null), usuario);

        // 10 * 1000 = 10000 factura la distribuidora; 10 * (1500-1000) = 5000 gana el comerciante
        assertThat(response.montoCobrado()).isEqualByComparingTo("10000");
        assertThat(response.gananciaComerciante()).isEqualByComparingTo("5000");
        assertThat(response.totalVendido()).isEqualByComparingTo("15000");
    }

    @Test
    @DisplayName("COMISION factura el porcentaje configurado sobre el precio al público del ítem")
    void comision_facturaElPorcentajeConfigurado() {
        item.setPrecioPublico(BigDecimal.valueOf(1500));
        when(esquemaLiquidacionService.vigente())
                .thenReturn(esquema(TipoEsquemaLiquidacion.COMISION, BigDecimal.valueOf(20)));

        LiquidacionResponseDTO response = liquidacionService.liquidarPlanilla(planilla.getId(), devolver(0, null), usuario);

        // venta al público = 10 * 1500 = 15000; comisión 20% = 3000 para el comerciante; 12000 factura la distribuidora
        assertThat(response.gananciaComerciante()).isEqualByComparingTo("3000");
        assertThat(response.montoCobrado()).isEqualByComparingTo("12000");
    }

    @Test
    @DisplayName("TARIFA_FIJA factura la venta al público menos el valor fijo que gana el comerciante")
    void tarifaFija_facturaElRestante() {
        item.setPrecioPublico(BigDecimal.valueOf(1500));
        when(esquemaLiquidacionService.vigente())
                .thenReturn(esquema(TipoEsquemaLiquidacion.TARIFA_FIJA, BigDecimal.valueOf(2000)));

        LiquidacionResponseDTO response = liquidacionService.liquidarPlanilla(planilla.getId(), devolver(0, null), usuario);

        // venta al público = 15000; el comerciante gana 2000 fijos; la distribuidora factura 13000
        assertThat(response.gananciaComerciante()).isEqualByComparingTo("2000");
        assertThat(response.montoCobrado()).isEqualByComparingTo("13000");
    }

    @Test
    @DisplayName("MARGEN con precio al público en 0: cobra el precio al comerciante y el ítem no genera ganancia")
    void margen_precioPublicoCero_cobraElPrecioAlComerciante() {
        item.setPrecioPublico(BigDecimal.ZERO);
        when(esquemaLiquidacionService.vigente()).thenReturn(esquema(TipoEsquemaLiquidacion.MARGEN, null));

        LiquidacionResponseDTO response = liquidacionService.liquidarPlanilla(planilla.getId(), devolver(0, null), usuario);

        assertThat(response.montoCobrado()).isEqualByComparingTo("10000");
        assertThat(response.totalVendido()).isEqualByComparingTo("10000");
        assertThat(response.gananciaComerciante()).isNull();
        assertThat(item.getGananciaComerciante()).isNull();
        assertThat(planilla.getTotalGanancia()).isEqualByComparingTo("10000");
    }

    @Test
    @DisplayName("MARGEN con un ítem con precio al público y otro en 0: el segundo no resta cobro ni ganancia")
    void margen_planillaMixta_elItemSinPrecioPublicoNoResta() {
        item.setPrecioPublico(BigDecimal.valueOf(1500));
        ItemPlanilla sinPrecio = ItemPlanilla.builder()
                .id(UUID.randomUUID()).planilla(planilla)
                .unidadesDespachadas(20).unidadesDevueltas(0)
                .precioVenta(BigDecimal.valueOf(2400)).precioPublico(BigDecimal.ZERO)
                .build();
        when(itemPlanillaRepository.findByIdAndPlanillaId(sinPrecio.getId(), planilla.getId()))
                .thenReturn(Optional.of(sinPrecio));
        when(esquemaLiquidacionService.vigente()).thenReturn(esquema(TipoEsquemaLiquidacion.MARGEN, null));

        LiquidacionResponseDTO response = liquidacionService.liquidarPlanilla(planilla.getId(),
                new LiquidacionRequestDTO(List.of(
                        new DevolucionItemDTO(item.getId(), 0),
                        new DevolucionItemDTO(sinPrecio.getId(), 0)), null), usuario);

        // 10 * 1000 + 20 * 2400 = 58000 cobrado; solo el primer ítem da ganancia: 10 * 500
        assertThat(response.montoCobrado()).isEqualByComparingTo("58000");
        assertThat(response.gananciaComerciante()).isEqualByComparingTo("5000");
        assertThat(response.totalVendido()).isEqualByComparingTo("63000");
    }

    @Test
    @DisplayName("COMISION con precio al público en 0: usa el precio al comerciante e informa la ganancia descontada")
    void comision_precioPublicoCero_usaElPrecioAlComerciante() {
        item.setPrecioPublico(BigDecimal.ZERO);
        when(esquemaLiquidacionService.vigente())
                .thenReturn(esquema(TipoEsquemaLiquidacion.COMISION, BigDecimal.valueOf(20)));

        LiquidacionResponseDTO response = liquidacionService.liquidarPlanilla(planilla.getId(), devolver(0, null), usuario);

        assertThat(response.gananciaComerciante()).isEqualByComparingTo("2000");
        assertThat(response.montoCobrado()).isEqualByComparingTo("8000");
        assertThat(planilla.getGananciaComerciante()).isEqualByComparingTo("2000");
    }

    @Test
    @DisplayName("sin efectivoRecibido indicado, se asume pago completo (igual al monto cobrado)")
    void sinEfectivoRecibido_asumePagoCompleto() {
        when(esquemaLiquidacionService.vigente()).thenReturn(esquema(TipoEsquemaLiquidacion.MARGEN, BigDecimal.ZERO));

        LiquidacionResponseDTO response = liquidacionService.liquidarPlanilla(planilla.getId(), devolver(0, null), usuario);

        assertThat(response.efectivoRecibido()).isEqualByComparingTo(response.montoCobrado());
        assertThat(planilla.getEfectivoRecibido()).isEqualByComparingTo(response.montoCobrado());
        assertThat(response.saldo()).isEqualByComparingTo("0");
        assertThat(response.fechaLimitePago()).isNull();
    }

    @Test
    @DisplayName("con efectivoRecibido parcial, queda saldo y se fija la fecha límite de pago")
    void conEfectivoRecibidoParcial_quedaSaldoYFechaLimite() {
        when(esquemaLiquidacionService.vigente()).thenReturn(esquema(TipoEsquemaLiquidacion.MARGEN, BigDecimal.ZERO));

        LiquidacionResponseDTO response = liquidacionService.liquidarPlanilla(
                planilla.getId(), devolver(0, BigDecimal.valueOf(4000)), usuario);

        assertThat(response.montoCobrado()).isEqualByComparingTo("10000");
        assertThat(response.efectivoRecibido()).isEqualByComparingTo("4000");
        assertThat(response.saldo()).isEqualByComparingTo("6000");
        assertThat(response.fechaLimitePago()).isEqualTo(LocalDate.now().plusDays(5));
        assertThat(planilla.getFechaLimitePago()).isEqualTo(LocalDate.now().plusDays(5));
    }

    @Test
    @DisplayName("efectivoRecibido mayor que el total cobrado: 400 y la planilla sigue abierta")
    void efectivoMayorQueElTotal_lanza400() {
        when(esquemaLiquidacionService.vigente()).thenReturn(esquema(TipoEsquemaLiquidacion.MARGEN, null));

        assertThatThrownBy(() -> liquidacionService.liquidarPlanilla(
                planilla.getId(), devolver(0, new BigDecimal("10000.01")), usuario))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));

        assertThat(planilla.getCerrada()).isFalse();
        verify(planillaRepository, never()).save(any());
        verify(itemPlanillaRepository, never()).save(any());
    }

    @Test
    @DisplayName("efectivoRecibido igual al total (con otra escala decimal) se acepta")
    void efectivoIgualAlTotal_seAcepta() {
        when(esquemaLiquidacionService.vigente()).thenReturn(esquema(TipoEsquemaLiquidacion.MARGEN, null));

        LiquidacionResponseDTO response = liquidacionService.liquidarPlanilla(
                planilla.getId(), devolver(0, new BigDecimal("10000.00")), usuario);

        assertThat(response.saldo()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("efectivoRecibido negativo no pasa la validación de la petición")
    void efectivoNegativo_noPasaLaValidacion() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            assertThat(factory.getValidator().validate(devolver(0, BigDecimal.valueOf(-1))))
                    .extracting(v -> v.getPropertyPath().toString())
                    .containsExactly("efectivoRecibido");
        }
    }
}
