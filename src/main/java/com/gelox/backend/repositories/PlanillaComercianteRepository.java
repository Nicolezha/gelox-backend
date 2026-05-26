package com.gelox.backend.repositories;

import com.gelox.backend.entities.PlanillaComerciante;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

public interface PlanillaComercianteRepository extends JpaRepository<PlanillaComerciante, UUID> {

    Optional<PlanillaComerciante> findByComercianteIdAndFecha(UUID comercianteId, LocalDate fecha);
}
