package com.gelox.backend.repositories;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Repository
public class DashboardRepository {

    @PersistenceContext
    private EntityManager em;

    public BigDecimal getIngresosVentasDia(LocalDate fecha) {
        Object result = em.createNativeQuery("""
                SELECT COALESCE(SUM(v.total), 0)
                FROM venta v
                WHERE v.fecha::date = :fecha
                """)
                .setParameter("fecha", fecha)
                .getSingleResult();
        return toBigDecimal(result);
    }

    public BigDecimal getIngresosPlanilasDia(LocalDate fecha) {
        Object result = em.createNativeQuery("""
                SELECT COALESCE(SUM(p.total_ganancia), 0)
                FROM planilla_comerciante p
                WHERE p.fecha = :fecha
                  AND p.cerrada = true
                """)
                .setParameter("fecha", fecha)
                .getSingleResult();
        return toBigDecimal(result);
    }

    public BigDecimal getCostosVentasDia(LocalDate fecha) {
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
                """)
                .setParameter("fecha", fecha)
                .getSingleResult();
        return toBigDecimal(result);
    }

    public BigDecimal getCostosPlanilasDia(LocalDate fecha) {
        Object result = em.createNativeQuery("""
                SELECT COALESCE(SUM(
                    ip.unidades_despachadas * pr.precio_costo
                        / COALESCE(pr.unidades_por_caja, 1)::numeric
                ), 0)
                FROM item_planilla ip
                JOIN planilla_comerciante pl  ON pl.id  = ip.planilla_id
                JOIN producto pr              ON pr.id  = ip.producto_id
                WHERE pl.fecha = :fecha
                  AND pl.cerrada = true
                """)
                .setParameter("fecha", fecha)
                .getSingleResult();
        return toBigDecimal(result);
    }

    public Long getComerciantesActivos() {
        return ((Number) em.createNativeQuery(
                "SELECT COUNT(*) FROM comerciante WHERE activo = true")
                .getSingleResult()).longValue();
    }

    public Long getTotalComerciantes() {
        return ((Number) em.createNativeQuery(
                "SELECT COUNT(*) FROM comerciante")
                .getSingleResult()).longValue();
    }

    public BigDecimal getTotalVentasPorCanal(String canal, LocalDate fechaInicio, LocalDate fechaFin) {
        Object result = em.createNativeQuery("""
                SELECT COALESCE(SUM(v.total), 0)
                FROM venta v
                WHERE v.canal::text = :canal
                  AND v.fecha::date BETWEEN :fechaInicio AND :fechaFin
                """)
                .setParameter("canal", canal)
                .setParameter("fechaInicio", fechaInicio)
                .setParameter("fechaFin", fechaFin)
                .getSingleResult();
        return toBigDecimal(result);
    }

    public BigDecimal getTotalPlanillasCerradas(LocalDate fechaInicio, LocalDate fechaFin) {
        Object result = em.createNativeQuery("""
                SELECT COALESCE(SUM(p.total_ganancia), 0)
                FROM planilla_comerciante p
                WHERE p.cerrada = true
                  AND p.fecha BETWEEN :fechaInicio AND :fechaFin
                """)
                .setParameter("fechaInicio", fechaInicio)
                .setParameter("fechaFin", fechaFin)
                .getSingleResult();
        return toBigDecimal(result);
    }

    @SuppressWarnings("unchecked")
    public List<Object[]> getTop5Comerciantes(LocalDate fechaInicio, LocalDate fechaFin) {
        return em.createNativeQuery("""
                SELECT c.id::text, c.nombre, COALESCE(SUM(pc.total_ganancia), 0) AS total_ingreso
                FROM planilla_comerciante pc
                JOIN comerciante c ON c.id = pc.comerciante_id
                WHERE pc.fecha BETWEEN :inicio AND :fin
                  AND pc.cerrada = true
                GROUP BY c.id, c.nombre
                ORDER BY total_ingreso DESC
                """)
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