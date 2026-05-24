package com.gelox.backend.repositories;

import com.gelox.backend.entities.ItemPedidoProveedor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface ItemPedidoProveedorRepository extends JpaRepository<ItemPedidoProveedor, UUID> {

    @Query("SELECT i FROM ItemPedidoProveedor i JOIN FETCH i.producto WHERE i.pedido.id = :pedidoId")
    List<ItemPedidoProveedor> findByPedidoIdWithProducto(@Param("pedidoId") UUID pedidoId);
}
