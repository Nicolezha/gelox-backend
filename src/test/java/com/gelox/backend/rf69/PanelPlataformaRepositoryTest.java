package com.gelox.backend.rf69;

import com.gelox.backend.gobierno.PanelPlataformaRepository;
import com.gelox.backend.gobierno.PanelPlataformaRepository.FilaConteo;
import com.gelox.backend.gobierno.PanelPlataformaRepository.FilaConteoValor;
import com.gelox.backend.gobierno.PanelPlataformaRepository.PuntoSerie;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RF69 — Agregados nativos del panel de la plataforma sobre H2: suma y agrupa
 * por tenant_id sin traer filas crudas a Java (FP-15 §2.16.1). El Hibernate
 * Filter no aplica a SQL nativo y aquí no hace falta: el panel ve todas las
 * distribuidoras. Flyway se desactiva: H2 crea el esquema desde las entidades.
 */
@DataJpaTest(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"
})
@Import(PanelPlataformaRepository.class)
class PanelPlataformaRepositoryTest {

    static final UUID TENANT_A = UUID.randomUUID();
    static final UUID TENANT_B = UUID.randomUUID();
    static final LocalDate HOY = LocalDate.now();
    static final LocalDate DESDE = HOY.minusDays(10);

    @Autowired PanelPlataformaRepository panelRepository;
    @Autowired EntityManager em;

    @BeforeEach
    void setUp() {
        // Solo se prueban los agregados: usuario, producto y comerciante no hacen falta.
        em.createNativeQuery("SET REFERENTIAL_INTEGRITY FALSE").executeUpdate();

        // H2 no reconoce el tipo PostgreSQL "estado_pedido" (@ColumnTransformer de PedidoProveedor):
        // Hibernate no logra crear la tabla al arrancar el contexto. Se crea aquí con un estado
        // VARCHAR simple, suficiente para las sumas de totalPedidosProveedor.
        em.createNativeQuery("""
                CREATE TABLE IF NOT EXISTS pedido_proveedor (
                    id UUID PRIMARY KEY, tenant_id UUID, usuario_id UUID, fecha DATE, estado VARCHAR(20)
                )
                """).executeUpdate();
        em.createNativeQuery("""
                CREATE TABLE IF NOT EXISTS item_pedido_proveedor (
                    id UUID PRIMARY KEY, pedido_id UUID, producto_id UUID,
                    cantidad_cajas INT, cantidad_unidades INT, cantidad_recibida INT, precio_unitario NUMERIC
                )
                """).executeUpdate();
    }

    UUID venta(UUID tenant, BigDecimal total, LocalDate fecha) {
        UUID id = UUID.randomUUID();
        em.createNativeQuery("""
                INSERT INTO venta (id, tenant_id, canal, fecha, total, usuario_id)
                VALUES (:id, :tenant, 'VENTANILLA', :fecha, :total, :usuario)
                """)
                .setParameter("id", id).setParameter("tenant", tenant)
                .setParameter("fecha", fecha.atTime(10, 0)).setParameter("total", total)
                .setParameter("usuario", UUID.randomUUID())
                .executeUpdate();
        return id;
    }

    UUID pedidoProveedor(UUID tenant, LocalDate fecha) {
        UUID id = UUID.randomUUID();
        em.createNativeQuery("""
                INSERT INTO pedido_proveedor (id, tenant_id, usuario_id, fecha, estado)
                VALUES (:id, :tenant, :usuario, :fecha, 'PENDIENTE')
                """)
                .setParameter("id", id).setParameter("tenant", tenant)
                .setParameter("usuario", UUID.randomUUID()).setParameter("fecha", fecha)
                .executeUpdate();
        return id;
    }

    UUID producto(int unidadesPorCaja) {
        UUID id = UUID.randomUUID();
        em.createNativeQuery("""
                INSERT INTO producto
                    (id, codigo_tecnico, nombre, precio_venta, precio_costo, stock_actual, stock_minimo, stock_medio, unidades_por_caja, activo)
                VALUES (:id, :codigo, 'Producto test', 0, 0, 0, 0, 0, :unidadesCaja, true)
                """)
                .setParameter("id", id).setParameter("codigo", id.toString())
                .setParameter("unidadesCaja", unidadesPorCaja)
                .executeUpdate();
        return id;
    }

