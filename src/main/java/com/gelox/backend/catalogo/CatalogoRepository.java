package com.gelox.backend.catalogo;

import com.gelox.backend.entities.Producto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface CatalogoRepository extends JpaRepository<Producto, UUID> {

    /**
     * RF18 / RF64 — productos activos ordenados por nombre, con filtros opcionales.
     * Cada filtro por id se apaga con su bandera; {@code q} y {@code stock} se apagan con cadena vacía.
     * {@code q} busca en nombre, SKU, código técnico y marca. {@code stock}: BAJO (actual ≤ mínimo),
     * MEDIO (actual ≤ medio, incluye los bajos si medio ≥ mínimo) o SIN (actual = 0).
     */
    @Query("""
            SELECT p FROM Producto p
            WHERE p.activo = true
              AND (:todasCategorias = true OR p.categoria.id = :categoriaId)
              AND (:todosProveedores = true OR p.proveedor.id = :proveedorId)
              AND (:q = '' OR LOWER(p.nombre) LIKE LOWER(CONCAT('%', :q, '%'))
                           OR LOWER(p.codigoTecnico) LIKE LOWER(CONCAT('%', :q, '%'))
                           OR LOWER(COALESCE(p.sku, '')) LIKE LOWER(CONCAT('%', :q, '%'))
                           OR LOWER(COALESCE(p.marca, '')) LIKE LOWER(CONCAT('%', :q, '%')))
              AND (:stock = ''
                   OR (:stock = 'BAJO'  AND p.stockActual <= p.stockMinimo)
                   OR (:stock = 'MEDIO' AND p.stockActual <= p.stockMedio)
                   OR (:stock = 'SIN'   AND p.stockActual <= 0))
            ORDER BY p.nombre ASC
            """)
    Page<Producto> buscar(@Param("todasCategorias") boolean todasCategorias,
                          @Param("categoriaId") UUID categoriaId,
                          @Param("todosProveedores") boolean todosProveedores,
                          @Param("proveedorId") UUID proveedorId,
                          @Param("q") String q,
                          @Param("stock") String stock,
                          Pageable pageable);

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
