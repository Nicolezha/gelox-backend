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

    /** RF24 — solo productos con stock mínimo configurado (> 0); sin mínimo no hay alerta. */
    @Query("SELECT p FROM Producto p WHERE p.activo = true AND p.stockMinimo > 0 AND p.stockActual <= p.stockMinimo ORDER BY p.nombre ASC")
    List<Producto> findProductosBajoStock();

    /**
     * Inventario muerto — productos activos con stock y sin salidas (venta/despacho) desde {@code desde}.
     * Columnas: id, codigo_tecnico, nombre, stock_actual, ultima_salida, created_at, valor_inmovilizado.
     * precio_costo es por caja (por unidad si el producto no maneja cajas).
     */
    @Query(value = """
            SELECT p.id::text, p.codigo_tecnico, p.nombre, p.stock_actual,
                   (SELECT MAX(m.fecha) FROM movimiento_inventario m
                     WHERE m.producto_id = p.id AND m.tipo IN ('SALIDA_VENTA', 'SALIDA_DESPACHO')) AS ultima_salida,
                   p.created_at,
                   p.stock_actual * COALESCE(p.precio_costo, 0)
                       / COALESCE(NULLIF(p.unidades_por_caja, 0), 1)::numeric AS valor_inmovilizado
            FROM producto p
            WHERE p.activo = true
              AND p.stock_actual > 0
              AND NOT EXISTS (SELECT 1 FROM movimiento_inventario m
                               WHERE m.producto_id = p.id
                                 AND m.tipo IN ('SALIDA_VENTA', 'SALIDA_DESPACHO')
                                 AND m.fecha >= :desde)
            ORDER BY valor_inmovilizado DESC, p.nombre ASC
            """, nativeQuery = true)
    List<Object[]> findInventarioMuerto(@Param("desde") java.time.LocalDateTime desde);

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
