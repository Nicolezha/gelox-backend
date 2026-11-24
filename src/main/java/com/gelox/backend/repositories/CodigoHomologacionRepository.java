package com.gelox.backend.repositories;

import com.gelox.backend.entities.CodigoHomologacion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CodigoHomologacionRepository extends JpaRepository<CodigoHomologacion, UUID> {

    List<CodigoHomologacion> findByProductoId(UUID productoId);

    Optional<CodigoHomologacion> findByProveedorIdAndCodigo(UUID proveedorId, String codigo);

    Optional<CodigoHomologacion> findByProveedorIdAndProductoId(UUID proveedorId, UUID productoId);
}
