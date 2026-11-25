package com.gelox.backend.rf65;

import com.gelox.backend.TestHelper;
import com.gelox.backend.credito.CreditoService;
import com.gelox.backend.credito.dto.AbonoRequest;
import com.gelox.backend.credito.dto.CreditoVistaDTO;
import com.gelox.backend.credito.dto.PoliticaCreditoDTO;
import com.gelox.backend.credito.dto.PoliticaCreditoRequest;
import com.gelox.backend.entities.AbonoComerciante;
import com.gelox.backend.entities.Comerciante;
import com.gelox.backend.entities.PlanillaComerciante;
import com.gelox.backend.entities.PoliticaCredito;
import com.gelox.backend.entities.PoliticaVencimiento;
import com.gelox.backend.entities.RolUsuario;
import com.gelox.backend.entities.Usuario;
import com.gelox.backend.repositories.AbonoComercianteRepository;
import com.gelox.backend.repositories.ComercianteRepository;
import com.gelox.backend.repositories.PlanillaComercianteRepository;
import com.gelox.backend.repositories.PoliticaCreditoRepository;
import com.gelox.backend.services.EventoSistemaService;
import com.gelox.backend.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * RF65 — Política de crédito (por defecto y excepciones), saldo por planilla
 * con reparto FIFO de abonos y evaluación de crédito al despachar (FP-15
 * §2.17.1). Contrato alineado con SeccionCredito.jsx / creditoService.js.
 */
@ExtendWith(MockitoExtension.class)
class CreditoServiceTest {

    private static final UUID TENANT = UUID.randomUUID();

    @Mock PoliticaCreditoRepository politicaRepository;
    @Mock AbonoComercianteRepository abonoRepository;
    @Mock PlanillaComercianteRepository planillaRepository;
    @Mock ComercianteRepository comercianteRepository;
    @Mock EventoSistemaService eventoSistemaService;

    @InjectMocks
    CreditoService creditoService;

    private Comerciante comerciante;
    private Usuario usuario;

