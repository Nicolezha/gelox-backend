package com.gelox.backend.repositories;

import com.gelox.backend.entities.PlantillaExportacion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface PlantillaExportacionRepository extends JpaRepository<PlantillaExportacion, UUID> {

    /** Una plantilla por proveedor (UNIQUE proveedor_id). Trae las columnas ya ordenadas. */
    @Query("""
            SELECT DISTINCT p FROM PlantillaExportacion p
            LEFT JOIN FETCH p.columnas
            WHERE p.proveedor.id = :proveedorId
            """)
    Optional<PlantillaExportacion> findByProveedorId(@Param("proveedorId") UUID proveedorId);
}
