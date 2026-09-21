package com.gelox.backend.repositories;

import com.gelox.backend.entities.ComandoVoz;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ComandoVozRepository extends JpaRepository<ComandoVoz, UUID> {
}
