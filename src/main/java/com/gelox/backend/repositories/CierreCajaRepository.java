package com.gelox.backend.repositories;

import com.gelox.backend.entities.CierreCaja;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CierreCajaRepository extends JpaRepository<CierreCaja, UUID> {

    Optional<CierreCaja> findByFecha(LocalDate fecha);

    boolean existsByFecha(LocalDate fecha);

    // Sin filtro de estado — todos los cierres en el rango
    Page<CierreCaja> findByFechaBetweenOrderByFechaDesc(
            LocalDate desde, LocalDate hasta, Pageable pageable);

    // estado = "perfecto" → diferenciaTotal == 0
    Page<CierreCaja> findByFechaBetweenAndDiferenciaTotalOrderByFechaDesc(
            LocalDate desde, LocalDate hasta, BigDecimal diferenciaTotal, Pageable pageable);

    // estado = "mayor" → diferenciaTotal > 0
    Page<CierreCaja> findByFechaBetweenAndDiferenciaTotalGreaterThanOrderByFechaDesc(
            LocalDate desde, LocalDate hasta, BigDecimal diferenciaTotal, Pageable pageable);

    // estado = "menor" → diferenciaTotal < 0
    Page<CierreCaja> findByFechaBetweenAndDiferenciaTotalLessThanOrderByFechaDesc(
            LocalDate desde, LocalDate hasta, BigDecimal diferenciaTotal, Pageable pageable);
}
