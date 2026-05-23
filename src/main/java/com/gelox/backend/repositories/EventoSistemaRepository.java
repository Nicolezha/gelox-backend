package com.gelox.backend.repositories;

import com.gelox.backend.entities.EventoSistema;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.UUID;

public interface EventoSistemaRepository extends JpaRepository<EventoSistema, UUID> {

    Page<EventoSistema> findAllByOrderByFechaDesc(Pageable pageable);

    /**
     * Elimina todos los registros que no estén dentro de los 10 más recientes.
     * La subconsulta con alias 'sub' es requerida por PostgreSQL cuando se hace
     * DELETE sobre la misma tabla que se referencia en la subconsulta.
     */
    @Modifying
    @Query(
        value = "DELETE FROM evento_sistema WHERE id NOT IN " +
                "(SELECT id FROM (SELECT id FROM evento_sistema ORDER BY fecha DESC LIMIT 10) sub)",
        nativeQuery = true
    )
    void eliminarEventosAntiguos();
}
