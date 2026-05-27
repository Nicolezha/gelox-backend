package com.gelox.backend.repositories;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Repositorio para los reportes diarios (RF40, RF41).
 *
 * Los canales de venta se comparan como literales SQL directos para evitar
 * el error "could not determine data type of parameter $N" con columnas ENUM.
 *
 * Columnas retornadas por los métodos de transacciones (mismo orden en los 4):
 *   [0] id                  (text)
 *   [1] tipo                (text)   VENTANILLA | RURAL | PLANILLA
 *   [2] cliente             (text, nullable)
 *   [3] detalles_productos  (text/JSON)  array de {nombre, cantidad, tipoUnidad}
 *   [4] total               (numeric)
 *   [5] hora                (timestamp, nullable)
 *   [6] metodo_pago         (text, nullable)
 */
@Repository
public class ReporteDiarioRepository {

    @PersistenceContext
    private EntityManager em;

    // ══════════════════════════════════════════════════════════════════════════
    // RF40 — Indicadores consolidados del día
    // ══════════════════════════════════════════════════════════════════════════

    public BigDecimal getIngresosVentanillaDia(LocalDate fecha) {
        Object r = em.createNativeQuery("""
                SELECT COALESCE(SUM(v.total), 0)
                FROM venta v
                WHERE v.fecha::date = :fecha
                  AND v.canal = 'VENTANILLA'
                """)
                .setParameter("fecha", fecha)
                .getSingleResult();
        return toBigDecimal(r);
    }

    public BigDecimal getIngresosRuralDia(LocalDate fecha) {
        Object r = em.createNativeQuery("""
                SELECT COALESCE(SUM(v.total), 0)
                FROM venta v
                WHERE v.fecha::date = :fecha
                  AND v.canal = 'RURAL'
                """)
                .setParameter("fecha", fecha)
                .getSingleResult();
        return toBigDecimal(r);
    }

    public BigDecimal getIngresosComerciantesDia(LocalDate fecha) {
        Object r = em.createNativeQuery("""
                SELECT COALESCE(SUM(p.total_ganancia), 0)
                FROM planilla_comerciante p
                WHERE p.fecha = :fecha
                  AND p.cerrada = true
                """)
                .setParameter("fecha", fecha)
                .getSingleResult();
        return toBigDecimal(r);
    }

    public long countVentanillaDia(LocalDate fecha) {
        Object r = em.createNativeQuery("""
                SELECT COUNT(*)
                FROM venta v
                WHERE v.fecha::date = :fecha
                  AND v.canal = 'VENTANILLA'
                """)
                .setParameter("fecha", fecha)
                .getSingleResult();
        return ((Number) r).longValue();
    }

    public long countRuralDia(LocalDate fecha) {
        Object r = em.createNativeQuery("""
                SELECT COUNT(*)
                FROM venta v
                WHERE v.fecha::date = :fecha
                  AND v.canal = 'RURAL'
                """)
                .setParameter("fecha", fecha)
                .getSingleResult();
        return ((Number) r).longValue();
    }

