package com.gelox.backend.rf71;

import com.gelox.backend.entities.Distribuidora;
import com.gelox.backend.entities.MetodoClasificacion;
import com.gelox.backend.gobierno.CuotaAlcanzadaException;
import com.gelox.backend.gobierno.CuotaService;
import com.gelox.backend.gobierno.PlanSuscripcion;
import com.gelox.backend.gobierno.PlanSuscripcionRepository;
import com.gelox.backend.gobierno.TipoCuota;
import com.gelox.backend.gobierno.UsoAlmacenamientoService;
import com.gelox.backend.repositories.ComandoVozRepository;
import com.gelox.backend.repositories.ComercianteRepository;
import com.gelox.backend.repositories.DistribuidoraRepository;
import com.gelox.backend.repositories.ProductoRepository;
import com.gelox.backend.repositories.UsuarioRepository;
import com.gelox.backend.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * RF70 — Cada cuota del plan bloquea con 409 al llegar al límite (CP-70-04 … 06, CP-64-09)
 * y un plan sin máximos no limita (CP-70-07). Plan de prueba de QA: máx. 3 usuarios,
 * 5 productos, 4 comerciantes, 10 MB y 20 llamadas IA al mes.
 */
@ExtendWith(MockitoExtension.class)
class CuotaServiceTest {

    private static final UUID TENANT = UUID.randomUUID();
    private static final UUID PLAN = UUID.randomUUID();
    private static final long MB = 1024L * 1024L;

    @Mock DistribuidoraRepository distribuidoraRepository;
    @Mock PlanSuscripcionRepository planRepository;
    @Mock UsuarioRepository usuarioRepository;
    @Mock ProductoRepository productoRepository;
    @Mock ComercianteRepository comercianteRepository;
    @Mock ComandoVozRepository comandoVozRepository;
    @Mock UsoAlmacenamientoService usoAlmacenamientoService;

    @InjectMocks
    CuotaService cuotaService;