    @BeforeEach
    void setUp() {
        TenantContext.set(TENANT);
        comerciante = Comerciante.builder().id(UUID.randomUUID()).tenantId(TENANT).nombre("Doña Rosa").activo(true).build();
        usuario = TestHelper.buildUsuario("uid-ventas", "ventas@gelox-test.com", RolUsuario.ENCARGADO_VENTAS, true);
        org.mockito.Mockito.lenient().when(comercianteRepository.findById(comerciante.getId())).thenReturn(Optional.of(comerciante));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private PoliticaCredito defecto(BigDecimal cupo, PoliticaVencimiento vencimiento) {
        return PoliticaCredito.builder().id(UUID.randomUUID()).tenantId(TENANT)
                .cupo(cupo).plazoDias(5).politicaVencimiento(vencimiento).build();
    }

    /** Planilla cerrada con saldo (sin abonos todavía) = valor − efectivoRecibido. */
    private PlanillaComerciante cerrada(LocalDate fecha, BigDecimal valor, BigDecimal efectivoRecibido, LocalDate fechaLimite) {
        return PlanillaComerciante.builder()
                .id(UUID.randomUUID()).tenantId(TENANT).comerciante(comerciante).usuario(usuario)
                .fecha(fecha).cerrada(true).totalGanancia(valor).efectivoRecibido(efectivoRecibido)
                .fechaLimitePago(fechaLimite).build();
    }

    private AbonoComerciante abono(BigDecimal valor) {
        return AbonoComerciante.builder().id(UUID.randomUUID()).comerciante(comerciante).valor(valor)
                .fecha(LocalDate.now()).usuario(usuario).build();
    }

    /** Para evaluar() / plazoDiasEfectivo(): no leen el historial de abonos (findByComerciante_IdOrderByFechaDesc). */
    private void sinPlanillasNiAbonos() {
        when(planillaRepository.findCerradasPorComercianteOrdenadas(comerciante.getId())).thenReturn(List.of());
        when(abonoRepository.findByComerciante_IdOrderByFechaAsc(comerciante.getId())).thenReturn(List.of());
    }

    /** Para obtenerCreditoComerciante() / definirExcepcion() / eliminarExcepcion() / registrarAbono(): la vista completa. */
    private void sinPlanillasNiAbonosVista() {
        sinPlanillasNiAbonos();
        when(abonoRepository.findByComerciante_IdOrderByFechaDesc(comerciante.getId())).thenReturn(List.of());
    }

    // ── Política por defecto ─────────────────────────────────────────────

    @Test
    @DisplayName("obtenerPoliticaDistribuidora devuelve la política por defecto de la distribuidora")
    void obtenerPolitica_devuelveLaPorDefecto() {
        when(politicaRepository.findByTenantIdAndComercianteIsNull(TENANT))
                .thenReturn(Optional.of(defecto(BigDecimal.valueOf(50000), PoliticaVencimiento.SOLO_ALERTA)));

        PoliticaCreditoDTO dto = creditoService.obtenerPoliticaDistribuidora();

        assertThat(dto.cupo()).isEqualByComparingTo("50000");
        assertThat(dto.politica()).isEqualTo(PoliticaVencimiento.SOLO_ALERTA);
    }

    @Test
    @DisplayName("sin política por defecto configurada, lanza 500")
    void obtenerPolitica_sinDefecto_lanza500() {
        when(politicaRepository.findByTenantIdAndComercianteIsNull(TENANT)).thenReturn(Optional.empty());

        assertThatThrownBy(creditoService::obtenerPoliticaDistribuidora)
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    @DisplayName("actualizarPoliticaDistribuidora sobrescribe cupo, plazo y política de vencimiento")
    void actualizarPolitica_sobrescribeLaPorDefecto() {
        PoliticaCredito existente = defecto(BigDecimal.valueOf(50000), PoliticaVencimiento.SOLO_ALERTA);
        when(politicaRepository.findByTenantIdAndComercianteIsNull(TENANT)).thenReturn(Optional.of(existente));
        when(politicaRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        PoliticaCreditoDTO dto = creditoService.actualizarPoliticaDistribuidora(
                new PoliticaCreditoRequest(BigDecimal.valueOf(100000), 10, PoliticaVencimiento.BLOQUEAR));

        assertThat(dto.cupo()).isEqualByComparingTo("100000");
        assertThat(dto.plazoDias()).isEqualTo(10);
        assertThat(dto.politica()).isEqualTo(PoliticaVencimiento.BLOQUEAR);
    }

    // ── Vista de crédito del comerciante ────────────────────────────────

    @Test
    @DisplayName("sin planillas ni abonos, el comerciante usa la política por defecto y no tiene deuda")
    void obtenerCredito_sinPlanillas_sinDeuda() {
        when(politicaRepository.findByTenantIdAndComerciante_Id(TENANT, comerciante.getId())).thenReturn(Optional.empty());
        when(politicaRepository.findByTenantIdAndComercianteIsNull(TENANT))
                .thenReturn(Optional.of(defecto(BigDecimal.valueOf(50000), PoliticaVencimiento.SOLO_ALERTA)));
        sinPlanillasNiAbonosVista();

        CreditoVistaDTO vista = creditoService.obtenerCreditoComerciante(comerciante.getId());

        assertThat(vista.origen()).isEqualTo("DEFECTO");
        assertThat(vista.deudaActual()).isEqualByComparingTo("0");
        assertThat(vista.cupoDisponible()).isEqualByComparingTo("50000");
        assertThat(vista.planillas()).isEmpty();
    }

    @Test
    @DisplayName("la deuda es la suma de los saldos de las planillas cerradas sin abonos")
    void obtenerCredito_deudaEsLaSumaDeSaldos() {
        when(politicaRepository.findByTenantIdAndComerciante_Id(TENANT, comerciante.getId())).thenReturn(Optional.empty());
        when(politicaRepository.findByTenantIdAndComercianteIsNull(TENANT))
                .thenReturn(Optional.of(defecto(BigDecimal.valueOf(50000), PoliticaVencimiento.SOLO_ALERTA)));
        PlanillaComerciante p1 = cerrada(LocalDate.now().minusDays(10), BigDecimal.valueOf(30000), BigDecimal.ZERO, LocalDate.now().plusDays(5));
        PlanillaComerciante p2 = cerrada(LocalDate.now().minusDays(5), BigDecimal.valueOf(20000), BigDecimal.ZERO, LocalDate.now().plusDays(10));
        when(planillaRepository.findCerradasPorComercianteOrdenadas(comerciante.getId())).thenReturn(List.of(p1, p2));
        when(abonoRepository.findByComerciante_IdOrderByFechaAsc(comerciante.getId())).thenReturn(List.of());
        when(abonoRepository.findByComerciante_IdOrderByFechaDesc(comerciante.getId())).thenReturn(List.of());

        CreditoVistaDTO vista = creditoService.obtenerCreditoComerciante(comerciante.getId());

        assertThat(vista.deudaActual()).isEqualByComparingTo("50000");
        assertThat(vista.planillas()).hasSize(2);
    }

    @Test
    @DisplayName("un abono se aplica primero a la planilla más antigua (FIFO), como distribuirAbono() del frontend")
    void obtenerCredito_abono_seAplicaALaPlanillaMasAntiguaPrimero() {
        when(politicaRepository.findByTenantIdAndComerciante_Id(TENANT, comerciante.getId())).thenReturn(Optional.empty());
        when(politicaRepository.findByTenantIdAndComercianteIsNull(TENANT))
                .thenReturn(Optional.of(defecto(BigDecimal.valueOf(50000), PoliticaVencimiento.SOLO_ALERTA)));
        PlanillaComerciante antigua = cerrada(LocalDate.now().minusDays(10), BigDecimal.valueOf(30000), BigDecimal.ZERO, LocalDate.now().plusDays(5));
        PlanillaComerciante reciente = cerrada(LocalDate.now().minusDays(1), BigDecimal.valueOf(20000), BigDecimal.ZERO, LocalDate.now().plusDays(10));
        when(planillaRepository.findCerradasPorComercianteOrdenadas(comerciante.getId())).thenReturn(List.of(antigua, reciente));
        when(abonoRepository.findByComerciante_IdOrderByFechaAsc(comerciante.getId())).thenReturn(List.of(abono(BigDecimal.valueOf(35000))));
        when(abonoRepository.findByComerciante_IdOrderByFechaDesc(comerciante.getId())).thenReturn(List.of());

        CreditoVistaDTO vista = creditoService.obtenerCreditoComerciante(comerciante.getId());

        // El abono de 35000 saja por completo la planilla antigua (30000) y deja 5000 en la reciente (20000-5000=15000)
        assertThat(vista.deudaActual()).isEqualByComparingTo("15000");
        assertThat(vista.planillas()).hasSize(1);
        assertThat(vista.planillas().get(0).planillaId()).isEqualTo(reciente.getId());
        assertThat(vista.planillas().get(0).saldo()).isEqualByComparingTo("15000");
    }

    @Test
    @DisplayName("una planilla con saldo y fecha límite vencida se marca vencida, con sus días de mora")
    void obtenerCredito_planillaVencida_marcaDiasDeMora() {
        when(politicaRepository.findByTenantIdAndComerciante_Id(TENANT, comerciante.getId())).thenReturn(Optional.empty());
        when(politicaRepository.findByTenantIdAndComercianteIsNull(TENANT))
                .thenReturn(Optional.of(defecto(BigDecimal.valueOf(50000), PoliticaVencimiento.BLOQUEAR)));
        PlanillaComerciante vencida = cerrada(LocalDate.now().minusDays(20), BigDecimal.valueOf(10000), BigDecimal.ZERO, LocalDate.now().minusDays(3));
        when(planillaRepository.findCerradasPorComercianteOrdenadas(comerciante.getId())).thenReturn(List.of(vencida));
        when(abonoRepository.findByComerciante_IdOrderByFechaAsc(comerciante.getId())).thenReturn(List.of());
        when(abonoRepository.findByComerciante_IdOrderByFechaDesc(comerciante.getId())).thenReturn(List.of());

        CreditoVistaDTO vista = creditoService.obtenerCreditoComerciante(comerciante.getId());

        assertThat(vista.planillas().get(0).vencida()).isTrue();
        assertThat(vista.planillas().get(0).diasMora()).isEqualTo(3L);
    }

    // ── Excepción por comerciante ────────────────────────────────────────

    @Test
    @DisplayName("definirExcepcion crea una política propia y la vista queda en origen EXCEPCION")
    void definirExcepcion_creaPoliticaPropia() {
        // 1ª lectura (dentro de definirExcepcion): no existe excepción todavía.
        // 2ª lectura (dentro de vistaDe, para origen): ya existe, la recién creada.
        when(politicaRepository.findByTenantIdAndComerciante_Id(TENANT, comerciante.getId())).thenReturn(
                Optional.empty(),
                Optional.of(PoliticaCredito.builder().comerciante(comerciante)
                        .cupo(BigDecimal.valueOf(200000)).plazoDias(15).politicaVencimiento(PoliticaVencimiento.BLOQUEAR).build()));
        when(politicaRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(politicaRepository.findByTenantIdAndComercianteIsNull(TENANT))
                .thenReturn(Optional.of(defecto(BigDecimal.valueOf(50000), PoliticaVencimiento.SOLO_ALERTA)));
        sinPlanillasNiAbonosVista();

        CreditoVistaDTO vista = creditoService.definirExcepcion(comerciante.getId(),
                new PoliticaCreditoRequest(BigDecimal.valueOf(200000), 15, PoliticaVencimiento.BLOQUEAR));

        assertThat(vista.origen()).isEqualTo("EXCEPCION");
        assertThat(vista.cupo()).isEqualByComparingTo("200000");
        assertThat(vista.politicaDefecto().cupo()).isEqualByComparingTo("50000");
    }

    @Test
    @DisplayName("eliminarExcepcion borra la política propia y la vista vuelve a origen DEFECTO")
    void eliminarExcepcion_vuelveAOrigenDefecto() {
        PoliticaCredito excepcion = PoliticaCredito.builder().id(UUID.randomUUID()).comerciante(comerciante).build();
        when(politicaRepository.findByTenantIdAndComerciante_Id(TENANT, comerciante.getId()))
                .thenReturn(Optional.of(excepcion), Optional.empty());
        when(politicaRepository.findByTenantIdAndComercianteIsNull(TENANT))
                .thenReturn(Optional.of(defecto(BigDecimal.valueOf(50000), PoliticaVencimiento.SOLO_ALERTA)));
        sinPlanillasNiAbonosVista();

        CreditoVistaDTO vista = creditoService.eliminarExcepcion(comerciante.getId());

        verify(politicaRepository).delete(excepcion);
        assertThat(vista.origen()).isEqualTo("DEFECTO");
    }

    // ── Evaluación de crédito al despachar ──────────────────────────────

    @Test
    @DisplayName("sin deuda ni planillas vencidas, el comerciante no excede el crédito")
    void evaluar_sinDeuda_noExcede() {
        when(politicaRepository.findByTenantIdAndComerciante_Id(TENANT, comerciante.getId())).thenReturn(Optional.empty());
        when(politicaRepository.findByTenantIdAndComercianteIsNull(TENANT))
                .thenReturn(Optional.of(defecto(BigDecimal.valueOf(50000), PoliticaVencimiento.BLOQUEAR)));
        sinPlanillasNiAbonos();

        CreditoService.Evaluacion evaluacion = creditoService.evaluar(comerciante.getId());

        assertThat(evaluacion.excede()).isFalse();
        assertThat(evaluacion.advertencias()).isEmpty();
    }

    @Test
    @DisplayName("deuda mayor al cupo: excede y la advertencia es CUPO_SUPERADO")
    void evaluar_deudaMayorAlCupo_advierteCupoSuperado() {
        when(politicaRepository.findByTenantIdAndComerciante_Id(TENANT, comerciante.getId())).thenReturn(Optional.empty());
        when(politicaRepository.findByTenantIdAndComercianteIsNull(TENANT))
                .thenReturn(Optional.of(defecto(BigDecimal.valueOf(5000), PoliticaVencimiento.SOLO_ALERTA)));
        PlanillaComerciante p = cerrada(LocalDate.now().minusDays(1), BigDecimal.valueOf(8000), BigDecimal.ZERO, LocalDate.now().plusDays(5));
        when(planillaRepository.findCerradasPorComercianteOrdenadas(comerciante.getId())).thenReturn(List.of(p));
        when(abonoRepository.findByComerciante_IdOrderByFechaAsc(comerciante.getId())).thenReturn(List.of());

        CreditoService.Evaluacion evaluacion = creditoService.evaluar(comerciante.getId());

        assertThat(evaluacion.excede()).isTrue();
        assertThat(evaluacion.advertencias()).extracting("codigo").containsExactly("CUPO_SUPERADO");
    }

    @Test
    @DisplayName("una planilla vencida advierte PLANILLA_VENCIDA aunque no se supere el cupo")
    void evaluar_planillaVencida_advierteSinSuperarCupo() {
        when(politicaRepository.findByTenantIdAndComerciante_Id(TENANT, comerciante.getId())).thenReturn(Optional.empty());
        when(politicaRepository.findByTenantIdAndComercianteIsNull(TENANT))
                .thenReturn(Optional.of(defecto(BigDecimal.valueOf(50000), PoliticaVencimiento.BLOQUEAR)));
        PlanillaComerciante vencida = cerrada(LocalDate.now().minusDays(20), BigDecimal.valueOf(1000), BigDecimal.ZERO, LocalDate.now().minusDays(1));
        when(planillaRepository.findCerradasPorComercianteOrdenadas(comerciante.getId())).thenReturn(List.of(vencida));
        when(abonoRepository.findByComerciante_IdOrderByFechaAsc(comerciante.getId())).thenReturn(List.of());

        CreditoService.Evaluacion evaluacion = creditoService.evaluar(comerciante.getId());

        assertThat(evaluacion.excede()).isTrue();
        assertThat(evaluacion.advertencias()).extracting("codigo").containsExactly("PLANILLA_VENCIDA");
    }

    @Test
    @DisplayName("evaluar usa la excepción del comerciante en vez de la política por defecto, si existe")
    void evaluar_usaLaExcepcionDelComerciante() {
        PoliticaCredito excepcion = PoliticaCredito.builder().id(UUID.randomUUID()).tenantId(TENANT)
                .comerciante(comerciante).cupo(BigDecimal.valueOf(500000)).plazoDias(30)
                .politicaVencimiento(PoliticaVencimiento.SOLO_ALERTA).build();
        when(politicaRepository.findByTenantIdAndComerciante_Id(TENANT, comerciante.getId())).thenReturn(Optional.of(excepcion));
        PlanillaComerciante p = cerrada(LocalDate.now().minusDays(1), BigDecimal.valueOf(80000), BigDecimal.ZERO, LocalDate.now().plusDays(10));
        when(planillaRepository.findCerradasPorComercianteOrdenadas(comerciante.getId())).thenReturn(List.of(p));
        when(abonoRepository.findByComerciante_IdOrderByFechaAsc(comerciante.getId())).thenReturn(List.of());

        CreditoService.Evaluacion evaluacion = creditoService.evaluar(comerciante.getId());

        assertThat(evaluacion.excede()).isFalse();
        assertThat(evaluacion.cupo()).isEqualByComparingTo("500000");
    }

    @Test
    @DisplayName("plazoDiasEfectivo devuelve el plazo de la excepción si existe, o el de la política por defecto")
    void plazoDiasEfectivo_usaExcepcionOPorDefecto() {
        when(politicaRepository.findByTenantIdAndComerciante_Id(TENANT, comerciante.getId())).thenReturn(Optional.empty());
        when(politicaRepository.findByTenantIdAndComercianteIsNull(TENANT))
                .thenReturn(Optional.of(defecto(BigDecimal.valueOf(50000), PoliticaVencimiento.SOLO_ALERTA)));

        assertThat(creditoService.plazoDiasEfectivo(comerciante.getId())).isEqualTo(5);
    }

    // ── Abonos ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("registrarAbono guarda el abono, dispara el evento del dashboard y devuelve la vista actualizada")
    void registrarAbono_guardaYNotifica() {
        when(politicaRepository.findByTenantIdAndComerciante_Id(TENANT, comerciante.getId())).thenReturn(Optional.empty());
        when(politicaRepository.findByTenantIdAndComercianteIsNull(TENANT))
                .thenReturn(Optional.of(defecto(BigDecimal.valueOf(50000), PoliticaVencimiento.SOLO_ALERTA)));
        PlanillaComerciante p = cerrada(LocalDate.now().minusDays(1), BigDecimal.valueOf(30000), BigDecimal.ZERO, LocalDate.now().plusDays(5));
        when(planillaRepository.findCerradasPorComercianteOrdenadas(comerciante.getId())).thenReturn(List.of(p));
        when(abonoRepository.findByComerciante_IdOrderByFechaAsc(comerciante.getId())).thenReturn(List.of());
        when(abonoRepository.findByComerciante_IdOrderByFechaDesc(comerciante.getId())).thenReturn(List.of(abono(BigDecimal.valueOf(20000))));
        when(abonoRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CreditoVistaDTO vista = creditoService.registrarAbono(comerciante.getId(),
                new AbonoRequest(BigDecimal.valueOf(20000), "Pago parcial"), usuario);

        assertThat(vista.abonos()).hasSize(1);
        verify(eventoSistemaService).registrarEvento(any(), any(), any());
    }

    @Test
    @DisplayName("registrarAbono con un valor mayor a la deuda actual lanza 400")
    void registrarAbono_mayorQueLaDeuda_lanza400() {
        sinPlanillasNiAbonos();

        assertThatThrownBy(() -> creditoService.registrarAbono(comerciante.getId(),
                new AbonoRequest(BigDecimal.valueOf(1000), null), usuario))
                .isInstanceOf(ResponseStatusException.class);
    }
}
