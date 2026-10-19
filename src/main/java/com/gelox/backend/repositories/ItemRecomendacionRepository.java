package com.gelox.backend.repositories;

import com.gelox.backend.entities.ItemRecomendacion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ItemRecomendacionRepository extends JpaRepository<ItemRecomendacion, UUID> {
}
