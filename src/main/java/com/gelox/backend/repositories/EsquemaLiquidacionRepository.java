package com.gelox.backend.repositories;

import com.gelox.backend.entities.EsquemaLiquidacion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface EsquemaLiquidacionRepository extends JpaRepository<EsquemaLiquidacion, UUID> {

    /** El esquema vigente de la distribuidora (el más reciente por vigenteDesde). */
    Optional<EsquemaLiquidacion> findTopByTenantIdOrderByVigenteDesdeDesc(UUID tenantId);
}
