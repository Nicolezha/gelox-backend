package com.gelox.backend.rf70;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gelox.backend.entities.Distribuidora;
import com.gelox.backend.entities.EstadoDistribuidora;
import com.gelox.backend.exceptions.CampoInvalidoException;
import com.gelox.backend.gobierno.Auditoria;
import com.gelox.backend.gobierno.AuditoriaAspect;
import com.gelox.backend.gobierno.AuditoriaEscritor;
import com.gelox.backend.gobierno.AuditoriaService;
import com.gelox.backend.gobierno.CuotaService;
import com.gelox.backend.gobierno.PlanService;
import com.gelox.backend.gobierno.PlanSuscripcion;
import com.gelox.backend.gobierno.PlanSuscripcionRepository;
import com.gelox.backend.gobierno.dto.PlanDTO;
import com.gelox.backend.gobierno.dto.PlanRequest;
import com.gelox.backend.repositories.DistribuidoraRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * RF70 — CRUD de planes (/api/plataforma/planes): nombre único (409), precio y cuotas no
 * negativos (400, con el campo), null = sin límite, no se borra un plan asignado (409),
 * y detalle de auditoría PLAN_CREAR / PLAN_EDITAR con la forma del mock de T193.
 */
@ExtendWith(MockitoExtension.class)
class PlanServiceTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final UUID BASICO = UUID.randomUUID();
    private static final UUID ESTANDAR = UUID.randomUUID();

    @Mock PlanSuscripcionRepository planRepository;
    @Mock DistribuidoraRepository distribuidoraRepository;
    @Mock CuotaService cuotaService;
    @Mock AuditoriaEscritor escritor;

    private PlanService planService;
    private final List<PlanSuscripcion> planes = new ArrayList<>();

    @BeforeEach
    void setUp() {
        planes.add(plan(BASICO, "Básico", "149000", 5, 150));
        planes.add(plan(ESTANDAR, "Estándar", "289000", 15, 600));
        lenient().when(planRepository.findAll()).thenAnswer(inv -> List.copyOf(planes));
        lenient().when(planRepository.findById(any())).thenAnswer(inv ->
                planes.stream().filter(p -> p.getId().equals(inv.getArgument(0))).findFirst());
        lenient().when(planRepository.save(any(PlanSuscripcion.class))).thenAnswer(inv -> {
            PlanSuscripcion p = inv.getArgument(0);
            if (p.getId() == null) p.setId(UUID.randomUUID());
            return p;
        });

        // PlanService real detrás del aspecto de auditoría real (el escritor es el mock)
        AspectJProxyFactory factory = new AspectJProxyFactory(
                new PlanService(planRepository, distribuidoraRepository, cuotaService));
        factory.setProxyTargetClass(true);
        factory.addAspect(new AuditoriaAspect(new AuditoriaService(escritor)));
        planService = factory.getProxy();
    }

    private static PlanSuscripcion plan(UUID id, String nombre, String precio, Integer maxUsuarios, Integer maxProductos) {
        return PlanSuscripcion.builder().id(id).nombre(nombre).precioMensual(new BigDecimal(precio))
                .maxUsuarios(maxUsuarios).maxProductos(maxProductos).build();
    }

    private static PlanRequest request(String nombre, String precio, Integer maxUsuarios, Integer maxProductos,
                                       Integer maxComerciantes, Integer maxMb, Integer maxIa) {
        return new PlanRequest(nombre, "Plan de prueba", precio == null ? null : new BigDecimal(precio),
                maxUsuarios, maxProductos, maxComerciantes, maxMb, maxIa);
    }

    private JsonNode detalleAuditado() throws Exception {
        ArgumentCaptor<Auditoria> captor = ArgumentCaptor.forClass(Auditoria.class);
        verify(escritor).guardar(captor.capture());
        return JSON.readTree(captor.getValue().getDetalle());
    }

    private static CampoInvalidoException errorDeCampo(Runnable accion) {
        Throwable t = catchThrowable(accion::run);
        assertThat(t).isInstanceOf(CampoInvalidoException.class);
        return (CampoInvalidoException) t;
    }

    // ── POST ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("POST: crea el plan (null = sin límite) y audita PLAN_CREAR {plan, precioMensual, cuotas}")
    void crearPlan() throws Exception {
        PlanDTO creado = planService.crear(request("  Premium ", "499000", 40, 2000, null, 20480, 8000));

        assertThat(creado.id()).isNotNull();
        assertThat(creado.nombre()).isEqualTo("Premium");
        assertThat(creado.maxComerciantes()).isNull();
        assertThat(creado.totalDistribuidoras()).isZero();

        ArgumentCaptor<Auditoria> captor = ArgumentCaptor.forClass(Auditoria.class);
        verify(escritor).guardar(captor.capture());
        Auditoria fila = captor.getValue();
        assertThat(fila.getAccion()).isEqualTo("PLAN_CREAR");
        assertThat(fila.getEntidad()).isEqualTo("PlanSuscripcion");
        assertThat(fila.getEntidadId()).isEqualTo(creado.id());
        JsonNode detalle = JSON.readTree(fila.getDetalle());
        assertThat(detalle.get("plan").asText()).isEqualTo("Premium");
        assertThat(detalle.get("precioMensual").asInt()).isEqualTo(499000);
        assertThat(detalle.get("cuotas").get("maxUsuarios").asInt()).isEqualTo(40);
        assertThat(detalle.get("cuotas").get("maxComerciantes").isNull()).isTrue();
    }

    @Test
    @DisplayName("POST: nombre repetido (sin distinguir mayúsculas ni tildes) → 409 {campo: nombre}, sin fila")
    void nombreRepetido409() {
        CampoInvalidoException e = errorDeCampo(() -> planService.crear(request("ESTANDAR", "1", 1, 1, 1, 1, 1)));

        assertThat(e.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(e.getCampo()).isEqualTo("nombre");
        verify(planRepository, never()).save(any());
        verify(escritor, never()).guardar(any());
    }

    @Test
    @DisplayName("POST: cuota negativa → 400 con el campo de la cuota")
    void cuotaNegativa400() {
        CampoInvalidoException e = errorDeCampo(() -> planService.crear(request("Nuevo", "1000", 3, 5, 4, -1, 20)));

        assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(e.getCampo()).isEqualTo("maxAlmacenamientoMb");
        verify(planRepository, never()).save(any());
    }

    @Test
    @DisplayName("POST: precio negativo u omitido → 400 {campo: precioMensual}")
    void precioInvalido400() {
        assertThat(errorDeCampo(() -> planService.crear(request("Nuevo", "-1", null, null, null, null, null)))
                .getCampo()).isEqualTo("precioMensual");
        assertThat(errorDeCampo(() -> planService.crear(request("Nuevo", null, null, null, null, null, null)))
                .getCampo()).isEqualTo("precioMensual");
    }

    @Test
    @DisplayName("POST: nombre vacío o de más de 30 caracteres → 400 {campo: nombre}")
    void nombreInvalido400() {
        assertThat(errorDeCampo(() -> planService.crear(request("  ", "0", null, null, null, null, null)))
                .getCampo()).isEqualTo("nombre");
        assertThat(errorDeCampo(() -> planService.crear(request("x".repeat(31), "0", null, null, null, null, null)))
                .getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // ── PUT ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("PUT: guarda los valores y audita PLAN_EDITAR {plan, cambios: [{campo, antes, despues}]}")
    void editarPlan() throws Exception {
        PlanDTO editado = planService.editar(ESTANDAR, request("Estándar", "289000", 15, 500, null, null, null));

        assertThat(editado.maxProductos()).isEqualTo(500);
        JsonNode detalle = detalleAuditado();
        assertThat(detalle.get("plan").asText()).isEqualTo("Estándar");
        JsonNode cambio = detalle.get("cambios").get(1);   // [0] es la descripción (null → "Plan de prueba")
        assertThat(cambio.get("campo").asText()).isEqualTo("maxProductos");
        assertThat(cambio.get("antes").asInt()).isEqualTo(600);
        assertThat(cambio.get("despues").asInt()).isEqualTo(500);
    }

    @Test
    @DisplayName("PUT: conservar su propio nombre no es repetido; otro nombre existente → 409")
    void editarNombre() {
        planService.editar(BASICO, request("básico", "149000", 5, 150, null, null, null));

        CampoInvalidoException e = errorDeCampo(() ->
                planService.editar(BASICO, request("Estandar", "149000", 5, 150, null, null, null)));
        assertThat(e.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    @DisplayName("PUT: al renombrar, distribuidora.plan (texto) de las asignadas toma el nombre nuevo")
    void renombrarActualizaTexto() {
        Distribuidora d = Distribuidora.builder().id(UUID.randomUUID()).plan("Básico").planId(BASICO)
                .estado(EstadoDistribuidora.ACTIVO).createdAt(LocalDateTime.now()).build();
        when(distribuidoraRepository.findByPlanId(BASICO)).thenReturn(List.of(d));

        planService.editar(BASICO, request("Básico Plus", "159000", 5, 150, null, null, null));

        assertThat(d.getPlan()).isEqualTo("Básico Plus");
        verify(distribuidoraRepository).saveAll(List.of(d));
    }

    @Test
    @DisplayName("PUT/GET de un plan inexistente → 404")
    void planInexistente404() {
        Throwable t = catchThrowable(() -> planService.obtener(UUID.randomUUID()));
        assertThat(((ResponseStatusException) t).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        t = catchThrowable(() -> planService.editar(UUID.randomUUID(), request("X", "0", null, null, null, null, null)));
        assertThat(((ResponseStatusException) t).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ── GET ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("GET: lista por precio con totalDistribuidoras y el uso (solo números) de cada una")
    void listarPlanes() {
        UUID activa = UUID.randomUUID();
        UUID inactiva = UUID.randomUUID();
        when(distribuidoraRepository.findByPlanId(BASICO)).thenReturn(List.of(
                Distribuidora.builder().id(inactiva).nombreComercial("Polo Andino").planId(BASICO)
                        .estado(EstadoDistribuidora.INACTIVO).createdAt(LocalDateTime.now().minusDays(200)).build(),
                Distribuidora.builder().id(activa).nombreComercial("Frío Express").planId(BASICO)
                        .estado(EstadoDistribuidora.ACTIVO).createdAt(LocalDateTime.now()).build()));
        when(cuotaService.usados(any())).thenReturn(Map.of("productos", 60));
        planes.add(0, plan(UUID.randomUUID(), "Premium", "499000", 40, 2000));

        List<PlanDTO> lista = planService.listar();

        assertThat(lista).extracting(PlanDTO::nombre).containsExactly("Básico", "Estándar", "Premium");
        PlanDTO basico = lista.get(0);
        assertThat(basico.totalDistribuidoras()).isEqualTo(2);
        assertThat(basico.distribuidoras()).extracting(PlanDTO.DistribuidoraDelPlan::id)
                .containsExactly(activa, inactiva);   // las inactivas al final
        assertThat(basico.distribuidoras().get(0).uso()).containsEntry("productos", 60);
        verify(escritor, never()).guardar(any());     // consultar no se audita
    }

    // ── DELETE ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("DELETE: plan con distribuidoras asignadas → 409 y no se borra")
    void eliminarAsignado409() {
        when(distribuidoraRepository.existsByPlanId(BASICO)).thenReturn(true);

        Throwable t = catchThrowable(() -> planService.eliminar(BASICO));

        assertThat(((ResponseStatusException) t).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        verify(planRepository, never()).delete(any());
    }

    @Test
    @DisplayName("DELETE: plan sin distribuidoras se borra")
    void eliminarLibre() {
        when(distribuidoraRepository.existsByPlanId(ESTANDAR)).thenReturn(false);

        planService.eliminar(ESTANDAR);

        verify(planRepository).delete(planes.get(1));
    }

    @Test
    @DisplayName("cuotasDe(plan): las cinco cuotas con los nombres del contrato")
    void cuotasDelContrato() {
        assertThat(PlanService.cuotasDe(planes.get(0))).containsOnlyKeys(
                "maxUsuarios", "maxProductos", "maxComerciantes", "maxAlmacenamientoMb", "maxLlamadasIaMes");
        assertThat(Optional.ofNullable(PlanService.cuotasDe(planes.get(0)).get("maxComerciantes"))).isEmpty();
    }
}
