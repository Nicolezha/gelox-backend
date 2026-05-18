package com.gelox.backend.repositories;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;

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
                FROM planilla p
                WHERE p.fecha = :fecha
                  AND p.cerrada = true
                """)
                .setParameter("fecha", fecha)
                .getSingleResult();
        return toBigDecimal(result);
    }

    public BigDecimal getCostosVentasDia(LocalDate fecha) {
        Object result = em.createNativeQuery("""
                SELECT COALESCE(SUM((iv.cantidad_unidades + iv.cantidad_cajas) * pr.precio_costo), 0)
                FROM item_venta iv
                JOIN venta v  ON v.id  = iv.venta_id
                JOIN producto pr ON pr.id = iv.producto_id
                WHERE v.fecha::date = :fecha
                """)
                .setParameter("fecha", fecha)
                .getSingleResult();
        return toBigDecimal(result);
    }

    public BigDecimal getCostosPlanilasDia(LocalDate fecha) {
        Object result = em.createNativeQuery("""
                SELECT COALESCE(SUM(ip.cantidad_despachada * pr.precio_costo), 0)
                FROM item_planilla ip
                JOIN planilla pl  ON pl.id  = ip.planilla_id
                JOIN producto pr  ON pr.id  = ip.producto_id
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
                WHERE v.canal = :canal
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
                FROM planilla p
                WHERE p.cerrada = true
                  AND p.fecha BETWEEN :fechaInicio AND :fechaFin
                """)
                .setParameter("fechaInicio", fechaInicio)
                .setParameter("fechaFin", fechaFin)
                .getSingleResult();
        return toBigDecimal(result);
    }

    private BigDecimal toBigDecimal(Object value) {
        if (value == null) return BigDecimal.ZERO;
        if (value instanceof BigDecimal bd) return bd;
        return new BigDecimal(value.toString());
    }
}