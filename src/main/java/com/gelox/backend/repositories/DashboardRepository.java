package com.gelox.backend.repositories;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public class DashboardRepository {

    @PersistenceContext
    private EntityManager em;

    public BigDecimal getIngresosVentasDia(UUID tenantId, LocalDate fecha) {
        Object result = em.createNativeQuery("""
                SELECT COALESCE(SUM(v.total), 0)
                FROM venta v
                WHERE v.fecha::date = :fecha
                  AND v.tenant_id = :tenantId
                """)
                .setParameter("tenantId", tenantId)
                .setParameter("fecha", fecha)
                .getSingleResult();
        return toBigDecimal(result);
    }

    public BigDecimal getIngresosPlanilasDia(UUID tenantId, LocalDate fecha) {
        Object result = em.createNativeQuery("""
                SELECT COALESCE(SUM(p.total_ganancia), 0)
                FROM planilla_comerciante p
                WHERE p.fecha = :fecha
                  AND p.tenant_id = :tenantId
                  AND p.cerrada = true
                """)
                .setParameter("tenantId", tenantId)
                .setParameter("fecha", fecha)
                .getSingleResult();
        return toBigDecimal(result);
    }

    public BigDecimal getCostosVentasDia(UUID tenantId, LocalDate fecha) {
        Object result = em.createNativeQuery("""
                SELECT COALESCE(SUM(
                    iv.cantidad_cajas * pr.precio_costo
                    + iv.cantidad_unidades * pr.precio_costo
                        / COALESCE(pr.unidades_por_caja, 1)::numeric
                ), 0)
                FROM item_venta iv
                JOIN venta v     ON v.id  = iv.venta_id
                JOIN producto pr ON pr.id = iv.producto_id
                WHERE v.fecha::date = :fecha
                  AND v.tenant_id = :tenantId
                """)
                .setParameter("tenantId", tenantId)
                .setParameter("fecha", fecha)
                .getSingleResult();
        return toBigDecimal(result);
    }

    public BigDecimal getCostosPlanilasDia(UUID tenantId, LocalDate fecha) {
        Object result = em.createNativeQuery("""
                SELECT COALESCE(SUM(
                    -- RF37: costo solo de lo vendido (despachadas − devueltas); precio_costo es por caja
                    (ip.unidades_despachadas - ip.unidades_devueltas) * pr.precio_costo
                        / COALESCE(pr.unidades_por_caja, 1)::numeric
                ), 0)
                FROM item_planilla ip
                JOIN planilla_comerciante pl  ON pl.id  = ip.planilla_id
                JOIN producto pr              ON pr.id  = ip.producto_id
                WHERE pl.fecha = :fecha
                  AND pl.tenant_id = :tenantId
                  AND pl.cerrada = true
                """)
                .setParameter("tenantId", tenantId)
                .setParameter("fecha", fecha)
                .getSingleResult();
        return toBigDecimal(result);
    }

    public Long getComerciantesActivos(UUID tenantId) {
        return ((Number) em.createNativeQuery(
                "SELECT COUNT(*) FROM comerciante WHERE activo = true AND tenant_id = :tenantId")
                .setParameter("tenantId", tenantId)
                .getSingleResult()).longValue();
    }

    public Long getTotalComerciantes(UUID tenantId) {
        return ((Number) em.createNativeQuery(
                "SELECT COUNT(*) FROM comerciante WHERE tenant_id = :tenantId")
                .setParameter("tenantId", tenantId)
                .getSingleResult()).longValue();
    }

    public BigDecimal getTotalVentasPorCanal(UUID tenantId, String canal, LocalDate fechaInicio, LocalDate fechaFin) {
        Object result = em.createNativeQuery("""
                SELECT COALESCE(SUM(v.total), 0)
                FROM venta v
                WHERE v.canal::text = :canal
                  AND v.tenant_id = :tenantId
                  AND v.fecha::date BETWEEN :fechaInicio AND :fechaFin
                """)
                .setParameter("tenantId", tenantId)
                .setParameter("canal", canal)
                .setParameter("fechaInicio", fechaInicio)
                .setParameter("fechaFin", fechaFin)
                .getSingleResult();
        return toBigDecimal(result);
    }

    public BigDecimal getTotalPlanillasCerradas(UUID tenantId, LocalDate fechaInicio, LocalDate fechaFin) {
        Object result = em.createNativeQuery("""
                SELECT COALESCE(SUM(p.total_ganancia), 0)
                FROM planilla_comerciante p
                WHERE p.cerrada = true
                  AND p.tenant_id = :tenantId
                  AND p.fecha BETWEEN :fechaInicio AND :fechaFin
                """)
                .setParameter("tenantId", tenantId)
                .setParameter("fechaInicio", fechaInicio)
                .setParameter("fechaFin", fechaFin)
                .getSingleResult();
        return toBigDecimal(result);
    }

    @SuppressWarnings("unchecked")
    public List<Object[]> getTop5Comerciantes(UUID tenantId, LocalDate fechaInicio, LocalDate fechaFin) {
        return em.createNativeQuery("""
                SELECT c.id::text, c.nombre, COALESCE(SUM(pc.total_ganancia), 0) AS total_ingreso
                FROM planilla_comerciante pc
                JOIN comerciante c ON c.id = pc.comerciante_id
                WHERE pc.fecha BETWEEN :inicio AND :fin
                  AND pc.tenant_id = :tenantId
                  AND pc.cerrada = true
                GROUP BY c.id, c.nombre
                ORDER BY total_ingreso DESC
                """)
                .setParameter("tenantId", tenantId)
                .setParameter("inicio", fechaInicio)
                .setParameter("fin", fechaFin)
                .setMaxResults(5)
                .getResultList();
    }

    private BigDecimal toBigDecimal(Object value) {
        if (value == null) return BigDecimal.ZERO;
        if (value instanceof BigDecimal bd) return bd;
        return new BigDecimal(value.toString());
    }
}