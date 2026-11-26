package com.gelox.backend.repositories;

import com.gelox.backend.entities.AbonoComerciante;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AbonoComercianteRepository extends JpaRepository<AbonoComerciante, UUID> {

    /** Para mostrar el historial de abonos (más reciente primero). */
    List<AbonoComerciante> findByComerciante_IdOrderByFechaDesc(UUID comercianteId);

    /** Para repartir los abonos sobre las planillas (FIFO: el más antiguo primero). */
    List<AbonoComerciante> findByComerciante_IdOrderByFechaAsc(UUID comercianteId);
}
