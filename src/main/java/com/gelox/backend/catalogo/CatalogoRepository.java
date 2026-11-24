package com.gelox.backend.catalogo;

import com.gelox.backend.entities.Producto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CatalogoRepository extends JpaRepository<Producto, UUID> {

    /** Listar activos sin filtro de categoría, ordenados por nombre. */
    Page<Producto> findByActivoTrueOrderByNombreAsc(Pageable pageable);

    /** Listar activos filtrando por categoría de tabla, ordenados por nombre. */
    Page<Producto> findByActivoTrueAndCategoriaIdOrderByNombreAsc(UUID categoriaId, Pageable pageable);

    /** Verificar código duplicado entre productos activos. */
    boolean existsByCodigoTecnicoAndActivoTrue(String codigoTecnico);

    /**
     * Verificar si otro producto activo (distinto a {@code id}) ya usa ese código.
     * Se usa al editar para permitir que el mismo producto mantenga su código.
     */
    boolean existsByCodigoTecnicoAndActivoTrueAndIdNot(String codigoTecnico, UUID id);

    /** RF63 — el SKU es único por distribuidora (uq_producto_tenant_sku), activo o no. */
    boolean existsBySku(String sku);

    boolean existsBySkuAndIdNot(String sku, UUID id);

    /** Buscar producto activo por id. */
    Optional<Producto> findByIdAndActivoTrue(UUID id);
}
