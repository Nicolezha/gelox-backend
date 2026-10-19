package com.gelox.backend.repositories;

import com.gelox.backend.dto.PeriodoFiltroDTO;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Repository
public class ReporteRepository {

    @PersistenceContext
    private EntityManager em;

    public BigDecimal getTotalInversion(UUID tenantId, PeriodoFiltroDTO periodo) {
        Object result = em.createNativeQuery("""
                SELECT COALESCE(SUM(ipp.precio_unitario * ipp.cantidad_recibida), 0)
                FROM item_pedido_proveedor ipp
                JOIN pedido_proveedor pp ON pp.id = ipp.pedido_id
                WHERE pp.fecha::date BETWEEN :inicio AND :fin
                  AND pp.tenant_id = :tenantId
                  AND pp.estado = 'RECIBIDO'
                """)
                .setParameter("tenantId", tenantId)
                .setParameter("inicio", periodo.fechaInicio())
                .setParameter("fin", periodo.fechaFin())
                .getSingleResult();
        return toBigDecimal(result);
    }

    public BigDecimal getIngresosPorCanal(UUID tenantId, String canal, PeriodoFiltroDTO periodo) {
        Object result = em.createNativeQuery("""
                SELECT COALESCE(SUM(v.total), 0)
                FROM venta v
                WHERE v.fecha::date BETWEEN :inicio AND :fin
                  AND v.tenant_id = :tenantId
                  AND v.canal::text = :canal
                """)
                .setParameter("tenantId", tenantId)
                .setParameter("inicio", periodo.fechaInicio())
                .setParameter("fin", periodo.fechaFin())
                .setParameter("canal", canal)
                .getSingleResult();
        return toBigDecimal(result);
    }

    public BigDecimal getIngresosComerciantesEnPeriodo(UUID tenantId, PeriodoFiltroDTO periodo) {
        Object result = em.createNativeQuery("""
                SELECT COALESCE(SUM(p.total_ganancia), 0)
                FROM planilla_comerciante p
                WHERE p.fecha BETWEEN :inicio AND :fin
                  AND p.tenant_id = :tenantId
                  AND p.cerrada = true
                """)
                .setParameter("tenantId", tenantId)
                .setParameter("inicio", periodo.fechaInicio())
                .setParameter("fin", periodo.fechaFin())
                .getSingleResult();
        return toBigDecimal(result);
    }

    // ── Agrupación por día (offset desde fechaInicio) — para filtro SEMANA ──────────────────────

    /**
     * Retorna filas [dia_offset (int), inversion] donde 0=primer día del período (lunes).
     */
    @SuppressWarnings("unchecked")
    public List<Object[]> getInversionPorDiaOffset(UUID tenantId, PeriodoFiltroDTO periodo) {
        return em.createNativeQuery("""
                SELECT
                    (pp.fecha::date - CAST(:inicio AS date)) AS dia_offset,
                    COALESCE(SUM(ipp.precio_unitario * ipp.cantidad_recibida), 0) AS inversion
                FROM item_pedido_proveedor ipp
                JOIN pedido_proveedor pp ON pp.id = ipp.pedido_id
                WHERE pp.fecha::date BETWEEN :inicio AND :fin
                  AND pp.tenant_id = :tenantId
                  AND pp.estado = 'RECIBIDO'
                GROUP BY dia_offset
                ORDER BY dia_offset
                """)
                .setParameter("tenantId", tenantId)
                .setParameter("inicio", periodo.fechaInicio())
                .setParameter("fin", periodo.fechaFin())
                .getResultList();
    }

    /**
     * Retorna filas [dia_offset (int), ingresos] de ventas agrupadas por día.
     */
    @SuppressWarnings("unchecked")
    public List<Object[]> getIngresosVentasPorDiaOffset(UUID tenantId, PeriodoFiltroDTO periodo) {
        return em.createNativeQuery("""
                SELECT
                    (v.fecha::date - CAST(:inicio AS date)) AS dia_offset,
                    COALESCE(SUM(v.total), 0) AS ingresos
                FROM venta v
                WHERE v.fecha::date BETWEEN :inicio AND :fin
                  AND v.tenant_id = :tenantId
                GROUP BY dia_offset
                ORDER BY dia_offset
                """)
                .setParameter("tenantId", tenantId)
                .setParameter("inicio", periodo.fechaInicio())
                .setParameter("fin", periodo.fechaFin())
                .getResultList();
    }

