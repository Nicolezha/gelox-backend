package com.gelox.backend.gobierno;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * RF69 — Agregados nativos para el dashboard del Superadministrador (FP-15 §2.16.1):
 * todo por {@code GROUP BY tenant_id} o por día, sin traer filas crudas a Java, para
 * cumplir el RNF de 5 s con todas las distribuidoras a la vez. El Hibernate Filter no
 * aplica a SQL nativo y aquí no hace falta: el Superadministrador ve todas las filas.
 */
@Repository
public class PanelPlataformaRepository {

    @PersistenceContext
    private EntityManager em;

    public record FilaConteoValor(UUID tenantId, long conteo, BigDecimal valor) {}

    public record FilaConteo(UUID tenantId, long conteo) {}

    public record PuntoSerie(LocalDate fecha, BigDecimal ventas, long transacciones, long llamadasIa, long usuariosNuevos) {}

    // ── Totales de la plataforma en el período [desde, hasta] ──────────────

    public FilaConteoValor totalVentas(LocalDate desde, LocalDate hasta) {
        return unaFilaConteoValor("""
                SELECT COUNT(*), COALESCE(SUM(v.total), 0)
                FROM venta v
                WHERE CAST(v.fecha AS date) BETWEEN :desde AND :hasta
                """, desde, hasta);
    }

    public FilaConteoValor totalPedidosProveedor(LocalDate desde, LocalDate hasta) {
        return unaFilaConteoValor("""
                SELECT COUNT(DISTINCT pp.id), COALESCE(SUM(
                       ipp.precio_unitario * (ipp.cantidad_cajas * COALESCE(p.unidades_por_caja, 1) + ipp.cantidad_unidades)
                   ), 0)
                FROM pedido_proveedor pp
                JOIN item_pedido_proveedor ipp ON ipp.pedido_id = pp.id
                JOIN producto p ON p.id = ipp.producto_id
                WHERE pp.fecha BETWEEN :desde AND :hasta
                """, desde, hasta);
    }

    public long totalPlanillasLiquidadas(LocalDate desde, LocalDate hasta) {
        return unLong("""
                SELECT COUNT(*)
                FROM planilla_comerciante pc
                WHERE pc.cerrada = true AND pc.fecha BETWEEN :desde AND :hasta
                """, desde, hasta);
    }

    /** Llamadas clasificadas con el LLM (cuenta la cuota LLAMADAS_IA_MES) en el período. */
    public long totalLlamadasIa(LocalDate desde, LocalDate hasta) {
        return unLong("""
                SELECT COUNT(*)
                FROM comando_voz cv
                WHERE cv.metodo_clasificacion = 'LLM'
                  AND CAST(cv.created_at AS date) BETWEEN :desde AND :hasta
                """, desde, hasta);
    }

    /** Comandos de voz procesados en el período, con cualquier método de clasificación. */
    public long totalComandosVoz(LocalDate desde, LocalDate hasta) {
        return unLong("""
                SELECT COUNT(*)
                FROM comando_voz cv
                WHERE CAST(cv.created_at AS date) BETWEEN :desde AND :hasta
                """, desde, hasta);
    }

    /** Recomendaciones de compra generadas (RF54) en el período. */
    public long totalRecomendaciones(LocalDate desde, LocalDate hasta) {
        return unLong("""
                SELECT COUNT(*)
                FROM recomendacion_compra rc
                WHERE CAST(rc.created_at AS date) BETWEEN :desde AND :hasta
                """, desde, hasta);
    }

    public long usuariosNuevos(LocalDate desde, LocalDate hasta) {
        return unLong("""
                SELECT COUNT(*)
                FROM usuario u
                WHERE u.tenant_id IS NOT NULL
                  AND CAST(u.created_at AS date) BETWEEN :desde AND :hasta
                """, desde, hasta);
    }

    public long usuariosRegistrados() {
        return unLong("SELECT COUNT(*) FROM usuario u WHERE u.tenant_id IS NOT NULL");
    }

    public long usuariosHabilitados() {
        return unLong("SELECT COUNT(*) FROM usuario u WHERE u.tenant_id IS NOT NULL AND u.activo = true");
    }

    public long almacenamientoUsadoBytes() {
        return unLong("SELECT COALESCE(SUM(a.bytes), 0) FROM archivo_almacenado a");
    }

    /** Suma de la cuota de almacenamiento (MB) de los planes con límite; los sin límite no suman. */
    public long almacenamientoAsignadoMb() {
        return unLong("""
                SELECT COALESCE(SUM(ps.max_almacenamiento_mb), 0)
                FROM distribuidora d
                JOIN plan_suscripcion ps ON ps.id = d.plan_id
                WHERE ps.max_almacenamiento_mb IS NOT NULL
                """);
    }

    // ── Por distribuidora, en el período ────────────────────────────────────

    public List<FilaConteoValor> ventasPorDistribuidora(LocalDate desde, LocalDate hasta) {
        return variasFilasConteoValor("""
                SELECT v.tenant_id, COUNT(*), COALESCE(SUM(v.total), 0)
                FROM venta v
                WHERE CAST(v.fecha AS date) BETWEEN :desde AND :hasta
                GROUP BY v.tenant_id
                """, desde, hasta);
    }

    public List<FilaConteo> llamadasIaPorDistribuidora(LocalDate desde, LocalDate hasta) {
        return variasFilasConteo("""
                SELECT cv.tenant_id, COUNT(*)
                FROM comando_voz cv
                WHERE cv.metodo_clasificacion = 'LLM'
                  AND CAST(cv.created_at AS date) BETWEEN :desde AND :hasta
                GROUP BY cv.tenant_id
                """, desde, hasta);
    }

