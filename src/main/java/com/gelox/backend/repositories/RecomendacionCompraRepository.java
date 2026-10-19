package com.gelox.backend.repositories;

import com.gelox.backend.entities.RecomendacionCompra;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface RecomendacionCompraRepository extends JpaRepository<RecomendacionCompra, UUID> {
}