    /**
     * Retorna filas [dia_offset (int), ingresos] de planillas agrupadas por día.
     */
    @SuppressWarnings("unchecked")
    public List<Object[]> getIngresosPllanillasPorDiaOffset(UUID tenantId, PeriodoFiltroDTO periodo) {
        return em.createNativeQuery("""
                SELECT
                    (p.fecha - CAST(:inicio AS date)) AS dia_offset,
                    COALESCE(SUM(p.total_ganancia), 0) AS ingresos
                FROM planilla_comerciante p
                WHERE p.fecha BETWEEN :inicio AND :fin
                  AND p.tenant_id = :tenantId
                  AND p.cerrada = true
                GROUP BY dia_offset
                ORDER BY dia_offset
                """)
                .setParameter("tenantId", tenantId)
                .setParameter("inicio", periodo.fechaInicio())
                .setParameter("fin", periodo.fechaFin())
                .getResultList();
    }

    // ── Agrupación por mes (número 1-12) — para filtro ANIO ─────────────────────────────────

    /**
     * Retorna filas [mes (1-12), inversion] agrupadas por mes.
     */
    @SuppressWarnings("unchecked")
    public List<Object[]> getInversionPorMes(UUID tenantId, PeriodoFiltroDTO periodo) {
        return em.createNativeQuery("""
                SELECT
                    EXTRACT(MONTH FROM pp.fecha::date)::int AS mes,
                    COALESCE(SUM(ipp.precio_unitario * ipp.cantidad_recibida), 0) AS inversion
                FROM item_pedido_proveedor ipp
                JOIN pedido_proveedor pp ON pp.id = ipp.pedido_id
                WHERE pp.fecha::date BETWEEN :inicio AND :fin
                  AND pp.tenant_id = :tenantId
                  AND pp.estado = 'RECIBIDO'
                GROUP BY mes
                ORDER BY mes
                """)
                .setParameter("tenantId", tenantId)
                .setParameter("inicio", periodo.fechaInicio())
                .setParameter("fin", periodo.fechaFin())
                .getResultList();
    }

    /**
     * Retorna filas [mes (1-12), ingresos] de ventas agrupadas por mes.
     */
    @SuppressWarnings("unchecked")
    public List<Object[]> getIngresosVentasPorMes(UUID tenantId, PeriodoFiltroDTO periodo) {
        return em.createNativeQuery("""
                SELECT
                    EXTRACT(MONTH FROM v.fecha::date)::int AS mes,
                    COALESCE(SUM(v.total), 0) AS ingresos
                FROM venta v
                WHERE v.fecha::date BETWEEN :inicio AND :fin
                  AND v.tenant_id = :tenantId
                GROUP BY mes
                ORDER BY mes
                """)
                .setParameter("tenantId", tenantId)
                .setParameter("inicio", periodo.fechaInicio())
                .setParameter("fin", periodo.fechaFin())
                .getResultList();
    }

    /**
     * Retorna filas [mes (1-12), ingresos] de planillas agrupadas por mes.
     */
    @SuppressWarnings("unchecked")
    public List<Object[]> getIngresosPllanillasPorMes(UUID tenantId, PeriodoFiltroDTO periodo) {
        return em.createNativeQuery("""
                SELECT
                    EXTRACT(MONTH FROM p.fecha)::int AS mes,
                    COALESCE(SUM(p.total_ganancia), 0) AS ingresos
                FROM planilla_comerciante p
                WHERE p.fecha BETWEEN :inicio AND :fin
                  AND p.tenant_id = :tenantId
                  AND p.cerrada = true
                GROUP BY mes
                ORDER BY mes
                """)
                .setParameter("tenantId", tenantId)
                .setParameter("inicio", periodo.fechaInicio())
                .setParameter("fin", periodo.fechaFin())
                .getResultList();
    }

    // ── Agrupación por semana (existente) — para filtro MES / RANGO ─────────────────────────

