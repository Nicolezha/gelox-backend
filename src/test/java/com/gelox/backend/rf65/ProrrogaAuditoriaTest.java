package com.gelox.backend.rf65;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gelox.backend.dto.CambiarFechaLimiteRequest;
import com.gelox.backend.entities.PlanillaComerciante;
import com.gelox.backend.gobierno.Auditoria;
import com.gelox.backend.gobierno.AuditoriaAspect;
import com.gelox.backend.gobierno.AuditoriaEscritor;
import com.gelox.backend.gobierno.AuditoriaService;
import com.gelox.backend.repositories.PlanillaComercianteRepository;
import com.gelox.backend.services.PlanillaService;
import com.gelox.backend.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** RF65 / RF71 — la prórroga queda auditada con la fecha anterior, la nueva y el motivo (FP-15 §2.16.3). */
@ExtendWith(MockitoExtension.class)
class ProrrogaAuditoriaTest {

    private static final UUID TENANT = UUID.randomUUID();
    private static final LocalDate LIMITE = LocalDate.of(2026, 11, 20);

    @Mock PlanillaComercianteRepository planillaRepository;
    @Mock AuditoriaEscritor escritor;

    PlanillaService service;

    @BeforeEach
    void setUp() {
        TenantContext.set(TENANT);
        PlanillaService real = new PlanillaService(null, planillaRepository, null, null, null, null, null, null);
        AspectJProxyFactory factory = new AspectJProxyFactory(real);
        factory.setProxyTargetClass(true);
        factory.addAspect(new AuditoriaAspect(new AuditoriaService(escritor)));
        service = factory.getProxy();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private PlanillaComerciante cerrada(LocalDate fechaLimite) {
        PlanillaComerciante planilla = PlanillaComerciante.builder()
                .id(UUID.randomUUID()).tenantId(TENANT).cerrada(true).fechaLimitePago(fechaLimite).build();
        when(planillaRepository.findById(planilla.getId())).thenReturn(Optional.of(planilla));
        return planilla;
    }

    private Auditoria filaAuditada() {
        ArgumentCaptor<Auditoria> captor = ArgumentCaptor.forClass(Auditoria.class);
        verify(escritor).guardar(captor.capture());
        return captor.getValue();
    }

    @Test
    @DisplayName("la auditoría guarda fecha anterior, fecha nueva y motivo")
    void prorroga_guardaFechasYMotivo() throws Exception {
        PlanillaComerciante planilla = cerrada(LIMITE);

        service.cambiarFechaLimite(planilla.getId(),
                new CambiarFechaLimiteRequest(LIMITE.plusDays(5), "  Robo en la jornada  "));

        Auditoria fila = filaAuditada();
        assertThat(fila.getAccion()).isEqualTo("PLANILLA_CAMBIAR_FECHA_LIMITE");
        assertThat(fila.getEntidadId()).isEqualTo(planilla.getId());
        JsonNode detalle = new ObjectMapper().readTree(fila.getDetalle());
        assertThat(detalle.get("fechaAnterior").asText()).isEqualTo("2026-11-20");
        assertThat(detalle.get("fechaNueva").asText()).isEqualTo("2026-11-25");
        assertThat(detalle.get("motivo").asText()).isEqualTo("Robo en la jornada");
    }

    @Test
    @DisplayName("sin fecha límite previa, la fecha anterior queda en null")
    void prorroga_sinFechaPrevia_guardaNull() throws Exception {
        PlanillaComerciante planilla = cerrada(null);

        service.cambiarFechaLimite(planilla.getId(), new CambiarFechaLimiteRequest(LIMITE, "Acuerdo"));

        JsonNode detalle = new ObjectMapper().readTree(filaAuditada().getDetalle());
        assertThat(detalle.get("fechaAnterior").isNull()).isTrue();
        assertThat(detalle.get("fechaNueva").asText()).isEqualTo("2026-11-20");
    }

    @Test
    @DisplayName("una prórroga rechazada no deja fila de auditoría")
    void prorrogaRechazada_noSeAudita() {
        PlanillaComerciante planilla = cerrada(LIMITE);

        assertThatThrownBy(() -> service.cambiarFechaLimite(planilla.getId(),
                new CambiarFechaLimiteRequest(LIMITE.plusDays(5), " ")))
                .isInstanceOf(ResponseStatusException.class);

        verifyNoInteractions(escritor);
    }
}
