package com.gelox.backend.catalogo;

import com.gelox.backend.entities.CategoriaProducto;
import com.gelox.backend.entities.Producto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CatalogoRepository extends JpaRepository<Producto, UUID> {

    /** Listar activos sin filtro de categoría, ordenados por nombre. */
    Page<Producto> findByActivoTrueOrderByNombreAsc(Pageable pageable);

    /** Listar activos filtrando por categoría, ordenados por nombre. */
    Page<Producto> findByActivoTrueAndCategoriaOrderByNombreAsc(
            CategoriaProducto categoria, Pageable pageable);

    /** Verificar código duplicado entre productos activos. */
    boolean existsByCodigoTecnicoAndActivoTrue(String codigoTecnico);

    /**
     * Verificar si otro producto activo (distinto a {@code id}) ya usa ese código.
     * Se usa al editar para permitir que el mismo producto mantenga su código.
     */
    boolean existsByCodigoTecnicoAndActivoTrueAndIdNot(String codigoTecnico, UUID id);

    /** Buscar producto activo por id. */
    Optional<Producto> findByIdAndActivoTrue(UUID id);
}
