package com.gelox.backend.rf70;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gelox.backend.TestHelper;
import com.gelox.backend.entities.Distribuidora;
import com.gelox.backend.entities.EstadoDistribuidora;
import com.gelox.backend.entities.RolUsuario;
import com.gelox.backend.entities.Usuario;
import com.gelox.backend.exceptions.CampoInvalidoException;
import com.gelox.backend.gobierno.Auditoria;
import com.gelox.backend.gobierno.AuditoriaAspect;
import com.gelox.backend.gobierno.AuditoriaEscritor;
import com.gelox.backend.gobierno.AuditoriaRepository;
import com.gelox.backend.gobierno.AuditoriaService;
import com.gelox.backend.gobierno.CuotaService;
import com.gelox.backend.gobierno.CuotaService.CuotaExcedida;
import com.gelox.backend.gobierno.CuotaService.UsoCuota;
import com.gelox.backend.gobierno.PlanSuscripcion;
import com.gelox.backend.gobierno.PlanSuscripcionRepository;
import com.gelox.backend.gobierno.TipoCuota;
import com.gelox.backend.plataforma.DistribuidoraService;
import com.gelox.backend.plataforma.dto.AsignarPlanRequest;
import com.gelox.backend.plataforma.dto.CrearDistribuidoraRequest;
import com.gelox.backend.plataforma.dto.DistribuidoraDTO;
import com.gelox.backend.repositories.DistribuidoraRepository;
import com.gelox.backend.repositories.UsuarioRepository;
import com.gelox.backend.services.EmailService;
import com.gelox.backend.services.EventoSistemaService;
import com.gelox.backend.tenant.DistribuidoraEstadoCache;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * RF70 — PATCH /api/plataforma/distribuidoras/{id}/plan {planId, motivo}, alta con planId y
 * detalle con el uso de cuotas (FP-15 §2.16.2; nombres del mock de T193).
 */
