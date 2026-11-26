package com.gelox.backend.repositories;

import com.gelox.backend.entities.Proveedor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProveedorRepository extends JpaRepository<Proveedor, UUID> {

    /** Búsqueda por nombre o NIT; cadena vacía = sin texto. {@code activo} null = todos. */
    @Query("""
            SELECT p FROM Proveedor p
            WHERE (:q = '' OR LOWER(p.nombre) LIKE LOWER(CONCAT('%', :q, '%'))
                           OR LOWER(COALESCE(p.nit, '')) LIKE LOWER(CONCAT('%', :q, '%')))
              AND (:activo IS NULL OR p.activo = :activo)
            ORDER BY p.nombre ASC
            """)
    List<Proveedor> buscar(@Param("q") String q, @Param("activo") Boolean activo);

    boolean existsByNit(String nit);

    /** RF63 — proveedor por defecto (Nutresa) de los productos sin proveedor. */
    Optional<Proveedor> findFirstByNombreIgnoreCaseAndActivoTrue(String nombre);

    boolean existsByNitAndIdNot(String nit, UUID id);
}
