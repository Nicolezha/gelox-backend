package com.gelox.backend.repositories;

import com.gelox.backend.entities.EstadoPedido;
import com.gelox.backend.entities.PedidoProveedor;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PedidoProveedorRepository extends JpaRepository<PedidoProveedor, UUID> {

    List<PedidoProveedor> findByEstadoOrderByFechaDesc(EstadoPedido estado);

    List<PedidoProveedor> findAllByOrderByFechaDesc();
}
