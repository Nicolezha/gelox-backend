package com.gelox.backend.repositories;

import com.gelox.backend.entities.Perdida;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface PerdidaRepository extends JpaRepository<Perdida, UUID> {

    List<Perdida> findByFechaBetweenOrderByFechaDesc(LocalDate inicio, LocalDate fin);

    List<Perdida> findByProductoIdOrderByFechaDesc(UUID productoId);
}