    public long countPlanillasDia(LocalDate fecha) {
        Object r = em.createNativeQuery("""
                SELECT COUNT(*)
                FROM planilla_comerciante p
                WHERE p.fecha = :fecha
                  AND p.cerrada = true
                """)
                .setParameter("fecha", fecha)
                .getSingleResult();
        return ((Number) r).longValue();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // RF41 — Lista paginada de transacciones del día
    // ══════════════════════════════════════════════════════════════════════════

    @SuppressWarnings("unchecked")
    public List<Object[]> getTransaccionesVentanilla(LocalDate fecha, int offset, int limit) {
        return em.createNativeQuery("""
                SELECT
                    v.id::text,
                    'VENTANILLA' AS tipo,
                    NULL::text   AS cliente,
                    COALESCE(
                        (SELECT JSON_AGG(d)
                         FROM (
                           SELECT JSON_BUILD_OBJECT('nombre', p2.nombre, 'cantidad', iv2.cantidad_unidades, 'tipoUnidad', 'UNIDAD') AS d
                           FROM item_venta iv2 JOIN producto p2 ON p2.id = iv2.producto_id
                           WHERE iv2.venta_id = v.id AND iv2.cantidad_unidades > 0
                           UNION ALL
                           SELECT JSON_BUILD_OBJECT('nombre', p3.nombre, 'cantidad', iv3.cantidad_cajas, 'tipoUnidad', 'CAJA') AS d
                           FROM item_venta iv3 JOIN producto p3 ON p3.id = iv3.producto_id
                           WHERE iv3.venta_id = v.id AND iv3.cantidad_cajas > 0
                         ) items
                        ), '[]'::json
                    )::text      AS detalles_productos,
                    v.total,
                    v.fecha      AS hora,
                    v.metodo_pago::text AS metodo_pago
                FROM venta v
                WHERE v.fecha::date = :fecha
                  AND v.canal = 'VENTANILLA'
                ORDER BY v.fecha DESC
                LIMIT :limit OFFSET :offset
                """)
                .setParameter("fecha",  fecha)
                .setParameter("limit",  limit)
                .setParameter("offset", offset)
                .getResultList();
    }

    @SuppressWarnings("unchecked")
    public List<Object[]> getTransaccionesRural(LocalDate fecha, int offset, int limit) {
        return em.createNativeQuery("""
                SELECT
                    v.id::text,
                    'RURAL'      AS tipo,
                    NULL::text   AS cliente,
                    COALESCE(
                        (SELECT JSON_AGG(d)
                         FROM (
                           SELECT JSON_BUILD_OBJECT('nombre', p2.nombre, 'cantidad', iv2.cantidad_unidades, 'tipoUnidad', 'UNIDAD') AS d
                           FROM item_venta iv2 JOIN producto p2 ON p2.id = iv2.producto_id
                           WHERE iv2.venta_id = v.id AND iv2.cantidad_unidades > 0
                           UNION ALL
                           SELECT JSON_BUILD_OBJECT('nombre', p3.nombre, 'cantidad', iv3.cantidad_cajas, 'tipoUnidad', 'CAJA') AS d
                           FROM item_venta iv3 JOIN producto p3 ON p3.id = iv3.producto_id
                           WHERE iv3.venta_id = v.id AND iv3.cantidad_cajas > 0
                         ) items
                        ), '[]'::json
                    )::text      AS detalles_productos,
                    v.total,
                    v.fecha      AS hora,
                    'TRANSFERENCIA' AS metodo_pago
                FROM venta v
                WHERE v.fecha::date = :fecha
                  AND v.canal = 'RURAL'
                ORDER BY v.fecha DESC
                LIMIT :limit OFFSET :offset
                """)
                .setParameter("fecha",  fecha)
                .setParameter("limit",  limit)
                .setParameter("offset", offset)
                .getResultList();
    }

    @SuppressWarnings("unchecked")
    public List<Object[]> getTransaccionesPlanilla(LocalDate fecha, int offset, int limit) {
        return em.createNativeQuery("""
                SELECT
                    pc.id::text,
                    'PLANILLA'   AS tipo,
                    c.nombre     AS cliente,
                    COALESCE(
                        (SELECT JSON_AGG(
                            JSON_BUILD_OBJECT('nombre', p2.nombre, 'cantidad', ip2.unidades_despachadas, 'tipoUnidad', 'UNIDAD')
                         )
                         FROM item_planilla ip2 JOIN producto p2 ON p2.id = ip2.producto_id
                         WHERE ip2.planilla_id = pc.id AND ip2.unidades_despachadas > 0
                        ), '[]'::json
                    )::text      AS detalles_productos,
                    pc.total_ganancia AS total,
                    NULL::timestamp   AS hora,
                    'EFECTIVO'        AS metodo_pago
                FROM planilla_comerciante pc
                JOIN comerciante c ON c.id = pc.comerciante_id
                WHERE pc.fecha = :fecha
                  AND pc.cerrada = true
                ORDER BY pc.total_ganancia DESC
                LIMIT :limit OFFSET :offset
                """)
                .setParameter("fecha",  fecha)
                .setParameter("limit",  limit)
                .setParameter("offset", offset)
                .getResultList();
    }

    @SuppressWarnings("unchecked")
    public List<Object[]> getTodasTransacciones(LocalDate fecha, int offset, int limit) {
        return em.createNativeQuery("""
                SELECT * FROM (
                    (SELECT
                        v.id::text,
                        'VENTANILLA' AS tipo,
                        NULL::text   AS cliente,
                        COALESCE(
                            (SELECT JSON_AGG(d)
                             FROM (
                               SELECT JSON_BUILD_OBJECT('nombre', p2.nombre, 'cantidad', iv2.cantidad_unidades, 'tipoUnidad', 'UNIDAD') AS d
                               FROM item_venta iv2 JOIN producto p2 ON p2.id = iv2.producto_id
                               WHERE iv2.venta_id = v.id AND iv2.cantidad_unidades > 0
                               UNION ALL
                               SELECT JSON_BUILD_OBJECT('nombre', p3.nombre, 'cantidad', iv3.cantidad_cajas, 'tipoUnidad', 'CAJA') AS d
                               FROM item_venta iv3 JOIN producto p3 ON p3.id = iv3.producto_id
                               WHERE iv3.venta_id = v.id AND iv3.cantidad_cajas > 0
                             ) items
                            ), '[]'::json
                        )::text      AS detalles_productos,
                        v.total,
                        v.fecha      AS hora,
                        v.metodo_pago::text AS metodo_pago
                    FROM venta v
                    WHERE v.fecha::date = :fecha
                      AND v.canal = 'VENTANILLA')
                    UNION ALL
                    (SELECT
                        v.id::text,
                        'RURAL'      AS tipo,
                        NULL::text   AS cliente,
                        COALESCE(
                            (SELECT JSON_AGG(d)
                             FROM (
                               SELECT JSON_BUILD_OBJECT('nombre', p2.nombre, 'cantidad', iv2.cantidad_unidades, 'tipoUnidad', 'UNIDAD') AS d
                               FROM item_venta iv2 JOIN producto p2 ON p2.id = iv2.producto_id
                               WHERE iv2.venta_id = v.id AND iv2.cantidad_unidades > 0
                               UNION ALL
                               SELECT JSON_BUILD_OBJECT('nombre', p3.nombre, 'cantidad', iv3.cantidad_cajas, 'tipoUnidad', 'CAJA') AS d
                               FROM item_venta iv3 JOIN producto p3 ON p3.id = iv3.producto_id
                               WHERE iv3.venta_id = v.id AND iv3.cantidad_cajas > 0
                             ) items
                            ), '[]'::json
                        )::text      AS detalles_productos,
                        v.total,
                        v.fecha      AS hora,
                        'TRANSFERENCIA' AS metodo_pago
                    FROM venta v
                    WHERE v.fecha::date = :fecha
                      AND v.canal = 'RURAL')
                    UNION ALL
                    (SELECT
                        pc.id::text,
                        'PLANILLA'   AS tipo,
                        c.nombre     AS cliente,
                        COALESCE(
                            (SELECT JSON_AGG(
                                JSON_BUILD_OBJECT('nombre', p2.nombre, 'cantidad', ip2.unidades_despachadas, 'tipoUnidad', 'UNIDAD')
                             )
                             FROM item_planilla ip2 JOIN producto p2 ON p2.id = ip2.producto_id
                             WHERE ip2.planilla_id = pc.id AND ip2.unidades_despachadas > 0
                            ), '[]'::json
                        )::text      AS detalles_productos,
                        pc.total_ganancia  AS total,
                        pc.fecha::timestamp AS hora,
                        'EFECTIVO'         AS metodo_pago
                    FROM planilla_comerciante pc
                    JOIN comerciante c ON c.id = pc.comerciante_id
                    WHERE pc.fecha = :fecha
                      AND pc.cerrada = true)
                ) t
                ORDER BY hora DESC NULLS LAST
                LIMIT :limit OFFSET :offset
                """)
                .setParameter("fecha",  fecha)
                .setParameter("limit",  limit)
                .setParameter("offset", offset)
                .getResultList();
    }

    // ──────────────────────────────────────────────────────────────────────────

    private BigDecimal toBigDecimal(Object value) {
        if (value == null) return BigDecimal.ZERO;
        if (value instanceof BigDecimal bd) return bd;
        return new BigDecimal(value.toString());
    }
}
