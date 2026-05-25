package com.gelox.backend.repositories;

import com.gelox.backend.entities.CategoriaProducto;
import com.gelox.backend.entities.Producto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface ProductoRepository extends JpaRepository<Producto, UUID> {

    List<Producto> findByActivoTrue();

    List<Producto> findByActivoTrueOrderByNombreAsc();

    List<Producto> findByActivoTrueAndCategoria(CategoriaProducto categoria);

    boolean existsByCodigoTecnico(String codigoTecnico);

    @Query("SELECT p FROM Producto p WHERE p.activo = true AND p.stockActual <= p.stockMinimo ORDER BY p.nombre ASC")
    List<Producto> findProductosBajoStock();
}
