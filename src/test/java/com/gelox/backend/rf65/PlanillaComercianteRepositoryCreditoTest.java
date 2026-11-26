package com.gelox.backend.rf65;

import com.gelox.backend.entities.Comerciante;
import com.gelox.backend.entities.PlanillaComerciante;
import com.gelox.backend.entities.RolUsuario;
import com.gelox.backend.entities.Usuario;
import com.gelox.backend.repositories.PlanillaComercianteRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RF65 — findCerradasPorComercianteOrdenadas devuelve las planillas cerradas
 * de la más antigua a la más reciente: es el orden que CreditoService usa
 * para repartir los abonos (FIFO), espejo de distribuirAbono() del frontend.
 */
@DataJpaTest(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"
})
class PlanillaComercianteRepositoryCreditoTest {

    static final UUID TENANT = UUID.randomUUID();

    @Autowired PlanillaComercianteRepository planillaRepository;
    @Autowired EntityManager em;

    Comerciante comerciante;
    Usuario usuario;

    @BeforeEach
    void setUp() {
        em.createNativeQuery("SET REFERENTIAL_INTEGRITY FALSE").executeUpdate();

        comerciante = em.merge(Comerciante.builder().tenantId(TENANT).nombre("Doña Rosa").activo(true).build());
        usuario = em.merge(Usuario.builder().tenantId(TENANT).firebaseUid(UUID.randomUUID().toString())
                .nombre("Vendedor").correo(UUID.randomUUID() + "@gelox-test.com")
                .rol(RolUsuario.ENCARGADO_VENTAS).activo(true).build());
        em.flush();
    }

    PlanillaComerciante planilla(LocalDate fecha, boolean cerrada) {
        return em.merge(PlanillaComerciante.builder()
                .tenantId(TENANT).comerciante(comerciante).usuario(usuario)
                .fecha(fecha).cerrada(cerrada).totalGanancia(BigDecimal.valueOf(10000))
                .build());
    }

    @Test
    @DisplayName("sin planillas, devuelve una lista vacía")
    void sinPlanillas_listaVacia() {
        assertThat(planillaRepository.findCerradasPorComercianteOrdenadas(comerciante.getId())).isEmpty();
    }

    @Test
    @DisplayName("devuelve las planillas cerradas de la más antigua a la más reciente")
    void devuelveLasCerradas_delaMasAntiguaALaMasReciente() {
        PlanillaComerciante reciente = planilla(LocalDate.now().minusDays(1), true);
        PlanillaComerciante antigua  = planilla(LocalDate.now().minusDays(10), true);
        PlanillaComerciante media    = planilla(LocalDate.now().minusDays(5), true);
        em.flush();

        List<PlanillaComerciante> resultado = planillaRepository.findCerradasPorComercianteOrdenadas(comerciante.getId());

        assertThat(resultado).extracting(PlanillaComerciante::getId)
                .containsExactly(antigua.getId(), media.getId(), reciente.getId());
    }

    @Test
    @DisplayName("una planilla abierta (no cerrada) no aparece en el resultado")
    void planillaAbierta_noAparece() {
        planilla(LocalDate.now(), false);
        em.flush();

        assertThat(planillaRepository.findCerradasPorComercianteOrdenadas(comerciante.getId())).isEmpty();
    }
}
