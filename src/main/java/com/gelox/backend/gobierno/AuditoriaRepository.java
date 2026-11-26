package com.gelox.backend.gobierno;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AuditoriaRepository extends JpaRepository<Auditoria, UUID>, JpaSpecificationExecutor<Auditoria> {

    /** RF70 — última fila de una acción sobre una entidad (fechaPlan = último PLAN_ASIGNAR). */
    Optional<Auditoria> findFirstByAccionAndEntidadIdOrderByFechaHoraDesc(String accion, UUID entidadId);

    /** RF71 — usuarios que aparecen en el log (filtro del panel). */
    @Query("select distinct a.usuarioId from Auditoria a where a.usuarioId is not null")
    List<UUID> usuariosConFilas();

    /** RF71 — usuarios con filas de la distribuidora (por tenant o por ser la entidad afectada). */
    @Query("""
            select distinct a.usuarioId from Auditoria a
             where a.usuarioId is not null
               and (a.tenantId = :distribuidoraId
                    or (a.entidad = 'Distribuidora' and a.entidadId = :distribuidoraId))
            """)
    List<UUID> usuariosConFilas(@Param("distribuidoraId") UUID distribuidoraId);
}