    void itemPedidoProveedor(UUID pedido, UUID producto, int cajas, int unidades, BigDecimal precioUnitario) {
        em.createNativeQuery("""
                INSERT INTO item_pedido_proveedor
                    (id, pedido_id, producto_id, cantidad_cajas, cantidad_unidades, cantidad_recibida, precio_unitario)
                VALUES (:id, :pedido, :producto, :cajas, :unidades, 0, :precio)
                """)
                .setParameter("id", UUID.randomUUID()).setParameter("pedido", pedido)
                .setParameter("producto", producto).setParameter("cajas", cajas)
                .setParameter("unidades", unidades).setParameter("precio", precioUnitario)
                .executeUpdate();
    }

    void planillaCerrada(UUID tenant, LocalDate fecha) {
        em.createNativeQuery("""
                INSERT INTO planilla_comerciante (id, tenant_id, comerciante_id, usuario_id, fecha, cerrada)
                VALUES (:id, :tenant, :comerciante, :usuario, :fecha, true)
                """)
                .setParameter("id", UUID.randomUUID()).setParameter("tenant", tenant)
                .setParameter("comerciante", UUID.randomUUID()).setParameter("usuario", UUID.randomUUID())
                .setParameter("fecha", fecha)
                .executeUpdate();
    }

    void comandoVoz(UUID tenant, String metodo, LocalDate fecha) {
        em.createNativeQuery("""
                INSERT INTO comando_voz (id, tenant_id, usuario_id, texto_transcrito, confianza, estado, metodo_clasificacion, created_at)
                VALUES (:id, :tenant, :usuario, 'texto', 0.9, 'PROCESADO', :metodo, :fecha)
                """)
                .setParameter("id", UUID.randomUUID()).setParameter("tenant", tenant)
                .setParameter("usuario", UUID.randomUUID()).setParameter("metodo", metodo)
                .setParameter("fecha", fecha.atTime(12, 0))
                .executeUpdate();
    }

    void usuario(UUID tenant, boolean activo, LocalDate creadoEn) {
        em.createNativeQuery("""
                INSERT INTO usuario (id, tenant_id, firebase_uid, nombre, correo, rol, activo, created_at, updated_at)
                VALUES (:id, :tenant, :uid, 'Usuario', :correo, 'ENCARGADO_VENTAS', :activo, :creado, :creado)
                """)
                .setParameter("id", UUID.randomUUID()).setParameter("tenant", tenant)
                .setParameter("uid", UUID.randomUUID().toString())
                .setParameter("correo", UUID.randomUUID() + "@test.com")
                .setParameter("activo", activo).setParameter("creado", creadoEn.atTime(8, 0))
                .executeUpdate();
    }

    void archivo(UUID tenant, long bytes) {
        em.createNativeQuery("""
                INSERT INTO archivo_almacenado (id, tenant_id, carpeta, url, bytes, created_at)
                VALUES (:id, :tenant, 'productos', 'http://x', :bytes, :fecha)
                """)
                .setParameter("id", UUID.randomUUID()).setParameter("tenant", tenant)
                .setParameter("bytes", bytes).setParameter("fecha", HOY.atStartOfDay())
                .executeUpdate();
    }

    @Test
    @DisplayName("totalVentas suma todas las distribuidoras en el rango, sin filtrar por tenant")
    void totalVentas_sumaTodasLasDistribuidoras() {
        venta(TENANT_A, BigDecimal.valueOf(1000), HOY);
        venta(TENANT_B, BigDecimal.valueOf(2000), HOY);
        venta(TENANT_A, BigDecimal.valueOf(500), DESDE.minusDays(1)); // fuera de rango

        FilaConteoValor total = panelRepository.totalVentas(DESDE, HOY);

        assertThat(total.conteo()).isEqualTo(2);
        assertThat(total.valor()).isEqualByComparingTo("3000");
    }

    @Test
    @DisplayName("ventasPorDistribuidora agrupa por tenant_id")
    void ventasPorDistribuidora_agrupaPorTenant() {
        venta(TENANT_A, BigDecimal.valueOf(1000), HOY);
        venta(TENANT_A, BigDecimal.valueOf(500), HOY);
        venta(TENANT_B, BigDecimal.valueOf(2000), HOY);

        List<FilaConteoValor> filas = panelRepository.ventasPorDistribuidora(DESDE, HOY);

        FilaConteoValor filaA = filas.stream().filter(f -> f.tenantId().equals(TENANT_A)).findFirst().orElseThrow();
        FilaConteoValor filaB = filas.stream().filter(f -> f.tenantId().equals(TENANT_B)).findFirst().orElseThrow();
        assertThat(filaA.conteo()).isEqualTo(2);
        assertThat(filaA.valor()).isEqualByComparingTo("1500");
        assertThat(filaB.conteo()).isEqualTo(1);
        assertThat(filaB.valor()).isEqualByComparingTo("2000");
    }

