package com.gelox.backend.rf54;

import com.gelox.backend.entities.Producto;
import com.gelox.backend.recomendacion.DemandaRepository;
import com.gelox.backend.recomendacion.DemandaRepository.DemandaDia;
import com.gelox.backend.repositories.ProductoRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RF54 — DemandaRepository sobre H2: suma ventanilla, rural y comerciantes por producto y día
 * sin duplicar totales, filtra por tenant y por rango, y calcula los días de historial.
 * Flyway se desactiva: las migraciones son de PostgreSQL y H2 crea el esquema desde las entidades.
 */
@DataJpaTest(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"
})
@Import(DemandaRepository.class)
class DemandaRepositoryTest {

    static final UUID TENANT_A = UUID.randomUUID();
    static final UUID TENANT_B = UUID.randomUUID();
    static final LocalDate HOY = LocalDate.now(ZoneId.of("America/Bogota"));
    static final LocalDate DESDE = HOY.minusDays(89);

    @Autowired DemandaRepository demandaRepository;
    @Autowired ProductoRepository productoRepository;
    @Autowired EntityManager em;

    Producto paleta;   // 12 unidades por caja
    Producto cono;     // 24 unidades por caja

    @BeforeEach
    void setUp() {
        // Solo se prueban las sumas: usuario, comerciante y distribuidora no hacen falta.
        em.createNativeQuery("SET REFERENTIAL_INTEGRITY FALSE").executeUpdate();
        paleta = productoRepository.saveAndFlush(producto("PAL-1", 12));
        cono = productoRepository.saveAndFlush(producto("CON-1", 24));
    }

    static Producto producto(String codigo, int unidadesCaja) {
        Producto p = new Producto();
        p.setCodigoTecnico(codigo);
        p.setNombre("Producto " + codigo);
        p.setPrecioVenta(BigDecimal.valueOf(3000));
        p.setPrecioCosto(BigDecimal.valueOf(24000));
        p.setUnidadesPorCaja(unidadesCaja);
        p.setTenantId(TENANT_A);
        return p;
    }

    UUID venta(UUID tenant, String canal, LocalDateTime fecha) {
        UUID id = UUID.randomUUID();
        em.createNativeQuery("""
                INSERT INTO venta (id, tenant_id, canal, fecha, total, usuario_id)
                VALUES (:id, :tenant, :canal, :fecha, 0, :usuario)
                """)
                .setParameter("id", id).setParameter("tenant", tenant).setParameter("canal", canal)
                .setParameter("fecha", fecha).setParameter("usuario", UUID.randomUUID())
                .executeUpdate();
        return id;
    }

    void itemVenta(UUID venta, Producto p, int cajas, int unidades) {
        em.createNativeQuery("""
                INSERT INTO item_venta (id, venta_id, producto_id, cantidad_cajas, cantidad_unidades, precio_unitario, subtotal)
                VALUES (:id, :venta, :producto, :cajas, :unidades, 0, 0)
                """)
                .setParameter("id", UUID.randomUUID()).setParameter("venta", venta)
                .setParameter("producto", p.getId()).setParameter("cajas", cajas).setParameter("unidades", unidades)
                .executeUpdate();
    }

    UUID planilla(UUID tenant, LocalDate fecha) {
        UUID id = UUID.randomUUID();
        em.createNativeQuery("""
                INSERT INTO planilla_comerciante (id, tenant_id, comerciante_id, usuario_id, fecha, cerrada)
                VALUES (:id, :tenant, :comerciante, :usuario, :fecha, false)
                """)
                .setParameter("id", id).setParameter("tenant", tenant)
                .setParameter("comerciante", UUID.randomUUID()).setParameter("usuario", UUID.randomUUID())
                .setParameter("fecha", fecha)
                .executeUpdate();
        return id;
    }

