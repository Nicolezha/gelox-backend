package com.gelox.backend.repositories;

import com.gelox.backend.entities.MovimientoInventario;
import com.gelox.backend.entities.TipoMovimiento;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface MovimientoInventarioRepository extends JpaRepository<MovimientoInventario, UUID> {

    List<MovimientoInventario> findByProductoIdOrderByFechaDesc(UUID productoId);

    List<MovimientoInventario> findByTipoOrderByFechaDesc(TipoMovimiento tipo);
}
