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
 * NOTA DE DISEÑO: Los canales de venta (VENTANILLA, RURAL) se comparan como
 * literales SQL directos —no como parámetros JDBC— para evitar el error
 * "could not determine data type of parameter $N" de PostgreSQL con columnas ENUM.
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
    //
    // Columnas retornadas (mismo orden en todos los métodos):
    //   [0] id       (text)
    //   [1] tipo     (text)  VENTANILLA | RURAL | PLANILLA
    //   [2] cliente  (text, nullable)
    //   [3] productos(text)
    //   [4] cantidad (int)
    //   [5] total    (numeric)
    //   [6] hora     (timestamp, nullable)
    // ══════════════════════════════════════════════════════════════════════════

    @SuppressWarnings("unchecked")
    public List<Object[]> getTransaccionesVentanilla(LocalDate fecha, int offset, int limit) {
        return em.createNativeQuery("""
                SELECT
                    v.id::text,
                    'VENTANILLA'                                                      AS tipo,
                    NULL::text                                                        AS cliente,
                    COALESCE(STRING_AGG(DISTINCT p.nombre, ', '), '')                AS productos,
                    COALESCE(SUM(iv.cantidad_unidades + iv.cantidad_cajas), 0)::int  AS cantidad,
                    v.total,
                    v.fecha                                                           AS hora
                FROM venta v
                LEFT JOIN item_venta iv ON iv.venta_id = v.id
                LEFT JOIN producto   p  ON p.id        = iv.producto_id
                WHERE v.fecha::date = :fecha
                  AND v.canal = 'VENTANILLA'
                GROUP BY v.id, v.total, v.fecha
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
                    'RURAL'                                                           AS tipo,
                    NULL::text                                                        AS cliente,
                    COALESCE(STRING_AGG(DISTINCT p.nombre, ', '), '')                AS productos,
                    COALESCE(SUM(iv.cantidad_unidades + iv.cantidad_cajas), 0)::int  AS cantidad,
                    v.total,
                    v.fecha                                                           AS hora
                FROM venta v
                LEFT JOIN item_venta iv ON iv.venta_id = v.id
                LEFT JOIN producto   p  ON p.id        = iv.producto_id
                WHERE v.fecha::date = :fecha
                  AND v.canal = 'RURAL'
                GROUP BY v.id, v.total, v.fecha
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
                    'PLANILLA'                                                        AS tipo,
                    c.nombre                                                          AS cliente,
                    COALESCE(STRING_AGG(DISTINCT p.nombre, ', '), '')                AS productos,
                    COALESCE(SUM(ip.unidades_despachadas), 0)::int                   AS cantidad,
                    pc.total_ganancia                                                 AS total,
                    NULL::timestamp                                                   AS hora
                FROM planilla_comerciante pc
                JOIN comerciante c ON c.id = pc.comerciante_id
                LEFT JOIN item_planilla ip ON ip.planilla_id = pc.id
                LEFT JOIN producto      p  ON p.id           = ip.producto_id
                WHERE pc.fecha = :fecha
                  AND pc.cerrada = true
                GROUP BY pc.id, c.nombre, pc.total_ganancia
                ORDER BY pc.total_ganancia DESC
                LIMIT :limit OFFSET :offset
                """)
                .setParameter("fecha",  fecha)
                .setParameter("limit",  limit)
                .setParameter("offset", offset)
                .getResultList();
    }

    /**
     * UNION ALL de los tres canales. Permite paginar sin filtro de canal.
     * Los canales de venta se seleccionan con literales SQL para evitar el
     * problema de inferencia de tipos con columnas ENUM de PostgreSQL.
     */
    @SuppressWarnings("unchecked")
    public List<Object[]> getTodasTransacciones(LocalDate fecha, int offset, int limit) {
        return em.createNativeQuery("""
                SELECT * FROM (
                    (SELECT
                        v.id::text,
                        v.canal::text                                                     AS tipo,
                        NULL::text                                                        AS cliente,
                        COALESCE(STRING_AGG(DISTINCT p.nombre, ', '), '')                AS productos,
                        COALESCE(SUM(iv.cantidad_unidades + iv.cantidad_cajas), 0)::int  AS cantidad,
                        v.total,
                        v.fecha                                                           AS hora
                    FROM venta v
                    LEFT JOIN item_venta iv ON iv.venta_id = v.id
                    LEFT JOIN producto   p  ON p.id        = iv.producto_id
                    WHERE v.fecha::date = :fecha
                    GROUP BY v.id, v.canal, v.total, v.fecha)
                    UNION ALL
                    (SELECT
                        pc.id::text,
                        'PLANILLA'                                                        AS tipo,
                        c.nombre                                                          AS cliente,
                        COALESCE(STRING_AGG(DISTINCT p.nombre, ', '), '')                AS productos,
                        COALESCE(SUM(ip.unidades_despachadas), 0)::int                   AS cantidad,
                        pc.total_ganancia                                                 AS total,
                        pc.fecha::timestamp                                               AS hora
                    FROM planilla_comerciante pc
                    JOIN comerciante c ON c.id = pc.comerciante_id
                    LEFT JOIN item_planilla ip ON ip.planilla_id = pc.id
                    LEFT JOIN producto      p  ON p.id           = ip.producto_id
                    WHERE pc.fecha = :fecha
                      AND pc.cerrada = true
                    GROUP BY pc.id, c.nombre, pc.total_ganancia, pc.fecha)
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