    @Test
    @DisplayName("totalPedidosProveedor calcula cantidad y valor (precio_unitario x unidades) en el rango")
    void totalPedidosProveedor_calculaCantidadYValor() {
        UUID producto = producto(12); // 12 unidades por caja
        UUID pedido = pedidoProveedor(TENANT_A, HOY);
        itemPedidoProveedor(pedido, producto, 2, 3, BigDecimal.valueOf(100)); // (2*12+3) * 100 = 2700

        FilaConteoValor total = panelRepository.totalPedidosProveedor(DESDE, HOY);

        assertThat(total.conteo()).isEqualTo(1);
        assertThat(total.valor()).isEqualByComparingTo("2700");
    }

    @Test
    @DisplayName("totalPlanillasLiquidadas solo cuenta las cerradas dentro del rango")
    void totalPlanillasLiquidadas_soloCerradas() {
        planillaCerrada(TENANT_A, HOY);
        planillaCerrada(TENANT_B, HOY);
        planillaCerrada(TENANT_A, DESDE.minusDays(1)); // fuera de rango

        long total = panelRepository.totalPlanillasLiquidadas(DESDE, HOY);

        assertThat(total).isEqualTo(2);
    }

    @Test
    @DisplayName("totalLlamadasIa solo cuenta metodo_clasificacion = LLM")
    void totalLlamadasIa_soloMetodoLlm() {
        comandoVoz(TENANT_A, "LLM", HOY);
        comandoVoz(TENANT_A, "REGLAS", HOY);
        comandoVoz(TENANT_B, "LLM", HOY);

        long llamadas = panelRepository.totalLlamadasIa(DESDE, HOY);

        assertThat(llamadas).isEqualTo(2);
    }

    @Test
    @DisplayName("llamadasIaPorDistribuidora agrupa por tenant_id")
    void llamadasIaPorDistribuidora_agrupaPorTenant() {
        comandoVoz(TENANT_A, "LLM", HOY);
        comandoVoz(TENANT_A, "LLM", HOY);
        comandoVoz(TENANT_B, "LLM", HOY);

        List<FilaConteo> filas = panelRepository.llamadasIaPorDistribuidora(DESDE, HOY);

        assertThat(filas).filteredOn(f -> f.tenantId().equals(TENANT_A))
                .extracting(FilaConteo::conteo).containsExactly(2L);
        assertThat(filas).filteredOn(f -> f.tenantId().equals(TENANT_B))
                .extracting(FilaConteo::conteo).containsExactly(1L);
    }

    @Test
    @DisplayName("usuariosNuevos cuenta los creados en el rango; usuariosRegistrados y habilitados, todos")
    void usuarios_registradosHabilitadosYNuevos() {
        usuario(TENANT_A, true, HOY);
        usuario(TENANT_A, false, HOY);
        usuario(TENANT_B, true, DESDE.minusDays(5)); // fuera del rango de "nuevos"

        assertThat(panelRepository.usuariosRegistrados()).isEqualTo(3);
        assertThat(panelRepository.usuariosHabilitados()).isEqualTo(2);
        assertThat(panelRepository.usuariosNuevos(DESDE, HOY)).isEqualTo(2);
    }

    @Test
    @DisplayName("almacenamientoUsadoBytes suma todas las distribuidoras")
    void almacenamientoUsadoBytes_sumaTodas() {
        archivo(TENANT_A, 1000);
        archivo(TENANT_B, 2000);

        assertThat(panelRepository.almacenamientoUsadoBytes()).isEqualTo(3000);
    }

    @Test
    @DisplayName("seriePorDia trae un punto por cada día del rango, con ceros donde no hay datos")
    void seriePorDia_unPuntoPorDiaConCeros() {
        venta(TENANT_A, BigDecimal.valueOf(1000), HOY);
        comandoVoz(TENANT_A, "LLM", HOY);

        List<PuntoSerie> serie = panelRepository.seriePorDia(HOY.minusDays(2), HOY);

        assertThat(serie).hasSize(3);
        PuntoSerie hoy = serie.stream().filter(p -> p.fecha().equals(HOY)).findFirst().orElseThrow();
        assertThat(hoy.ventas()).isEqualByComparingTo("1000");
        assertThat(hoy.transacciones()).isEqualTo(1);
        assertThat(hoy.llamadasIa()).isEqualTo(1);

        PuntoSerie ayer = serie.stream().filter(p -> p.fecha().equals(HOY.minusDays(1))).findFirst().orElseThrow();
        assertThat(ayer.ventas()).isEqualByComparingTo("0");
        assertThat(ayer.transacciones()).isZero();
    }
}
