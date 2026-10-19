package com.gelox.backend.repositories;

import com.gelox.backend.entities.Distribuidora;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface DistribuidoraRepository extends JpaRepository<Distribuidora, UUID> {

    Optional<Distribuidora> findBySubdominio(String subdominio);

    boolean existsByNit(String nit);

    boolean existsBySubdominio(String subdominio);
}
