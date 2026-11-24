package com.gelox.backend.repositories;

import com.gelox.backend.entities.AbonoComerciante;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface AbonoComercianteRepository extends JpaRepository<AbonoComerciante, UUID> {

    List<AbonoComerciante> findByComerciante_IdOrderByFechaDesc(UUID comercianteId);

    @Query("SELECT COALESCE(SUM(a.valor), 0) FROM AbonoComerciante a WHERE a.comerciante.id = :comercianteId")
    BigDecimal sumarValorPorComerciante(@Param("comercianteId") UUID comercianteId);
}
