package com.gelox.backend.repositories;

import com.gelox.backend.entities.EstadoRecomendacion;
import com.gelox.backend.entities.RecomendacionCompra;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface RecomendacionCompraRepository extends JpaRepository<RecomendacionCompra, UUID> {

    /** Historial paginado (RF57): del tenant, por estados y rango [desde, hasta). */
    @Query("""
            SELECT r FROM RecomendacionCompra r
            WHERE r.tenantId = :tenantId
              AND r.estado IN :estados
              AND r.createdAt >= :desde AND r.createdAt < :hasta
            ORDER BY r.createdAt DESC
            """)
    Page<RecomendacionCompra> buscar(@Param("tenantId") UUID tenantId,
                                     @Param("estados") Collection<EstadoRecomendacion> estados,
                                     @Param("desde") LocalDateTime desde,
                                     @Param("hasta") LocalDateTime hasta,
                                     Pageable pageable);

    /** Recomendaciones del tenant en el rango [desde, hasta) con sus ítems, para las métricas. */
    @Query("""
            SELECT DISTINCT r FROM RecomendacionCompra r
            LEFT JOIN FETCH r.items
            WHERE r.tenantId = :tenantId
              AND r.createdAt >= :desde AND r.createdAt < :hasta
            """)
    List<RecomendacionCompra> buscarConItems(@Param("tenantId") UUID tenantId,
                                             @Param("desde") LocalDateTime desde,
                                             @Param("hasta") LocalDateTime hasta);
}
