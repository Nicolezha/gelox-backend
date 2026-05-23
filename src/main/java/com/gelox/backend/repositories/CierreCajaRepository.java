package com.gelox.backend.repositories;

import com.gelox.backend.entities.CierreCaja;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CierreCajaRepository extends JpaRepository<CierreCaja, UUID> {

    Optional<CierreCaja> findByFecha(LocalDate fecha);

    boolean existsByFecha(LocalDate fecha);

    @Query("""
            SELECT c FROM CierreCaja c
            WHERE c.fecha BETWEEN :desde AND :hasta
            AND (:estado IS NULL
                 OR (:estado = 'perfecto'  AND c.diferenciaTotal = 0)
                 OR (:estado = 'mayor'     AND c.diferenciaTotal > 0)
                 OR (:estado = 'menor'     AND c.diferenciaTotal < 0))
            ORDER BY c.fecha DESC
            """)
    Page<CierreCaja> findByFiltros(
            @Param("desde") LocalDate desde,
            @Param("hasta") LocalDate hasta,
            @Param("estado") String estado,
            Pageable pageable);
}