    @BeforeEach
    void setUp() {
        TenantContext.set(TENANT);
        org.mockito.Mockito.lenient().when(distribuidoraRepository.findById(TENANT))
                .thenReturn(Optional.of(Distribuidora.builder().id(TENANT).plan("PEQUENO").planId(PLAN).build()));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private void planPequeno() {
        when(planRepository.findById(PLAN)).thenReturn(Optional.of(PlanSuscripcion.builder()
                .id(PLAN).nombre("PEQUENO")
                .maxUsuarios(3).maxProductos(5).maxComerciantes(4)
                .maxAlmacenamientoMb(10).maxLlamadasIaMes(20)
                .build()));
    }

    private void planSinLimites() {
        when(planRepository.findById(PLAN)).thenReturn(Optional.of(PlanSuscripcion.builder()
                .id(PLAN).nombre("PILOTO").build()));
    }

    private static CuotaAlcanzadaException bloqueo(Runnable accion) {
        Throwable t = org.assertj.core.api.Assertions.catchThrowable(accion::run);
        assertThat(t).isInstanceOf(CuotaAlcanzadaException.class);
        CuotaAlcanzadaException e = (CuotaAlcanzadaException) t;
        assertThat(e.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(e.getReason()).startsWith("Cuota del plan alcanzada: ");
        return e;
    }

    // ── Usuarios ────────────────────────────────────────────────────────

    @Test
    @DisplayName("Usuarios: con 2 de 3 (gerente titular incluido) permite crear")
    void usuariosBajoLimite() {
        planPequeno();
        when(usuarioRepository.countByTenantIdAndActivoTrue(TENANT)).thenReturn(2L);

        assertThatCode(() -> cuotaService.validar(TipoCuota.USUARIOS)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("CP-70-04: con 3 usuarios y plan de máx. 3 → 409 {error, codigo, cuota, limite, usado}")
    void usuariosEnElLimite() {
        planPequeno();
        when(usuarioRepository.countByTenantIdAndActivoTrue(TENANT)).thenReturn(3L);

        CuotaAlcanzadaException e = bloqueo(() -> cuotaService.validar(TipoCuota.USUARIOS));

        Map<String, Object> cuerpo = e.cuerpo();
        assertThat(cuerpo).containsEntry("error", "Cuota del plan alcanzada: usuarios (3 de 3).")
                .containsEntry("codigo", "CUOTA_PLAN_ALCANZADA")
                .containsEntry("cuota", "USUARIOS")
                .containsEntry("limite", 3L)
                .containsEntry("usado", 3L);
    }

    // ── Productos ───────────────────────────────────────────────────────

    @Test
    @DisplayName("CP-64-09 / CP-70-05: con 5 productos y plan de máx. 5 → 409")
    void productosEnElLimite() {
        planPequeno();
        when(productoRepository.countByTenantIdAndActivoTrue(TENANT)).thenReturn(5L);

        assertThat(bloqueo(() -> cuotaService.validar(TipoCuota.PRODUCTOS)).getCuota()).isEqualTo(TipoCuota.PRODUCTOS);
    }

    @Test
    @DisplayName("Productos: con 4 de 5 permite crear")
    void productosBajoLimite() {
        planPequeno();
        when(productoRepository.countByTenantIdAndActivoTrue(TENANT)).thenReturn(4L);

        assertThatCode(() -> cuotaService.validar(TipoCuota.PRODUCTOS)).doesNotThrowAnyException();
    }

    // ── Comerciantes ────────────────────────────────────────────────────

    @Test
    @DisplayName("Comerciantes: con 4 y plan de máx. 4 → 409")
    void comerciantesEnElLimite() {
        planPequeno();
        when(comercianteRepository.countByTenantIdAndActivoTrue(TENANT)).thenReturn(4L);

        assertThat(bloqueo(() -> cuotaService.validar(TipoCuota.COMERCIANTES)).getUsado()).isEqualTo(4L);
    }

    @Test
    @DisplayName("Comerciantes: con 3 de 4 permite crear")
    void comerciantesBajoLimite() {
        planPequeno();
        when(comercianteRepository.countByTenantIdAndActivoTrue(TENANT)).thenReturn(3L);

        assertThatCode(() -> cuotaService.validar(TipoCuota.COMERCIANTES)).doesNotThrowAnyException();
    }

    // ── Almacenamiento ──────────────────────────────────────────────────

    @Test
    @DisplayName("CP-70-06: 9,5 MB usados + archivo de 1 MB contra 10 MB → 409 con usado en MB")
    void almacenamientoSuperado() {
        planPequeno();
        when(usoAlmacenamientoService.usadoBytes(TENANT)).thenReturn(9 * MB + MB / 2);

        CuotaAlcanzadaException e = bloqueo(() -> cuotaService.validar(TipoCuota.ALMACENAMIENTO_MB, MB));

        assertThat(e.getLimite()).isEqualTo(10L);
        assertThat(e.getUsado()).isEqualTo(new BigDecimal("9.50"));
    }

    @Test
    @DisplayName("Almacenamiento: 9 MB usados + 1 MB llega justo a 10 MB y se permite")
    void almacenamientoJustoEnElLimite() {
        planPequeno();
        when(usoAlmacenamientoService.usadoBytes(TENANT)).thenReturn(9 * MB);

        assertThatCode(() -> cuotaService.validar(TipoCuota.ALMACENAMIENTO_MB, MB)).doesNotThrowAnyException();
    }

    // ── Llamadas IA ─────────────────────────────────────────────────────

    @Test
    @DisplayName("IA: 20 comandos LLM este mes y plan de 20 → no disponible (el procesador usa el respaldo)")
    void llamadasIaAgotadas() {
        planPequeno();
        when(comandoVozRepository.countByTenantIdAndMetodoClasificacionAndCreatedAtGreaterThanEqual(
                eq(TENANT), eq(MetodoClasificacion.LLM), any(LocalDateTime.class))).thenReturn(20L);

        assertThat(cuotaService.disponible(TipoCuota.LLAMADAS_IA_MES)).isFalse();
    }

    @Test
    @DisplayName("IA: 19 de 20 → disponible")
    void llamadasIaDisponibles() {
        planPequeno();
        when(comandoVozRepository.countByTenantIdAndMetodoClasificacionAndCreatedAtGreaterThanEqual(
                eq(TENANT), eq(MetodoClasificacion.LLM), any(LocalDateTime.class))).thenReturn(19L);

        assertThat(cuotaService.disponible(TipoCuota.LLAMADAS_IA_MES)).isTrue();
    }

    // ── Sin límites ─────────────────────────────────────────────────────

    @Test
    @DisplayName("CP-70-07: plan migrado sin máximos no limita ni cuenta")
    void planSinLimite() {
        planSinLimites();

        for (TipoCuota tipo : TipoCuota.values()) {
            assertThatCode(() -> cuotaService.validar(tipo)).doesNotThrowAnyException();
        }
        assertThat(cuotaService.disponible(TipoCuota.LLAMADAS_IA_MES)).isTrue();
        verifyNoInteractions(usuarioRepository, productoRepository, comercianteRepository,
                comandoVozRepository, usoAlmacenamientoService);
    }

    @Test
    @DisplayName("Sin plan asignado no limita")
    void planNoEncontrado() {
        when(planRepository.findById(PLAN)).thenReturn(Optional.empty());

        assertThatCode(() -> cuotaService.validar(TipoCuota.PRODUCTOS)).doesNotThrowAnyException();
        verify(productoRepository, never()).countByTenantIdAndActivoTrue(any());
    }

    @Test
    @DisplayName("disponible() sin distribuidora en la petición no limita")
    void disponibleSinTenant() {
        TenantContext.clear();
        org.mockito.Mockito.reset(distribuidoraRepository);

        assertThat(cuotaService.disponible(TipoCuota.LLAMADAS_IA_MES)).isTrue();
        verifyNoInteractions(distribuidoraRepository);
    }

    @Test
    @DisplayName("Mes calendario en hora de Bogotá: el 30/11 a las 22:00 aún cuenta desde el 1/11")
    void inicioDelMesEnBogota() {
        // 2026-12-01T03:00Z = 30/11 22:00 en Bogotá (UTC-5)
        java.time.Instant ahora = java.time.Instant.parse("2026-12-01T03:00:00Z");

        assertThat(CuotaService.inicioMesBogota(ahora, java.time.ZoneOffset.UTC))
                .isEqualTo(LocalDateTime.of(2026, 11, 1, 5, 0));
        assertThat(CuotaService.inicioMesBogota(ahora, java.time.ZoneId.of("America/Bogota")))
                .isEqualTo(LocalDateTime.of(2026, 11, 1, 0, 0));
    }

    @Test
    @DisplayName("validar() sin distribuidora en la petición → 403")
    void validarSinTenant() {
        TenantContext.clear();
        org.mockito.Mockito.reset(distribuidoraRepository);

        assertThatThrownBy(() -> cuotaService.validar(TipoCuota.USUARIOS))
                .hasMessageContaining("Sin distribuidora");
    }
}
