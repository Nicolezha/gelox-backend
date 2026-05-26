package com.gelox.backend.repositories;

import com.gelox.backend.entities.ItemPlanilla;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ItemPlanillaRepository extends JpaRepository<ItemPlanilla, UUID> {

    Optional<ItemPlanilla> findByIdAndPlanillaId(UUID id, UUID planillaId);

    List<ItemPlanilla> findByPlanillaId(UUID planillaId);
}
