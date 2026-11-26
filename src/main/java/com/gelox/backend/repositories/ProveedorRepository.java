package com.gelox.backend.repositories;

import com.gelox.backend.entities.Proveedor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
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

    /** Filas [proveedorId, cantidad] de productos activos; un proveedor sin productos no aparece. */
    @Query("""
            SELECT p.proveedor.id, COUNT(p) FROM Producto p
            WHERE p.activo = true AND p.proveedor.id IN :ids
            GROUP BY p.proveedor.id
            """)
    List<Object[]> contarProductosActivos(@Param("ids") Collection<UUID> ids);

    /** Filas [proveedorId, nombre de la plantilla de exportación] (una plantilla por proveedor). */
    @Query("SELECT t.proveedor.id, t.nombre FROM PlantillaExportacion t WHERE t.proveedor.id IN :ids")
    List<Object[]> nombresDePlantillas(@Param("ids") Collection<UUID> ids);

    boolean existsByNit(String nit);

    /** RF63 — proveedor por defecto (Nutresa) de los productos sin proveedor. */
    Optional<Proveedor> findFirstByNombreIgnoreCaseAndActivoTrue(String nombre);

    boolean existsByNitAndIdNot(String nit, UUID id);
}
