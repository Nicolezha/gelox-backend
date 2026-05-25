package com.gelox.backend.ventas.rural;

import com.gelox.backend.entities.EstadoEnvio;
import com.gelox.backend.entities.PedidoRural;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

/**
 * RF32 — Repositorio para pedidos rurales.
 * Combina datos de venta + pedido_rural.
 */
public interface VentaRuralRepository extends JpaRepository<PedidoRural, UUID> {

    /** Lista todos los pedidos rurales paginados (sin filtro de estado). */
    @Query("SELECT pr FROM PedidoRural pr JOIN FETCH pr.venta v ORDER BY v.fecha DESC")
    Page<PedidoRural> findAllWithVenta(Pageable pageable);

    /** Lista pedidos rurales filtrados por estado de envío. */
    @Query("SELECT pr FROM PedidoRural pr JOIN FETCH pr.venta v " +
           "WHERE pr.estadoEnvio = :estado ORDER BY v.fecha DESC")
    Page<PedidoRural> findByEstadoEnvio(@Param("estado") EstadoEnvio estado, Pageable pageable);
}
