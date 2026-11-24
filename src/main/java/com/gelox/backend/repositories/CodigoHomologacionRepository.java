package com.gelox.backend.repositories;

import com.gelox.backend.entities.CodigoHomologacion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CodigoHomologacionRepository extends JpaRepository<CodigoHomologacion, UUID> {

    List<CodigoHomologacion> findByProductoId(UUID productoId);

    Optional<CodigoHomologacion> findByProveedorIdAndCodigo(UUID proveedorId, String codigo);

    Optional<CodigoHomologacion> findByProveedorIdAndProductoId(UUID proveedorId, UUID productoId);

    /** RF67 — todos los códigos de un proveedor con su producto ya cargado. */
    @Query("SELECT h FROM CodigoHomologacion h JOIN FETCH h.producto WHERE h.proveedor.id = :proveedorId")
    List<CodigoHomologacion> listarPorProveedor(@Param("proveedorId") UUID proveedorId);
}
