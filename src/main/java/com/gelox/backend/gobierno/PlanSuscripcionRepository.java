package com.gelox.backend.gobierno;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PlanSuscripcionRepository extends JpaRepository<PlanSuscripcion, UUID> {

    Optional<PlanSuscripcion> findByNombreIgnoreCase(String nombre);
}
