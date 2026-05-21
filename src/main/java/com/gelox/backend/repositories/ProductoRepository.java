package com.gelox.backend.repositories;

import com.gelox.backend.entities.CategoriaProducto;
import com.gelox.backend.entities.Producto;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ProductoRepository extends JpaRepository<Producto, UUID> {

    List<Producto> findByActivoTrue();

    List<Producto> findByActivoTrueAndCategoria(CategoriaProducto categoria);

    boolean existsByCodigoTecnico(String codigoTecnico);
}