    void itemPlanilla(UUID planilla, Producto p, int despachadas, int devueltas) {
        em.createNativeQuery("""
                INSERT INTO item_planilla (id, planilla_id, producto_id, unidades_despachadas, unidades_devueltas, precio_venta)
                VALUES (:id, :planilla, :producto, :despachadas, :devueltas, 0)
                """)
                .setParameter("id", UUID.randomUUID()).setParameter("planilla", planilla)
                .setParameter("producto", p.getId()).setParameter("despachadas", despachadas)
                .setParameter("devueltas", devueltas)
                .executeUpdate();
    }

    static int unidades(List<DemandaDia> demanda, Producto p, LocalDate fecha) {
        return demanda.stream()
                .filter(d -> d.productoId().equals(p.getId()) && d.fecha().equals(fecha))
                .mapToInt(DemandaDia::unidades)
                .sum();
    }

    @Test
    @DisplayName("Suma ventanilla, rural y comerciantes del mismo día sin duplicar totales")
    void sumaLosTresCanales() {
        LocalDate dia = HOY.minusDays(5);
        UUID v1 = venta(TENANT_A, "VENTANILLA", dia.atTime(9, 0));
        itemVenta(v1, paleta, 2, 3);                        // 2 × 12 + 3 = 27
        itemVenta(v1, cono, 1, 0);                          // 24
        UUID v2 = venta(TENANT_A, "RURAL", dia.atTime(23, 30));
        itemVenta(v2, paleta, 0, 5);                        // 5
        UUID p1 = planilla(TENANT_A, dia);
        itemPlanilla(p1, paleta, 10, 4);                    // 6
        itemPlanilla(p1, cono, 30, 30);                     // 0
        UUID p2 = planilla(TENANT_A, dia);
        itemPlanilla(p2, paleta, 5, 0);                     // 5

        List<DemandaDia> demanda = demandaRepository.demandaDiaria(TENANT_A, DESDE, HOY);

        assertThat(unidades(demanda, paleta, dia)).isEqualTo(27 + 5 + 6 + 5);
        assertThat(unidades(demanda, cono, dia)).isEqualTo(24);
        assertThat(demanda).filteredOn(d -> d.productoId().equals(paleta.getId())).hasSize(1);
    }

    @Test
    @DisplayName("Solo trae el tenant pedido y los días dentro de [desde, hasta]")
    void filtraTenantYRango() {
        UUID propia = venta(TENANT_A, "VENTANILLA", HOY.atTime(10, 0));
        itemVenta(propia, paleta, 0, 7);
        UUID ajena = venta(TENANT_B, "VENTANILLA", HOY.atTime(10, 0));
        itemVenta(ajena, paleta, 0, 100);
        itemPlanilla(planilla(TENANT_B, HOY), paleta, 50, 0);
        UUID vieja = venta(TENANT_A, "VENTANILLA", DESDE.minusDays(1).atTime(12, 0));
        itemVenta(vieja, paleta, 0, 40);
        itemVenta(venta(TENANT_A, "VENTANILLA", DESDE.atStartOfDay()), paleta, 0, 2);

        List<DemandaDia> demanda = demandaRepository.demandaDiaria(TENANT_A, DESDE, HOY);

        assertThat(unidades(demanda, paleta, HOY)).isEqualTo(7);
        assertThat(unidades(demanda, paleta, DESDE)).isEqualTo(2);
        assertThat(demanda).extracting(DemandaDia::unidades).doesNotContain(40, 100, 50);
    }

    @Test
    @DisplayName("diasHistorial cuenta desde el primer registro de venta o planilla del tenant")
    void diasHistorial() {
        itemVenta(venta(TENANT_A, "VENTANILLA", HOY.minusDays(30).atTime(8, 0)), paleta, 0, 1);
        itemPlanilla(planilla(TENANT_A, HOY.minusDays(120)), paleta, 3, 0);
        itemVenta(venta(TENANT_B, "VENTANILLA", HOY.minusDays(400).atTime(8, 0)), paleta, 0, 1);

        assertThat(demandaRepository.diasHistorial(TENANT_A)).isEqualTo(120);
        assertThat(demandaRepository.diasHistorial(TENANT_B)).isEqualTo(400);
        assertThat(demandaRepository.diasHistorial(UUID.randomUUID())).isZero();
    }
}