    /**
     * Retorna filas [semana_numero (int), inversion (numeric)] por semana dentro del período.
     * semana_numero = floor(dias_desde_inicio / 7) + 1
     */
    @SuppressWarnings("unchecked")
    public List<Object[]> getInversionPorSemana(UUID tenantId, PeriodoFiltroDTO periodo) {
        return em.createNativeQuery("""
                SELECT
                    (pp.fecha::date - CAST(:inicio AS date)) / 7 + 1 AS semana,
                    COALESCE(SUM(ipp.precio_unitario * ipp.cantidad_recibida), 0) AS inversion
                FROM item_pedido_proveedor ipp
                JOIN pedido_proveedor pp ON pp.id = ipp.pedido_id
                WHERE pp.fecha::date BETWEEN :inicio AND :fin
                  AND pp.tenant_id = :tenantId
                  AND pp.estado = 'RECIBIDO'
                GROUP BY semana
                ORDER BY semana
                """)
                .setParameter("tenantId", tenantId)
                .setParameter("inicio", periodo.fechaInicio())
                .setParameter("fin", periodo.fechaFin())
                .getResultList();
    }

    /**
     * Retorna filas [semana_numero (int), ingresos (numeric)] de ventas por semana.
     */
    @SuppressWarnings("unchecked")
    public List<Object[]> getIngresosVentasPorSemana(UUID tenantId, PeriodoFiltroDTO periodo) {
        return em.createNativeQuery("""
                SELECT
                    (v.fecha::date - CAST(:inicio AS date)) / 7 + 1 AS semana,
                    COALESCE(SUM(v.total), 0) AS ingresos
                FROM venta v
                WHERE v.fecha::date BETWEEN :inicio AND :fin
                  AND v.tenant_id = :tenantId
                GROUP BY semana
                ORDER BY semana
                """)
                .setParameter("tenantId", tenantId)
                .setParameter("inicio", periodo.fechaInicio())
                .setParameter("fin", periodo.fechaFin())
                .getResultList();
    }

    /**
     * Retorna filas [semana_numero (int), ingresos (numeric)] de planillas por semana.
     */
    @SuppressWarnings("unchecked")
    public List<Object[]> getIngresosPllanillasPorSemana(UUID tenantId, PeriodoFiltroDTO periodo) {
        return em.createNativeQuery("""
                SELECT
                    (p.fecha - CAST(:inicio AS date)) / 7 + 1 AS semana,
                    COALESCE(SUM(p.total_ganancia), 0) AS ingresos
                FROM planilla_comerciante p
                WHERE p.fecha BETWEEN :inicio AND :fin
                  AND p.tenant_id = :tenantId
                  AND p.cerrada = true
                GROUP BY semana
                ORDER BY semana
                """)
                .setParameter("tenantId", tenantId)
                .setParameter("inicio", periodo.fechaInicio())
                .setParameter("fin", periodo.fechaFin())
                .getResultList();
    }

    // ── Agrupación por año+mes (clave YYYYMM) — para filtro RANGO > 28 días ────────────────────

    /**
     * Retorna filas [anio_mes (int YYYYMM), inversion] agrupadas por año y mes.
     * Soporta rangos que cruzan años, a diferencia de getInversionPorMes.
     */
    @SuppressWarnings("unchecked")
    public List<Object[]> getInversionPorAnioMes(UUID tenantId, PeriodoFiltroDTO periodo) {
        return em.createNativeQuery("""
                SELECT
                    EXTRACT(YEAR FROM pp.fecha::date)::int * 100
                        + EXTRACT(MONTH FROM pp.fecha::date)::int AS anio_mes,
                    COALESCE(SUM(ipp.precio_unitario * ipp.cantidad_recibida), 0) AS inversion
                FROM item_pedido_proveedor ipp
                JOIN pedido_proveedor pp ON pp.id = ipp.pedido_id
                WHERE pp.fecha::date BETWEEN :inicio AND :fin
                  AND pp.tenant_id = :tenantId
                  AND pp.estado = 'RECIBIDO'
                GROUP BY anio_mes
                ORDER BY anio_mes
                """)
                .setParameter("tenantId", tenantId)
                .setParameter("inicio", periodo.fechaInicio())
                .setParameter("fin", periodo.fechaFin())
                .getResultList();
    }

