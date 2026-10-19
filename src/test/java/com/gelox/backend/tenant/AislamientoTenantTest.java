package com.gelox.backend.tenant;

import com.gelox.backend.entities.Producto;
import com.gelox.backend.repositories.ProductoRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.aop.AopAutoConfiguration;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * T111 — Una distribuidora no ve los datos de otra (filtro de Hibernate + listener).
 * Flyway se desactiva: las migraciones son de PostgreSQL y H2 crea el esquema desde las entidades.
 */
@DataJpaTest(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"
})
@Import(TenantFilterAspect.class)
@ImportAutoConfiguration(AopAutoConfiguration.class)
class AislamientoTenantTest {

    private static final UUID TENANT_A = UUID.randomUUID();
    private static final UUID TENANT_B = UUID.randomUUID();

    @Autowired
    ProductoRepository productoRepository;

    @Autowired
    PlatformTransactionManager transactionManager;

    @BeforeEach
    void limpiarAntes() {
        TenantContext.clear();
    }

    @AfterEach
    void limpiarDespues() {
        TenantContext.clear();
    }

    private Producto producto(String codigo, UUID tenantId) {
        Producto p = new Producto();
        p.setCodigoTecnico(codigo);
        p.setNombre("Producto " + codigo);
        p.setPrecioVenta(BigDecimal.valueOf(3000));
        p.setPrecioCosto(BigDecimal.valueOf(2000));
        p.setTenantId(tenantId);
        return p;
    }

    private void crearDatosDeDosTenants() {
        productoRepository.saveAndFlush(producto("A-1", TENANT_A));
        productoRepository.saveAndFlush(producto("A-2", TENANT_A));
        productoRepository.saveAndFlush(producto("B-1", TENANT_B));
    }

    @Test
    @DisplayName("listar con el tenant A no trae productos del tenant B")
    void listar_conTenantA_soloDevuelveLosDeA() {
        crearDatosDeDosTenants();

        TenantContext.set(TENANT_A);

        // findAll es heredado de JpaRepository; findByActivoTrue está declarado en ProductoRepository
        assertThat(productoRepository.findAll())
                .extracting(Producto::getCodigoTecnico)
                .containsExactlyInAnyOrder("A-1", "A-2");
        assertThat(productoRepository.findByActivoTrue())
                .extracting(Producto::getCodigoTecnico)
                .containsExactlyInAnyOrder("A-1", "A-2");
    }

    @Test
    @DisplayName("el mismo código técnico puede existir en dos distribuidoras")
    void existsByCodigoTecnico_esPorTenant() {
        crearDatosDeDosTenants();

        TenantContext.set(TENANT_A);
        assertThat(productoRepository.existsByCodigoTecnico("B-1")).isFalse();

        TenantContext.set(TENANT_B);
        assertThat(productoRepository.existsByCodigoTecnico("B-1")).isTrue();
    }

    @Test
    @DisplayName("guardar sin tenantId lo toma del contexto")
    void guardar_sinTenantId_loTomaDelContexto() {
        TenantContext.set(TENANT_A);

        Producto guardado = productoRepository.saveAndFlush(producto("NUEVO-1", null));

        assertThat(guardado.getTenantId()).isEqualTo(TENANT_A);
    }

    @Test
    @DisplayName("guardar sin tenantId y sin contexto responde 403 Sin distribuidora")
    void guardar_sinTenantNiContexto_403() {
        assertThatThrownBy(() -> productoRepository.saveAndFlush(producto("HUERFANO-1", null)))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
                    assertThat(rse.getReason()).isEqualTo("Sin distribuidora");
                });
    }

    @Test
    @DisplayName("findById no pasa por el filtro: por eso los servicios validan con TenantContext.pertenece")
    void findById_noFiltra_yPerteneceLoDetecta() {
        Producto deB = productoRepository.saveAndFlush(producto("B-1", TENANT_B));

        TenantContext.set(TENANT_A);

        assertThat(productoRepository.findById(deB.getId())).isPresent();
        assertThat(productoRepository.findById(deB.getId()).filter(TenantContext::pertenece)).isEmpty();
    }

    @Test
    @DisplayName("sin transacción (open-in-view=false) el filtro también se aplica")
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void listar_sinTransaccion_tambienFiltra() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.executeWithoutResult(status -> crearDatosDeDosTenants());
        try {
            TenantContext.set(TENANT_A);

            List<Producto> productos = productoRepository.findAll();

            assertThat(productos)
                    .extracting(Producto::getCodigoTecnico)
                    .containsExactlyInAnyOrder("A-1", "A-2");
        } finally {
            TenantContext.clear();
            tx.executeWithoutResult(status -> productoRepository.deleteAll());
        }
    }
}
