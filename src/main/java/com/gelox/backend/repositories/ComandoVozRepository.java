package com.gelox.backend.repositories;

import com.gelox.backend.entities.ComandoVoz;
import com.gelox.backend.entities.MetodoClasificacion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.UUID;

public interface ComandoVozRepository extends JpaRepository<ComandoVoz, UUID> {

    /** RF70 — comandos de la distribuidora clasificados con {@code metodo} desde {@code desde}. */
    long countByTenantIdAndMetodoClasificacionAndCreatedAtGreaterThanEqual(
            UUID tenantId, MetodoClasificacion metodo, LocalDateTime desde);
}