    /**
     * Retorna filas [anio_mes (int YYYYMM), ingresos] de ventas agrupadas por año y mes.
     */
    @SuppressWarnings("unchecked")
    public List<Object[]> getIngresosVentasPorAnioMes(UUID tenantId, PeriodoFiltroDTO periodo) {
        return em.createNativeQuery("""
                SELECT
                    EXTRACT(YEAR FROM v.fecha::date)::int * 100
                        + EXTRACT(MONTH FROM v.fecha::date)::int AS anio_mes,
                    COALESCE(SUM(v.total), 0) AS ingresos
                FROM venta v
                WHERE v.fecha::date BETWEEN :inicio AND :fin
                  AND v.tenant_id = :tenantId
                GROUP BY anio_mes
                ORDER BY anio_mes
                """)
                .setParameter("tenantId", tenantId)
                .setParameter("inicio", periodo.fechaInicio())
                .setParameter("fin", periodo.fechaFin())
                .getResultList();
    }

    /**
     * Retorna filas [anio_mes (int YYYYMM), ingresos] de planillas agrupadas por año y mes.
     */
    @SuppressWarnings("unchecked")
    public List<Object[]> getIngresosPllanillasPorAnioMes(UUID tenantId, PeriodoFiltroDTO periodo) {
        return em.createNativeQuery("""
                SELECT
                    EXTRACT(YEAR FROM p.fecha)::int * 100
                        + EXTRACT(MONTH FROM p.fecha)::int AS anio_mes,
                    COALESCE(SUM(p.total_ganancia), 0) AS ingresos
                FROM planilla_comerciante p
                WHERE p.fecha BETWEEN :inicio AND :fin
                  AND p.tenant_id = :tenantId
                  AND p.cerrada = true
                GROUP BY anio_mes
                ORDER BY anio_mes
                """)
                .setParameter("tenantId", tenantId)
                .setParameter("inicio", periodo.fechaInicio())
                .setParameter("fin", periodo.fechaFin())
                .getResultList();
    }

    /**
     * Retorna filas [canal, ingresos, costos] para los canales VENTANILLA y RURAL.
     */
    @SuppressWarnings("unchecked")
    public List<Object[]> getRentabilidadVentanillaRural(UUID tenantId, PeriodoFiltroDTO periodo) {
        return em.createNativeQuery("""
                SELECT
                    v.canal,
                    COALESCE(SUM(iv.subtotal), 0) AS ingresos,
                    COALESCE(SUM(
                        iv.cantidad_cajas * pr.precio_costo
                        + iv.cantidad_unidades * pr.precio_costo
                            / COALESCE(pr.unidades_por_caja, 1)::numeric
                    ), 0) AS costos
                FROM venta v
                JOIN item_venta iv ON iv.venta_id = v.id
                JOIN producto pr   ON pr.id = iv.producto_id
                WHERE v.fecha::date BETWEEN :inicio AND :fin
                  AND v.tenant_id = :tenantId
                GROUP BY v.canal
                ORDER BY v.canal
                """)
                .setParameter("tenantId", tenantId)
                .setParameter("inicio", periodo.fechaInicio())
                .setParameter("fin", periodo.fechaFin())
                .getResultList();
    }

    /**
     * Costos del canal COMERCIANTES: precio_costo × (despachadas − devueltas) por producto en planillas cerradas.
     * NOTA: Ajustar nombre de columna 'cantidad_despachada' si difiere en el esquema real de item_planilla.
     */
    public BigDecimal getCostosComerciantesEnPeriodo(UUID tenantId, PeriodoFiltroDTO periodo) {
        Object result = em.createNativeQuery("""
                SELECT COALESCE(SUM(
                    -- RF37: costo solo de lo vendido (despachadas − devueltas); precio_costo es por caja
                    (ip.unidades_despachadas - ip.unidades_devueltas) * pr.precio_costo
                        / COALESCE(pr.unidades_por_caja, 1)::numeric
                ), 0)
                FROM item_planilla ip
                JOIN planilla_comerciante pl ON pl.id = ip.planilla_id
                JOIN producto pr             ON pr.id = ip.producto_id
                WHERE pl.fecha BETWEEN :inicio AND :fin
                  AND pl.tenant_id = :tenantId
                  AND pl.cerrada = true
                """)
                .setParameter("tenantId", tenantId)
                .setParameter("inicio", periodo.fechaInicio())
                .setParameter("fin", periodo.fechaFin())
                .getSingleResult();
        return toBigDecimal(result);
    }

    private BigDecimal toBigDecimal(Object value) {
        if (value == null) return BigDecimal.ZERO;
        if (value instanceof BigDecimal bd) return bd;
        return new BigDecimal(value.toString());
    }
}
