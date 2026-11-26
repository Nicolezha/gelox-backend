package com.gelox.backend.repositories;

import com.gelox.backend.entities.PlanillaComerciante;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PlanillaComercianteRepository extends JpaRepository<PlanillaComerciante, UUID> {

    Optional<PlanillaComerciante> findByComercianteIdAndFecha(UUID comercianteId, LocalDate fecha);

    /** Verifica si ya existe una planilla abierta para ese comerciante en esa fecha (anti-duplicado). */
    boolean existsByComercianteIdAndFechaAndCerradaFalse(UUID comercianteId, LocalDate fecha);

    /** Busca una planilla cerrada que pertenezca al comerciante indicado (RF36-39). */
    Optional<PlanillaComerciante> findByIdAndComerciante_IdAndCerrada(UUID id, UUID comercianteId, boolean cerrada);

    /**
     * RF65 — planillas cerradas del comerciante, de la más antigua a la más
     * reciente: es el orden en que CreditoService reparte los abonos (FIFO),
     * igual que distribuirAbono() del frontend.
     */
    @Query("""
            SELECT p FROM PlanillaComerciante p
            WHERE p.comerciante.id = :comercianteId AND p.cerrada = true
            ORDER BY p.fecha ASC
            """)
    List<PlanillaComerciante> findCerradasPorComercianteOrdenadas(@Param("comercianteId") UUID comercianteId);
}
