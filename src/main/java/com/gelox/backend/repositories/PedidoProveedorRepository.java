package com.gelox.backend.repositories;

import com.gelox.backend.entities.EstadoPedido;
import com.gelox.backend.entities.PedidoProveedor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PedidoProveedorRepository extends JpaRepository<PedidoProveedor, UUID> {

    List<PedidoProveedor> findByEstadoOrderByFechaDesc(EstadoPedido estado);

    List<PedidoProveedor> findAllByOrderByFechaDesc();

    /**
     * Resumen para el historial de pedidos (RF21 — GET /pedidos).
     * Devuelve: [id::text, fecha, estado::text, notas, totalSolicitado::int]
     * Filtros opcionales: estado (null = todos), fechaInicio/fechaFin (null = sin límite).
     */
    @Query(value = """
            SELECT
                p.id::text                                          AS id,
                p.fecha                                             AS fecha,
                p.estado::text                                      AS estado,
                p.notas                                             AS notas,
                COALESCE(SUM(COALESCE(i.cantidad_cajas, 0) + COALESCE(i.cantidad_unidades, 0)), 0)::int AS total_solicitado
            FROM pedido_proveedor p
            LEFT JOIN item_pedido_proveedor i ON i.pedido_id = p.id
            WHERE (:estado IS NULL OR p.estado::text = :estado)
              AND p.fecha >= CAST(:fechaInicio AS date)
              AND p.fecha <= CAST(:fechaFin    AS date)
            GROUP BY p.id, p.fecha, p.estado, p.notas
            ORDER BY p.fecha DESC
            """, nativeQuery = true)
    List<Object[]> findResumenWithFilters(
            @Param("estado")      String estado,
            @Param("fechaInicio") String fechaInicio,
            @Param("fechaFin")    String fechaFin);

    /**
     * Detalle completo de un pedido con todos sus ítems y productos cargados
     * en una sola consulta (RF22 — GET /pedidos/{id}).
     */
    @Query("""
            SELECT p FROM PedidoProveedor p
            LEFT JOIN FETCH p.items i
            LEFT JOIN FETCH i.producto
            WHERE p.id = :id
            """)
    Optional<PedidoProveedor> findByIdWithItems(@Param("id") UUID id);
}
