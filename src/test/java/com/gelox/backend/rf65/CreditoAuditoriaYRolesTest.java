package com.gelox.backend.rf65;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gelox.backend.TestHelper;
import com.gelox.backend.credito.CreditoService;
import com.gelox.backend.credito.dto.AbonoRequest;
import com.gelox.backend.credito.dto.PoliticaCreditoRequest;
import com.gelox.backend.dto.AdvertenciaCreditoDTO;
import com.gelox.backend.entities.AbonoComerciante;
import com.gelox.backend.entities.Comerciante;
import com.gelox.backend.entities.PlanillaComerciante;
import com.gelox.backend.entities.PoliticaCredito;
import com.gelox.backend.entities.PoliticaVencimiento;
import com.gelox.backend.entities.RolUsuario;
import com.gelox.backend.entities.Usuario;
import com.gelox.backend.gobierno.Auditoria;
import com.gelox.backend.gobierno.AuditoriaAspect;
import com.gelox.backend.gobierno.AuditoriaEscritor;
import com.gelox.backend.gobierno.AuditoriaService;
import com.gelox.backend.repositories.AbonoComercianteRepository;
import com.gelox.backend.repositories.ComercianteRepository;
import com.gelox.backend.repositories.PlanillaComercianteRepository;
import com.gelox.backend.repositories.PoliticaCreditoRepository;
import com.gelox.backend.security.RolVerificacionAspect;
import com.gelox.backend.services.EventoSistemaService;
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
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

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

/** RF65 / RF71 — quién registra abonos y qué queda en la auditoría de crédito (FP-15 §2.16.3 y §2.17.1). */
@ExtendWith(MockitoExtension.class)
class CreditoAuditoriaYRolesTest {

    private static final UUID TENANT = UUID.randomUUID();

    @Mock PoliticaCreditoRepository politicaRepository;
    @Mock AbonoComercianteRepository abonoRepository;
    @Mock PlanillaComercianteRepository planillaRepository;
    @Mock ComercianteRepository comercianteRepository;
    @Mock EventoSistemaService eventoSistemaService;
    @Mock AuditoriaEscritor escritor;

    CreditoService service;
    Comerciante comerciante;

    @BeforeEach
    void setUp() {
        TenantContext.set(TENANT);
        comerciante = Comerciante.builder().id(UUID.randomUUID()).tenantId(TENANT).nombre("Juan").activo(true).build();
        AspectJProxyFactory factory = new AspectJProxyFactory(new CreditoService(politicaRepository, abonoRepository,
                planillaRepository, comercianteRepository, eventoSistemaService));
        factory.setProxyTargetClass(true);
        factory.addAspect(new RolVerificacionAspect());
        factory.addAspect(new AuditoriaAspect(new AuditoriaService(escritor)));
        service = factory.getProxy();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    private Usuario como(RolUsuario rol) {
        Usuario usuario = TestHelper.buildUsuario("uid-" + rol, rol + "@gelox.test", rol, true);
        SecurityContextHolder.getContext().setAuthentication(TestHelper.authParaUsuario(usuario));
        return usuario;
    }

    private static PoliticaCredito politica(String cupo, int plazo, PoliticaVencimiento vencimiento) {
        return PoliticaCredito.builder().id(UUID.randomUUID()).tenantId(TENANT)
                .cupo(new BigDecimal(cupo)).plazoDias(plazo).politicaVencimiento(vencimiento).build();
    }

    private JsonNode detalleAuditado(String accion) throws Exception {
        ArgumentCaptor<Auditoria> captor = ArgumentCaptor.forClass(Auditoria.class);
        verify(escritor).guardar(captor.capture());
        assertThat(captor.getValue().getAccion()).isEqualTo(accion);
        return new ObjectMapper().readTree(captor.getValue().getDetalle());
    }

    @Test
    @DisplayName("el Encargado de Ventas no registra abonos: 403 y nada se guarda ni se audita")
    void abonoDeVentas_403() {
        Usuario ventas = como(RolUsuario.ENCARGADO_VENTAS);

        assertThatThrownBy(() -> service.registrarAbono(comerciante.getId(),
                new AbonoRequest(BigDecimal.TEN, null), ventas))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));

