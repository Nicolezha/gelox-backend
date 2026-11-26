package com.gelox.backend.repositories;

import com.gelox.backend.entities.UnidadMedida;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UnidadMedidaRepository extends JpaRepository<UnidadMedida, UUID> {

    List<UnidadMedida> findAllByOrderByCodigoAsc();

    Optional<UnidadMedida> findByCodigoIgnoreCase(String codigo);

    boolean existsByCodigoIgnoreCase(String codigo);
}
