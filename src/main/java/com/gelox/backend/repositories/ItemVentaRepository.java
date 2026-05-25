package com.gelox.backend.repositories;

import com.gelox.backend.entities.ItemVenta;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ItemVentaRepository extends JpaRepository<ItemVenta, UUID> {
}
