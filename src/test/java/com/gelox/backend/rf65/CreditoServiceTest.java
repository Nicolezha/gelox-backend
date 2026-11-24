package com.gelox.backend.rf65;

import com.gelox.backend.TestHelper;
import com.gelox.backend.credito.CreditoService;
import com.gelox.backend.credito.dto.AbonoRequest;
import com.gelox.backend.credito.dto.CreditoComercianteDTO;
import com.gelox.backend.credito.dto.PoliticaCreditoDTO;
import com.gelox.backend.credito.dto.PoliticaCreditoRequest;
import com.gelox.backend.entities.AbonoComerciante;
import com.gelox.backend.entities.Comerciante;
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
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * RF65 — Política de crédito (por defecto y excepciones), deuda/planillas
 * vencidas del comerciante y abonos (FP-15 §2.17.1).
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

    @BeforeEach
    void setUp() {
        TenantContext.set(TENANT);
        comerciante = Comerciante.builder().id(UUID.randomUUID()).tenantId(TENANT).nombre("Doña Rosa").activo(true).build();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private PoliticaCredito defecto(BigDecimal cupo, PoliticaVencimiento vencimiento) {
        return PoliticaCredito.builder().id(UUID.randomUUID()).tenantId(TENANT)
                .cupo(cupo).plazoDias(5).politicaVencimiento(vencimiento).build();
    }

    // ── Política por defecto ─────────────────────────────────────────────

    @Test
    @DisplayName("obtenerPoliticaDistribuidora devuelve la política por defecto de la distribuidora")
    void obtenerPolitica_devuelveLaPorDefecto() {
        when(politicaRepository.findByTenantIdAndComercianteIsNull(TENANT))
                .thenReturn(Optional.of(defecto(BigDecimal.valueOf(50000), PoliticaVencimiento.SOLO_ALERTA)));

        PoliticaCreditoDTO dto = creditoService.obtenerPoliticaDistribuidora();

        assertThat(dto.cupo()).isEqualByComparingTo("50000");
        assertThat(dto.esExcepcion()).isFalse();
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
        assertThat(dto.politicaVencimiento()).isEqualTo(PoliticaVencimiento.BLOQUEAR);
    }

    // ── Excepción por comerciante ────────────────────────────────────────

    @Test
    @DisplayName("definirExcepcion crea una política propia para el comerciante")
    void definirExcepcion_creaPoliticaPropia() {
        when(comercianteRepository.findById(comerciante.getId())).thenReturn(Optional.of(comerciante));
        when(politicaRepository.findByTenantIdAndComerciante_Id(TENANT, comerciante.getId())).thenReturn(Optional.empty());
        when(politicaRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        PoliticaCreditoDTO dto = creditoService.definirExcepcion(comerciante.getId(),
                new PoliticaCreditoRequest(BigDecimal.valueOf(200000), 15, PoliticaVencimiento.BLOQUEAR));

        assertThat(dto.esExcepcion()).isTrue();
        assertThat(dto.cupo()).isEqualByComparingTo("200000");
    }

    @Test
    @DisplayName("definirExcepcion para un comerciante de otro tenant lanza 404")
    void definirExcepcion_comercianteDeOtroTenant_lanza404() {
        Comerciante otro = Comerciante.builder().id(UUID.randomUUID()).tenantId(UUID.randomUUID()).build();
        when(comercianteRepository.findById(otro.getId())).thenReturn(Optional.of(otro));

        assertThatThrownBy(() -> creditoService.definirExcepcion(otro.getId(),
                new PoliticaCreditoRequest(BigDecimal.TEN, 1, PoliticaVencimiento.BLOQUEAR)))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    @DisplayName("eliminarExcepcion borra la política propia del comerciante si existe")
    void eliminarExcepcion_borraLaExcepcion() {
        when(comercianteRepository.findById(comerciante.getId())).thenReturn(Optional.of(comerciante));
        PoliticaCredito excepcion = PoliticaCredito.builder().id(UUID.randomUUID()).comerciante(comerciante).build();
        when(politicaRepository.findByTenantIdAndComerciante_Id(TENANT, comerciante.getId()))
                .thenReturn(Optional.of(excepcion));

        creditoService.eliminarExcepcion(comerciante.getId());

        verify(politicaRepository).delete(excepcion);
    }

    // ── Evaluación de crédito (deuda, vencidas, bloqueo) ────────────────

    @Test
    @DisplayName("sin deuda ni planillas vencidas, el comerciante no excede el crédito")
    void evaluar_sinDeuda_noExcede() {
        when(politicaRepository.findByTenantIdAndComerciante_Id(TENANT, comerciante.getId())).thenReturn(Optional.empty());
        when(politicaRepository.findByTenantIdAndComercianteIsNull(TENANT))
                .thenReturn(Optional.of(defecto(BigDecimal.valueOf(50000), PoliticaVencimiento.BLOQUEAR)));
        when(planillaRepository.sumarSaldoCerradas(comerciante.getId())).thenReturn(BigDecimal.ZERO);
        when(abonoRepository.sumarValorPorComerciante(comerciante.getId())).thenReturn(BigDecimal.ZERO);
        when(planillaRepository.contarVencidas(eq(comerciante.getId()), any())).thenReturn(0L);

        CreditoService.Evaluacion evaluacion = creditoService.evaluar(comerciante.getId());

        assertThat(evaluacion.excede()).isFalse();
        assertThat(evaluacion.deuda()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("la deuda es el saldo de las planillas cerradas menos los abonos, nunca negativa")
    void evaluar_deuda_restaAbonosSinQuedarNegativa() {
        when(politicaRepository.findByTenantIdAndComerciante_Id(TENANT, comerciante.getId())).thenReturn(Optional.empty());
        when(politicaRepository.findByTenantIdAndComercianteIsNull(TENANT))
                .thenReturn(Optional.of(defecto(BigDecimal.valueOf(50000), PoliticaVencimiento.SOLO_ALERTA)));
        when(planillaRepository.sumarSaldoCerradas(comerciante.getId())).thenReturn(BigDecimal.valueOf(30000));
        when(abonoRepository.sumarValorPorComerciante(comerciante.getId())).thenReturn(BigDecimal.valueOf(45000));
        when(planillaRepository.contarVencidas(eq(comerciante.getId()), any())).thenReturn(0L);

        CreditoService.Evaluacion evaluacion = creditoService.evaluar(comerciante.getId());

        assertThat(evaluacion.deuda()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("deuda mayor al cupo: excede, y con política BLOQUEAR el comerciante queda bloqueado")
    void evaluar_deudaMayorAlCupo_conBloquear_bloqueado() {
        when(politicaRepository.findByTenantIdAndComerciante_Id(TENANT, comerciante.getId())).thenReturn(Optional.empty());
        when(politicaRepository.findByTenantIdAndComercianteIsNull(TENANT))
                .thenReturn(Optional.of(defecto(BigDecimal.valueOf(50000), PoliticaVencimiento.BLOQUEAR)));
        when(planillaRepository.sumarSaldoCerradas(comerciante.getId())).thenReturn(BigDecimal.valueOf(80000));
        when(abonoRepository.sumarValorPorComerciante(comerciante.getId())).thenReturn(BigDecimal.ZERO);
        when(planillaRepository.contarVencidas(eq(comerciante.getId()), any())).thenReturn(0L);
        when(comercianteRepository.findById(comerciante.getId())).thenReturn(Optional.of(comerciante));

        CreditoComercianteDTO dto = creditoService.obtenerCreditoComerciante(comerciante.getId());

        assertThat(dto.deuda()).isEqualByComparingTo("80000");
        assertThat(dto.bloqueado()).isTrue();
    }

    @Test
    @DisplayName("deuda mayor al cupo con política SOLO_ALERTA: excede pero no queda bloqueado")
    void evaluar_deudaMayorAlCupo_conSoloAlerta_noBloqueado() {
        when(politicaRepository.findByTenantIdAndComerciante_Id(TENANT, comerciante.getId())).thenReturn(Optional.empty());
        when(politicaRepository.findByTenantIdAndComercianteIsNull(TENANT))
                .thenReturn(Optional.of(defecto(BigDecimal.valueOf(50000), PoliticaVencimiento.SOLO_ALERTA)));
        when(planillaRepository.sumarSaldoCerradas(comerciante.getId())).thenReturn(BigDecimal.valueOf(80000));
        when(abonoRepository.sumarValorPorComerciante(comerciante.getId())).thenReturn(BigDecimal.ZERO);
        when(planillaRepository.contarVencidas(eq(comerciante.getId()), any())).thenReturn(0L);
        when(comercianteRepository.findById(comerciante.getId())).thenReturn(Optional.of(comerciante));

        CreditoComercianteDTO dto = creditoService.obtenerCreditoComerciante(comerciante.getId());

        assertThat(dto.bloqueado()).isFalse();
    }

    @Test
    @DisplayName("una planilla vencida hace que el comerciante exceda aunque no supere el cupo")
    void evaluar_planillaVencida_excedeSinSuperarCupo() {
        when(politicaRepository.findByTenantIdAndComerciante_Id(TENANT, comerciante.getId())).thenReturn(Optional.empty());
        when(politicaRepository.findByTenantIdAndComercianteIsNull(TENANT))
                .thenReturn(Optional.of(defecto(BigDecimal.valueOf(50000), PoliticaVencimiento.BLOQUEAR)));
        when(planillaRepository.sumarSaldoCerradas(comerciante.getId())).thenReturn(BigDecimal.valueOf(1000));
        when(abonoRepository.sumarValorPorComerciante(comerciante.getId())).thenReturn(BigDecimal.ZERO);
        when(planillaRepository.contarVencidas(eq(comerciante.getId()), any())).thenReturn(1L);

        CreditoService.Evaluacion evaluacion = creditoService.evaluar(comerciante.getId());

        assertThat(evaluacion.excede()).isTrue();
        assertThat(evaluacion.planillasVencidas()).isEqualTo(1L);
    }

    @Test
    @DisplayName("evaluar usa la excepción del comerciante en vez de la política por defecto, si existe")
    void evaluar_usaLaExcepcionDelComerciante() {
        PoliticaCredito excepcion = PoliticaCredito.builder().id(UUID.randomUUID()).tenantId(TENANT)
                .comerciante(comerciante).cupo(BigDecimal.valueOf(500000)).plazoDias(30)
                .politicaVencimiento(PoliticaVencimiento.SOLO_ALERTA).build();
        when(politicaRepository.findByTenantIdAndComerciante_Id(TENANT, comerciante.getId())).thenReturn(Optional.of(excepcion));
        when(planillaRepository.sumarSaldoCerradas(comerciante.getId())).thenReturn(BigDecimal.valueOf(80000));
        when(abonoRepository.sumarValorPorComerciante(comerciante.getId())).thenReturn(BigDecimal.ZERO);
        when(planillaRepository.contarVencidas(eq(comerciante.getId()), any())).thenReturn(0L);

        CreditoService.Evaluacion evaluacion = creditoService.evaluar(comerciante.getId());

        assertThat(evaluacion.excede()).isFalse();
        assertThat(evaluacion.politica().cupo()).isEqualByComparingTo("500000");
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
    @DisplayName("registrarAbono guarda el abono, dispara el evento del dashboard y lo retorna")
    void registrarAbono_guardaYNotifica() {
        Usuario usuario = TestHelper.buildUsuario("uid-ventas", "ventas@gelox-test.com", RolUsuario.ENCARGADO_VENTAS, true);
        when(comercianteRepository.findById(comerciante.getId())).thenReturn(Optional.of(comerciante));
        when(abonoRepository.save(any())).thenAnswer(inv -> {
            AbonoComerciante a = inv.getArgument(0);
            a.setId(UUID.randomUUID());
            a.setFecha(LocalDate.now());
            return a;
        });

        var dto = creditoService.registrarAbono(comerciante.getId(),
                new AbonoRequest(BigDecimal.valueOf(20000), null, "Pago parcial"), usuario);

        assertThat(dto.valor()).isEqualByComparingTo("20000");
        verify(eventoSistemaService).registrarEvento(any(), any(), any());
    }

    @Test
    @DisplayName("registrarAbono con planillaId de otro tenant lanza 404")
    void registrarAbono_planillaDeOtroTenant_lanza404() {
        Usuario usuario = TestHelper.buildUsuario("uid-ventas", "ventas@gelox-test.com", RolUsuario.ENCARGADO_VENTAS, true);
        when(comercianteRepository.findById(comerciante.getId())).thenReturn(Optional.of(comerciante));
        when(planillaRepository.findById(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> creditoService.registrarAbono(comerciante.getId(),
                new AbonoRequest(BigDecimal.valueOf(1000), UUID.randomUUID(), null), usuario))
                .isInstanceOf(ResponseStatusException.class);
    }
}
