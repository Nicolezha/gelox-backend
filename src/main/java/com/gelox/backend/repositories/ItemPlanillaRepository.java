package com.gelox.backend.repositories;

import com.gelox.backend.entities.ItemPlanilla;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ItemPlanillaRepository extends JpaRepository<ItemPlanilla, UUID> {

    Optional<ItemPlanilla> findByIdAndPlanillaId(UUID id, UUID planillaId);

    List<ItemPlanilla> findByPlanillaId(UUID planillaId);

    /** Carga ítems con producto en una sola query para el detalle de planilla (RF36-39). */
    @Query("SELECT i FROM ItemPlanilla i JOIN FETCH i.producto WHERE i.planilla.id = :planillaId")
    List<ItemPlanilla> findByPlanillaIdWithProducto(@Param("planillaId") UUID planillaId);
}
