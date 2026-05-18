package com.gelox.backend.repositories;

import com.gelox.backend.entities.EventoSistema;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface EventoSistemaRepository extends JpaRepository<EventoSistema, UUID> {

    Page<EventoSistema> findAllByOrderByFechaDesc(Pageable pageable);
}
