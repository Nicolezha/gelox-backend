package com.gelox.backend.repositories;

import com.gelox.backend.entities.EventoSistema;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface EventoSistemaRepository extends JpaRepository<EventoSistema, UUID> {

    Page<EventoSistema> findAllByOrderByFechaDesc(Pageable pageable);

    /**
     * Elimina los registros de la distribuidora que no estén dentro de sus 10 más recientes.
     * La subconsulta con alias 'sub' es requerida por PostgreSQL cuando se hace
     * DELETE sobre la misma tabla que se referencia en la subconsulta.
     */
    @Modifying
    @Query(
        value = "DELETE FROM evento_sistema WHERE tenant_id = :tenantId AND id NOT IN " +
                "(SELECT id FROM (SELECT id FROM evento_sistema WHERE tenant_id = :tenantId " +
                "ORDER BY fecha DESC LIMIT 10) sub)",
        nativeQuery = true
    )
    void eliminarEventosAntiguos(@Param("tenantId") UUID tenantId);

    /**
     * Limpieza al arrancar (sin petición, por tanto sin tenant): deja los 10 eventos
     * más recientes de cada distribuidora.
     */
    @Modifying
    @Query(
        value = "DELETE FROM evento_sistema WHERE id IN " +
                "(SELECT id FROM (SELECT id, ROW_NUMBER() OVER (PARTITION BY tenant_id ORDER BY fecha DESC) AS fila " +
                "FROM evento_sistema) sub WHERE sub.fila > 10)",
        nativeQuery = true
    )
    void eliminarEventosAntiguosPorDistribuidora();
}