    // ── Serie diaria de la plataforma completa ──────────────────────────────

    @SuppressWarnings("unchecked")
    public List<PuntoSerie> seriePorDia(LocalDate desde, LocalDate hasta) {
        List<Object[]> ventas = em.createNativeQuery("""
                SELECT CAST(v.fecha AS date), COUNT(*), COALESCE(SUM(v.total), 0)
                FROM venta v
                WHERE CAST(v.fecha AS date) BETWEEN :desde AND :hasta
                GROUP BY CAST(v.fecha AS date)
                """)
                .setParameter("desde", desde).setParameter("hasta", hasta).getResultList();

        List<Object[]> ia = em.createNativeQuery("""
                SELECT CAST(cv.created_at AS date), COUNT(*)
                FROM comando_voz cv
                WHERE cv.metodo_clasificacion = 'LLM'
                  AND CAST(cv.created_at AS date) BETWEEN :desde AND :hasta
                GROUP BY CAST(cv.created_at AS date)
                """)
                .setParameter("desde", desde).setParameter("hasta", hasta).getResultList();

        List<Object[]> nuevos = em.createNativeQuery("""
                SELECT CAST(u.created_at AS date), COUNT(*)
                FROM usuario u
                WHERE u.tenant_id IS NOT NULL
                  AND CAST(u.created_at AS date) BETWEEN :desde AND :hasta
                GROUP BY CAST(u.created_at AS date)
                """)
                .setParameter("desde", desde).setParameter("hasta", hasta).getResultList();

        Map<LocalDate, BigDecimal> valorPorDia = new LinkedHashMap<>();
        Map<LocalDate, Long> transPorDia = new HashMap<>();
        Map<LocalDate, Long> iaPorDia = new HashMap<>();
        Map<LocalDate, Long> nuevosPorDia = new HashMap<>();

        for (Object[] f : ventas) {
            LocalDate fecha = toLocalDate(f[0]);
            valorPorDia.put(fecha, toBigDecimal(f[2]));
            transPorDia.put(fecha, ((Number) f[1]).longValue());
        }
        for (Object[] f : ia) {
            iaPorDia.put(toLocalDate(f[0]), ((Number) f[1]).longValue());
        }
        for (Object[] f : nuevos) {
            nuevosPorDia.put(toLocalDate(f[0]), ((Number) f[1]).longValue());
        }

        List<PuntoSerie> serie = new ArrayList<>();
        for (LocalDate dia = desde; !dia.isAfter(hasta); dia = dia.plusDays(1)) {
            serie.add(new PuntoSerie(dia,
                    valorPorDia.getOrDefault(dia, BigDecimal.ZERO),
                    transPorDia.getOrDefault(dia, 0L),
                    iaPorDia.getOrDefault(dia, 0L),
                    nuevosPorDia.getOrDefault(dia, 0L)));
        }
        return serie;
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private long unLong(String sql) {
        Object resultado = em.createNativeQuery(sql).getSingleResult();
        return ((Number) resultado).longValue();
    }

    private long unLong(String sql, LocalDate desde, LocalDate hasta) {
        Object resultado = em.createNativeQuery(sql)
                .setParameter("desde", desde).setParameter("hasta", hasta).getSingleResult();
        return ((Number) resultado).longValue();
    }

    private FilaConteoValor unaFilaConteoValor(String sql, LocalDate desde, LocalDate hasta) {
        Object[] fila = (Object[]) em.createNativeQuery(sql)
                .setParameter("desde", desde).setParameter("hasta", hasta).getSingleResult();
        return new FilaConteoValor(null, ((Number) fila[0]).longValue(), toBigDecimal(fila[1]));
    }

    @SuppressWarnings("unchecked")
    private List<FilaConteoValor> variasFilasConteoValor(String sql, LocalDate desde, LocalDate hasta) {
        List<Object[]> filas = em.createNativeQuery(sql)
                .setParameter("desde", desde).setParameter("hasta", hasta).getResultList();
        return filas.stream()
                .map(f -> new FilaConteoValor(toUuid(f[0]), ((Number) f[1]).longValue(), toBigDecimal(f[2])))
                .toList();
    }

    @SuppressWarnings("unchecked")
    private List<FilaConteo> variasFilasConteo(String sql, LocalDate desde, LocalDate hasta) {
        List<Object[]> filas = em.createNativeQuery(sql)
                .setParameter("desde", desde).setParameter("hasta", hasta).getResultList();
        return filas.stream()
                .map(f -> new FilaConteo(toUuid(f[0]), ((Number) f[1]).longValue()))
                .toList();
    }

    /** PostgreSQL devuelve UUID; H2 (pruebas) puede devolver los 16 bytes. */
    private static UUID toUuid(Object valor) {
        if (valor == null) return null;
        if (valor instanceof UUID uuid) return uuid;
        if (valor instanceof byte[] bytes && bytes.length == 16) {
            ByteBuffer buffer = ByteBuffer.wrap(bytes);
            return new UUID(buffer.getLong(), buffer.getLong());
        }
        return UUID.fromString(valor.toString());
    }

    private static LocalDate toLocalDate(Object valor) {
        if (valor instanceof LocalDate fecha) return fecha;
        if (valor instanceof Date fecha) return fecha.toLocalDate();
        return LocalDate.parse(valor.toString().substring(0, 10));
    }

    private static BigDecimal toBigDecimal(Object valor) {
        if (valor == null) return BigDecimal.ZERO;
        if (valor instanceof BigDecimal bd) return bd;
        return new BigDecimal(valor.toString());
    }
}
