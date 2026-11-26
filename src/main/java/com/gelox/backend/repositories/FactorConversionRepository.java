package com.gelox.backend.repositories;

import com.gelox.backend.entities.FactorConversion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FactorConversionRepository extends JpaRepository<FactorConversion, UUID> {

    List<FactorConversion> findByProductoId(UUID productoId);

    List<FactorConversion> findByProductoIdIn(java.util.Collection<UUID> productoIds);

    /** Factor de una unidad (por código) hacia la unidad base del producto. */
    @Query("""
            SELECT f FROM FactorConversion f
            WHERE f.producto.id = :productoId
              AND UPPER(f.unidadOrigen.codigo) = UPPER(:codigo)
              AND f.unidadDestino.id = :baseId
            """)
    Optional<FactorConversion> findHaciaBase(@Param("productoId") UUID productoId,
                                             @Param("codigo") String codigo,
                                             @Param("baseId") UUID baseId);

    /** Borrado inmediato (no espera al flush) para poder reinsertar sin chocar con el UNIQUE. */
    @Modifying(flushAutomatically = true)
    @Query("DELETE FROM FactorConversion f WHERE f.producto.id = :productoId")
    void borrarPorProducto(@Param("productoId") UUID productoId);
}
