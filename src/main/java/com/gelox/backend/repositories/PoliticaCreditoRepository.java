package com.gelox.backend.repositories;

import com.gelox.backend.entities.PoliticaCredito;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PoliticaCreditoRepository extends JpaRepository<PoliticaCredito, UUID> {

    /** La política por defecto de la distribuidora (comerciante_id es null). */
    Optional<PoliticaCredito> findByTenantIdAndComercianteIsNull(UUID tenantId);

    /** La excepción de un comerciante, si existe. */
    Optional<PoliticaCredito> findByTenantIdAndComerciante_Id(UUID tenantId, UUID comercianteId);
}
