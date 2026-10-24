package com.gelox.backend.recomendacion;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;

import java.nio.ByteBuffer;
import java.sql.Date;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

/**
 * Demanda diaria por producto e historial de la distribuidora (RF54, FP-15 2.10.1).
 *
 * <p>Ventanilla y rural salen de item_venta ⨝ venta (cajas × unidades_por_caja + unidades);
 * los comerciantes de item_planilla ⨝ planilla_comerciante (despachadas − devueltas). Cada
 * origen se agrega en su propia subconsulta y luego se suman, sin CROSS JOIN, para no duplicar
 * totales.</p>
 *
 * <p>El Hibernate Filter no aplica a SQL nativo: tenant_id va explícito en venta y en
 * planilla_comerciante.</p>
 */
@Repository
public class DemandaRepository {

    private static final ZoneId ZONA_BOGOTA = ZoneId.of("America/Bogota");

    @PersistenceContext
    private EntityManager em;

    /** Unidades vendidas de un producto en un día (solo días con movimiento). */
    public record DemandaDia(UUID productoId, LocalDate fecha, int unidades) {}

    /** Demanda por producto y día en el rango [desde, hasta], ambos inclusive. */
    @SuppressWarnings("unchecked")
    public List<DemandaDia> demandaDiaria(UUID tenantId, LocalDate desde, LocalDate hasta) {
        List<Object[]> filas = em.createNativeQuery("""
                SELECT t.producto_id, t.fecha, SUM(t.unidades)
                FROM (
                    SELECT iv.producto_id AS producto_id,
                           CAST(v.fecha AS date) AS fecha,
                           SUM(iv.cantidad_cajas * COALESCE(p.unidades_por_caja, 0)
                               + iv.cantidad_unidades) AS unidades
                    FROM item_venta iv
                    JOIN venta v    ON v.id = iv.venta_id
                    JOIN producto p ON p.id = iv.producto_id
                    WHERE v.tenant_id = :tenantId
                      AND CAST(v.fecha AS date) BETWEEN :desde AND :hasta
                    GROUP BY iv.producto_id, CAST(v.fecha AS date)

                    UNION ALL

                    SELECT ip.producto_id AS producto_id,
                           pc.fecha AS fecha,
                           SUM(ip.unidades_despachadas - ip.unidades_devueltas) AS unidades
                    FROM item_planilla ip
                    JOIN planilla_comerciante pc ON pc.id = ip.planilla_id
                    WHERE pc.tenant_id = :tenantId
                      AND pc.fecha BETWEEN :desde AND :hasta
                    GROUP BY ip.producto_id, pc.fecha
                ) t
                GROUP BY t.producto_id, t.fecha
                ORDER BY t.fecha
                """)
                .setParameter("tenantId", tenantId)
                .setParameter("desde", desde)
                .setParameter("hasta", hasta)
                .getResultList();

        return filas.stream()
                .map(f -> new DemandaDia(
                        toUuid(f[0]),
                        toLocalDate(f[1]),
                        ((Number) f[2]).intValue()))
                .toList();
    }

    /**
     * Días entre el primer registro de venta o planilla de la distribuidora y hoy
     * (America/Bogota). Sin registros, 0.
     */
    public int diasHistorial(UUID tenantId) {
        Object primero = em.createNativeQuery("""
                SELECT MIN(t.fecha)
                FROM (
                    SELECT CAST(v.fecha AS date) AS fecha FROM venta v WHERE v.tenant_id = :tenantId
                    UNION ALL
                    SELECT pc.fecha AS fecha FROM planilla_comerciante pc WHERE pc.tenant_id = :tenantId
                ) t
                """)
                .setParameter("tenantId", tenantId)
                .getSingleResult();
        if (primero == null) {
            return 0;
        }
        return (int) ChronoUnit.DAYS.between(toLocalDate(primero), LocalDate.now(ZONA_BOGOTA));
    }

    /** PostgreSQL devuelve UUID; H2 (pruebas) puede devolver los 16 bytes. */
    private static UUID toUuid(Object valor) {
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
}