        verify(abonoRepository, never()).save(any());
        verify(escritor, never()).guardar(any());
    }

    @Test
    @DisplayName("el abono del Gerente queda auditado con el comerciante y el valor")
    void abonoDelGerente_auditado() throws Exception {
        Usuario gerente = como(RolUsuario.ADMINISTRADOR);
        when(comercianteRepository.findById(comerciante.getId())).thenReturn(Optional.of(comerciante));
        when(planillaRepository.findCerradasPorComercianteOrdenadas(comerciante.getId())).thenReturn(List.of(
                PlanillaComerciante.builder().id(UUID.randomUUID()).fecha(LocalDate.now()).cerrada(true)
                        .totalGanancia(BigDecimal.valueOf(30000)).efectivoRecibido(BigDecimal.ZERO).build()));
        when(abonoRepository.findByComerciante_IdOrderByFechaAsc(comerciante.getId())).thenReturn(List.of());
        when(abonoRepository.findByComerciante_IdOrderByFechaDesc(comerciante.getId())).thenReturn(List.of());
        when(abonoRepository.save(any(AbonoComerciante.class))).thenAnswer(inv -> inv.getArgument(0));
        when(politicaRepository.findByTenantIdAndComercianteIsNull(TENANT))
                .thenReturn(Optional.of(politica("50000", 0, PoliticaVencimiento.SOLO_ALERTA)));

        service.registrarAbono(comerciante.getId(), new AbonoRequest(BigDecimal.valueOf(25000), null), gerente);

        JsonNode detalle = detalleAuditado("ABONO_REGISTRAR");
        assertThat(detalle.get("comerciante").asText()).isEqualTo("Juan");
        assertThat(detalle.get("valor").asInt()).isEqualTo(25000);
    }

    @Test
    @DisplayName("cambiar la política por defecto guarda cupo, plazo y política anteriores y nuevos")
    void politicaPorDefecto_auditada() throws Exception {
        Usuario gerente = como(RolUsuario.ADMINISTRADOR);
        when(politicaRepository.findByTenantIdAndComercianteIsNull(TENANT))
                .thenReturn(Optional.of(politica("50000", 0, PoliticaVencimiento.SOLO_ALERTA)));
        when(politicaRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.actualizarPoliticaDistribuidora(
                new PoliticaCreditoRequest(BigDecimal.valueOf(80000), 3, PoliticaVencimiento.BLOQUEAR), gerente);

        JsonNode detalle = detalleAuditado("CREDITO_POLITICA_CAMBIAR");
        assertThat(detalle.get("antes").get("cupo").asInt()).isEqualTo(50000);
        assertThat(detalle.get("antes").get("plazoDias").asInt()).isZero();
        assertThat(detalle.get("antes").get("politica").asText()).isEqualTo("SOLO_ALERTA");
        assertThat(detalle.get("despues").get("cupo").asInt()).isEqualTo(80000);
        assertThat(detalle.get("despues").get("plazoDias").asInt()).isEqualTo(3);
        assertThat(detalle.get("despues").get("politica").asText()).isEqualTo("BLOQUEAR");
    }

    @Test
    @DisplayName("quitar la excepción guarda la que regía y la política por defecto que queda")
    void quitarExcepcion_auditada() throws Exception {
        como(RolUsuario.ADMINISTRADOR);
        when(comercianteRepository.findById(comerciante.getId())).thenReturn(Optional.of(comerciante));
        when(politicaRepository.findByTenantIdAndComerciante_Id(TENANT, comerciante.getId())).thenReturn(
                Optional.of(politica("100000", 2, PoliticaVencimiento.BLOQUEAR)), Optional.empty());
        when(politicaRepository.findByTenantIdAndComercianteIsNull(TENANT))
                .thenReturn(Optional.of(politica("50000", 0, PoliticaVencimiento.SOLO_ALERTA)));
        when(planillaRepository.findCerradasPorComercianteOrdenadas(comerciante.getId())).thenReturn(List.of());
        when(abonoRepository.findByComerciante_IdOrderByFechaAsc(comerciante.getId())).thenReturn(List.of());
        when(abonoRepository.findByComerciante_IdOrderByFechaDesc(comerciante.getId())).thenReturn(List.of());

        service.eliminarExcepcion(comerciante.getId());

        JsonNode detalle = detalleAuditado("CREDITO_POLITICA_CAMBIAR");
        assertThat(detalle.get("comerciante").asText()).isEqualTo("Juan");
        assertThat(detalle.get("antes").get("cupo").asInt()).isEqualTo(100000);
        assertThat(detalle.get("despues").get("cupo").asInt()).isEqualTo(50000);
    }

    @Test
    @DisplayName("las advertencias salen con «tipo» y «codigo»")
    void advertenciaConTipoYCodigo() throws Exception {
        JsonNode json = new ObjectMapper().valueToTree(new AdvertenciaCreditoDTO("CUPO_SUPERADO", "mensaje"));

        assertThat(json.get("tipo").asText()).isEqualTo("CUPO_SUPERADO");
        assertThat(json.get("codigo").asText()).isEqualTo("CUPO_SUPERADO");
        assertThat(json.get("mensaje").asText()).isEqualTo("mensaje");
    }
}