@ExtendWith(MockitoExtension.class)
class AsignarPlanTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final UUID ID = UUID.randomUUID();
    private static final UUID BASICO = UUID.randomUUID();
    private static final UUID ESTANDAR = UUID.randomUUID();

    @Mock DistribuidoraRepository distribuidoraRepository;
    @Mock UsuarioRepository usuarioRepository;
    @Mock EventoSistemaService eventoSistemaService;
    @Mock EmailService emailService;
    @Mock DistribuidoraEstadoCache distribuidoraEstadoCache;
    @Mock PlanSuscripcionRepository planRepository;
    @Mock CuotaService cuotaService;
    @Mock AuditoriaRepository auditoriaRepository;
    @Mock AuditoriaEscritor escritor;

    private DistribuidoraService servicio;
    private Distribuidora distribuidora;
    private PlanSuscripcion estandar;

    @BeforeEach
    void setUp() {
        Usuario superadmin = TestHelper.buildUsuario("uid-super", "super@gelox.app", RolUsuario.SUPERADMINISTRADOR, true);
        SecurityContextHolder.getContext().setAuthentication(TestHelper.authParaUsuario(superadmin));

        Usuario gerente = TestHelper.buildUsuario("uid-g", "gerencia@frioexpress.co", RolUsuario.ADMINISTRADOR, true);
        gerente.setNombre("Ana Gerente");
        distribuidora = Distribuidora.builder().id(ID).nombreComercial("Frío Express").plan("Básico").planId(BASICO)
                .estado(EstadoDistribuidora.ACTIVO).gerenteTitular(gerente)
                .createdAt(LocalDateTime.of(2026, 5, 1, 9, 0)).build();
        estandar = PlanSuscripcion.builder().id(ESTANDAR).nombre("Estándar").maxUsuarios(15).maxProductos(500).build();

        lenient().when(distribuidoraRepository.findById(ID)).thenReturn(Optional.of(distribuidora));
        lenient().when(distribuidoraRepository.save(any(Distribuidora.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(planRepository.findById(BASICO))
                .thenReturn(Optional.of(PlanSuscripcion.builder().id(BASICO).nombre("Básico").build()));
        lenient().when(planRepository.findById(ESTANDAR)).thenReturn(Optional.of(estandar));

        DistribuidoraService real = new DistribuidoraService(distribuidoraRepository, usuarioRepository,
                eventoSistemaService, emailService, distribuidoraEstadoCache, planRepository, cuotaService,
                auditoriaRepository);
        AspectJProxyFactory factory = new AspectJProxyFactory(real);
        factory.setProxyTargetClass(true);
        factory.addAspect(new AuditoriaAspect(new AuditoriaService(escritor)));
        servicio = factory.getProxy();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private Auditoria filaAuditada() {
        ArgumentCaptor<Auditoria> captor = ArgumentCaptor.forClass(Auditoria.class);
        verify(escritor).guardar(captor.capture());
        return captor.getValue();
    }

    private static HttpStatus estado(Runnable accion) {
        Throwable t = catchThrowable(accion::run);
        assertThat(t).isInstanceOf(ResponseStatusException.class);
        return HttpStatus.valueOf(((ResponseStatusException) t).getStatusCode().value());
    }

    @Test
    @DisplayName("Asigna el plan, llena plan (texto) y audita PLAN_ASIGNAR {planAnterior, planNuevo, motivo, cuotas}")
    void asignarPlan() throws Exception {
        when(cuotaService.excedidas(ID, estandar)).thenReturn(List.of());
        Map<String, UsoCuota> uso = new LinkedHashMap<>();
        uso.put("productos", new UsoCuota(120L, 500));
        when(cuotaService.uso(ID, estandar)).thenReturn(uso);

        DistribuidoraDTO dto = servicio.asignarPlan(ID, new AsignarPlanRequest(ESTANDAR, " Creció en catálogo "));

        assertThat(distribuidora.getPlanId()).isEqualTo(ESTANDAR);
        assertThat(distribuidora.getPlan()).isEqualTo("Estándar");
        assertThat(dto.planId()).isEqualTo(ESTANDAR);
        assertThat(dto.planNombre()).isEqualTo("Estándar");
        assertThat(dto.plan()).isEqualTo("Estándar");
        assertThat(dto.fechaPlan()).isAfter(distribuidora.getCreatedAt());
        assertThat(dto.uso()).containsKey("productos");
        assertThat(dto.advertencias()).isNull();

        Auditoria fila = filaAuditada();
        assertThat(fila.getAccion()).isEqualTo("PLAN_ASIGNAR");
        assertThat(fila.getEntidad()).isEqualTo("Distribuidora");
        assertThat(fila.getEntidadId()).isEqualTo(ID);
        assertThat(fila.getTenantId()).isNull();   // Superadministrador
        JsonNode detalle = JSON.readTree(fila.getDetalle());
        assertThat(detalle.get("planAnterior").asText()).isEqualTo("Básico");
        assertThat(detalle.get("planNuevo").asText()).isEqualTo("Estándar");
        assertThat(detalle.get("motivo").asText()).isEqualTo("Creció en catálogo");
        assertThat(detalle.get("cuotas").get("maxProductos").asInt()).isEqualTo(500);
    }

    @Test
    @DisplayName("Uso que ya supera una cuota del plan nuevo: se asigna igual y trae advertencias")
    void asignarConAdvertencia() {
        List<CuotaExcedida> excedidas = List.of(new CuotaExcedida(TipoCuota.PRODUCTOS, 534L, 500));
        when(cuotaService.excedidas(ID, estandar)).thenReturn(excedidas);

        DistribuidoraDTO dto = servicio.asignarPlan(ID, new AsignarPlanRequest(ESTANDAR, "Baja de plan"));

        assertThat(distribuidora.getPlanId()).isEqualTo(ESTANDAR);
        assertThat(dto.advertencias()).containsExactly(new CuotaExcedida(TipoCuota.PRODUCTOS, 534L, 500));
    }

    @Test
    @DisplayName("Mismo plan → 409; distribuidora inactiva → 409; sin fila de auditoría")
    void conflictos409() {
        assertThat(estado(() -> servicio.asignarPlan(ID, new AsignarPlanRequest(BASICO, "x"))))
                .isEqualTo(HttpStatus.CONFLICT);

        distribuidora.setEstado(EstadoDistribuidora.INACTIVO);
        assertThat(estado(() -> servicio.asignarPlan(ID, new AsignarPlanRequest(ESTANDAR, "x"))))
                .isEqualTo(HttpStatus.CONFLICT);

        verify(distribuidoraRepository, never()).save(any());
        verify(escritor, never()).guardar(any());
    }

    @Test
    @DisplayName("Sin motivo o sin planId → 400 con el campo; plan inexistente → 404; distribuidora inexistente → 404")
    void validaciones() {
        Throwable t = catchThrowable(() -> servicio.asignarPlan(ID, new AsignarPlanRequest(ESTANDAR, "  ")));
        assertThat(((CampoInvalidoException) t).getCampo()).isEqualTo("motivo");

        t = catchThrowable(() -> servicio.asignarPlan(ID, new AsignarPlanRequest(null, "motivo")));
        assertThat(((CampoInvalidoException) t).getCampo()).isEqualTo("planId");

        assertThat(estado(() -> servicio.asignarPlan(ID, new AsignarPlanRequest(UUID.randomUUID(), "motivo"))))
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(estado(() -> servicio.asignarPlan(UUID.randomUUID(), new AsignarPlanRequest(ESTANDAR, "m"))))
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("GET detalle: planId, planNombre, plan, fechaPlan (último PLAN_ASIGNAR) y uso {usado, limite}")
    void detalleConUso() {
        LocalDateTime asignado = LocalDateTime.of(2026, 11, 20, 10, 30);
        when(auditoriaRepository.findFirstByAccionAndEntidadIdOrderByFechaHoraDesc("PLAN_ASIGNAR", ID))
                .thenReturn(Optional.of(Auditoria.builder().fechaHora(asignado).build()));
        when(usuarioRepository.countByTenantIdAndActivoTrue(ID)).thenReturn(4L);
        Map<String, UsoCuota> uso = new LinkedHashMap<>();
        uso.put("usuarios", new UsoCuota(4L, 5));
        uso.put("almacenamientoMb", new UsoCuota(new java.math.BigDecimal("310.50"), 1024));
        when(cuotaService.uso(eq(ID), any())).thenReturn(uso);

        DistribuidoraDTO dto = servicio.obtener(ID);

        assertThat(dto.planId()).isEqualTo(BASICO);
        assertThat(dto.planNombre()).isEqualTo("Básico");
        assertThat(dto.plan()).isEqualTo("Básico");
        assertThat(dto.fechaPlan()).isEqualTo(asignado);
        assertThat(dto.nombreGerente()).isEqualTo("Ana Gerente");
        assertThat(dto.usuariosActivos()).isEqualTo(4L);
        assertThat(dto.uso().get("usuarios")).isEqualTo(new UsoCuota(4L, 5));
        verify(escritor, never()).guardar(any());
    }

    @Test
    @DisplayName("Sin PLAN_ASIGNAR en el log, fechaPlan es la fecha de alta")
    void fechaPlanPorDefecto() {
        when(auditoriaRepository.findFirstByAccionAndEntidadIdOrderByFechaHoraDesc("PLAN_ASIGNAR", ID))
                .thenReturn(Optional.empty());

        assertThat(servicio.obtener(ID).fechaPlan()).isEqualTo(distribuidora.getCreatedAt());
    }

    @Test
    @DisplayName("Alta con un planId que no existe → 400 {campo: planId} antes de crear nada")
    void altaConPlanInexistente() {
        CrearDistribuidoraRequest req = new CrearDistribuidoraRequest("Nueva S.A.S.", "900111222", "Nueva", "nueva",
                "Gerente", "g@nueva.co", null, null, UUID.randomUUID());

        Throwable t = catchThrowable(() -> servicio.aprovisionar(req));

        assertThat(t).isInstanceOf(CampoInvalidoException.class);
        assertThat(((CampoInvalidoException) t).getCampo()).isEqualTo("planId");
        verify(distribuidoraRepository, never()).save(any());
    }
}
