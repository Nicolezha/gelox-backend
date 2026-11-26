package com.gelox.backend.repositories;

import com.gelox.backend.entities.EsquemaLiquidacion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface EsquemaLiquidacionRepository extends JpaRepository<EsquemaLiquidacion, UUID> {

    /** El esquema de la distribuidora (hay uno solo: uq_esquema_liquidacion_tenant). */
    Optional<EsquemaLiquidacion> findByTenantId(UUID tenantId);
}
