package com.gelox.backend.repositories;

import com.gelox.backend.entities.Comerciante;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface ComercianteRepository extends JpaRepository<Comerciante, UUID> {

    /** Todos los comerciantes ordenados por nombre (RF34 — listar). */
    List<Comerciante> findAllByOrderByNombreAsc();

    /** Filtra por estado activo/inactivo. */
    List<Comerciante> findByActivoOrderByNombreAsc(boolean activo);

    /** Búsqueda por nombre (insensible a mayúsculas, coincidencia parcial). */
    List<Comerciante> findByNombreContainingIgnoreCaseOrderByNombreAsc(String nombre);

    /**
     * Resumen de todas las planillas (abiertas y cerradas) de un comerciante en un rango de fechas (RF35).
     * Retorna: [planillaId, fecha, totalDespachado, totalDevuelto, unidadesVendidas, ganancia, cerrada]
     */
    @Query(value = """
            SELECT
                pc.id::text                                     AS planilla_id,
                pc.fecha                                        AS fecha,
                COALESCE(SUM(ip.unidades_despachadas), 0)::int  AS total_despachado,
                COALESCE(SUM(ip.unidades_devueltas),   0)::int  AS total_devuelto,
                COALESCE(SUM(ip.unidades_despachadas - ip.unidades_devueltas), 0)::int
                                                                AS unidades_vendidas,
                pc.total_ganancia                               AS ganancia,
                pc.cerrada                                      AS cerrada
            FROM planilla_comerciante pc
            LEFT JOIN item_planilla ip ON ip.planilla_id = pc.id
            WHERE pc.comerciante_id = :comercianteId
              AND (:fechaInicio IS NULL OR pc.fecha >= CAST(:fechaInicio AS date))
              AND (:fechaFin    IS NULL OR pc.fecha <= CAST(:fechaFin    AS date))
            GROUP BY pc.id, pc.fecha, pc.total_ganancia, pc.cerrada
            ORDER BY pc.fecha DESC
            """, nativeQuery = true)
    List<Object[]> findPlanillasResumen(
            @Param("comercianteId") UUID comercianteId,
            @Param("fechaInicio")   LocalDate fechaInicio,
            @Param("fechaFin")      LocalDate fechaFin);
}
