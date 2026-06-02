package com.gelox.backend.repositories;

import com.gelox.backend.entities.CategoriaProducto;
import com.gelox.backend.entities.Producto;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProductoRepository extends JpaRepository<Producto, UUID> {

    List<Producto> findByActivoTrue();

    List<Producto> findByActivoTrueOrderByNombreAsc();

    List<Producto> findByActivoTrueAndCategoria(CategoriaProducto categoria);

    boolean existsByCodigoTecnico(String codigoTecnico);

    @Query("SELECT p FROM Producto p WHERE p.activo = true AND p.stockActual <= p.stockMinimo ORDER BY p.nombre ASC")
    List<Producto> findProductosBajoStock();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Producto p WHERE p.id IN :ids")
    List<Producto> findByIdInWithLock(@Param("ids") List<UUID> ids);

    /** Bloqueo pesimista sobre un único producto (usado en descontarStock). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Producto p WHERE p.id = :id")
    Optional<Producto> findByIdWithLock(@Param("id") UUID id);

    /**
     * RF27 — Listado con filtros opcionales.
     * Pasar cadena vacía ("") cuando el filtro no se usa.
     */
    @Query("""
            SELECT p FROM Producto p
            WHERE p.activo = true
            AND (:q = '' OR LOWER(p.nombre) LIKE LOWER(CONCAT('%', :q, '%'))
                         OR LOWER(p.codigoTecnico) LIKE LOWER(CONCAT('%', :q, '%')))
            AND (:estado = ''
                 OR (:estado = 'BAJO_STOCK' AND p.stockActual <= p.stockMinimo)
                 OR (:estado = 'NORMAL'     AND p.stockActual >  p.stockMinimo))
            ORDER BY p.stockActual DESC, p.nombre ASC
            """)
    List<Producto> findConFiltros(@Param("q") String q, @Param("estado") String estado);
}
