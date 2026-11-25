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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RF65 — saldo y planillas vencidas de un comerciante (FP-15 §2.17.1): el
 * saldo de cada planilla cerrada es total_ganancia − efectivo_recibido, la
 * deuda es la suma de esos saldos, y una planilla está vencida si su saldo
 * es positivo y la fecha_limite_pago ya pasó.
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

    PlanillaComerciante planillaCerrada(BigDecimal totalGanancia, BigDecimal efectivoRecibido, LocalDate fechaLimitePago) {
        return em.merge(PlanillaComerciante.builder()
                .tenantId(TENANT).comerciante(comerciante).usuario(usuario)
                .fecha(LocalDate.now()).cerrada(true)
                .totalGanancia(totalGanancia).efectivoRecibido(efectivoRecibido)
                .fechaLimitePago(fechaLimitePago)
                .build());
    }

    @Test
    @DisplayName("sin planillas, el saldo es cero y no hay vencidas")
    void sinPlanillas_saldoCero() {
        assertThat(planillaRepository.sumarSaldoCerradas(comerciante.getId())).isEqualByComparingTo("0");
        assertThat(planillaRepository.contarVencidas(comerciante.getId(), LocalDate.now())).isZero();
    }

    @Test
    @DisplayName("el saldo de varias planillas cerradas se suma (total_ganancia − efectivo_recibido)")
    void variasPlanillasCerradas_sumaLosSaldos() {
        planillaCerrada(BigDecimal.valueOf(50000), BigDecimal.valueOf(20000), LocalDate.now().plusDays(10));
        planillaCerrada(BigDecimal.valueOf(30000), BigDecimal.valueOf(30000), LocalDate.now().plusDays(10));
        em.flush();

        assertThat(planillaRepository.sumarSaldoCerradas(comerciante.getId())).isEqualByComparingTo("30000");
    }

    @Test
    @DisplayName("una planilla abierta (no cerrada) no entra en el saldo")
    void planillaAbierta_noSuma() {
        em.merge(PlanillaComerciante.builder()
                .tenantId(TENANT).comerciante(comerciante).usuario(usuario)
                .fecha(LocalDate.now()).cerrada(false)
                .totalGanancia(BigDecimal.valueOf(99999)).build());
        em.flush();

        assertThat(planillaRepository.sumarSaldoCerradas(comerciante.getId())).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("planilla con saldo positivo y fecha límite vencida cuenta como vencida")
    void saldoPositivoYFechaVencida_cuentaComoVencida() {
        planillaCerrada(BigDecimal.valueOf(50000), BigDecimal.ZERO, LocalDate.now().minusDays(1));
        em.flush();

        assertThat(planillaRepository.contarVencidas(comerciante.getId(), LocalDate.now())).isEqualTo(1);
    }

    @Test
    @DisplayName("planilla con saldo ya en cero, aunque la fecha límite pasó, no cuenta como vencida")
    void saldoEnCero_noCuentaComoVencidaAunqueLaFechaPaso() {
        planillaCerrada(BigDecimal.valueOf(50000), BigDecimal.valueOf(50000), LocalDate.now().minusDays(1));
        em.flush();

        assertThat(planillaRepository.contarVencidas(comerciante.getId(), LocalDate.now())).isZero();
    }

    @Test
    @DisplayName("planilla con saldo positivo pero fecha límite futura no cuenta como vencida todavía")
    void saldoPositivoConFechaFutura_noCuentaComoVencida() {
        planillaCerrada(BigDecimal.valueOf(50000), BigDecimal.ZERO, LocalDate.now().plusDays(5));
        em.flush();

        assertThat(planillaRepository.contarVencidas(comerciante.getId(), LocalDate.now())).isZero();
    }
}
