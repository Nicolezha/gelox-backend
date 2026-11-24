package com.gelox.backend.repositories;

import com.gelox.backend.entities.PlanillaComerciante;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PlanillaComercianteRepository extends JpaRepository<PlanillaComerciante, UUID> {

    Optional<PlanillaComerciante> findByComercianteIdAndFecha(UUID comercianteId, LocalDate fecha);

    /** Verifica si ya existe una planilla abierta para ese comerciante en esa fecha (anti-duplicado). */
    boolean existsByComercianteIdAndFechaAndCerradaFalse(UUID comercianteId, LocalDate fecha);

    /** Busca una planilla cerrada que pertenezca al comerciante indicado (RF36-39). */
    Optional<PlanillaComerciante> findByIdAndComerciante_IdAndCerrada(UUID id, UUID comercianteId, boolean cerrada);

    /**
     * RF65 — planillas cerradas del comerciante (FP-15 §2.17.1): saldo de cada una =
     * total_ganancia (monto cobrado) − efectivo_recibido; la deuda es la suma de los
     * saldos positivos, y la planilla está vencida si ese saldo es positivo y su
     * fecha_limite_pago ya pasó. Los abonos se descuentan aparte, de forma agregada
     * (no se reparten entre planillas concretas).
     */
    @Query("""
            SELECT p FROM PlanillaComerciante p
            WHERE p.comerciante.id = :comercianteId AND p.cerrada = true
            """)
    List<PlanillaComerciante> findCerradasPorComerciante(@Param("comercianteId") UUID comercianteId);

    default BigDecimal sumarSaldoCerradas(UUID comercianteId) {
        return findCerradasPorComerciante(comercianteId).stream()
                .map(p -> {
                    BigDecimal cobrado = p.getTotalGanancia() != null ? p.getTotalGanancia() : BigDecimal.ZERO;
                    BigDecimal recibido = p.getEfectivoRecibido() != null ? p.getEfectivoRecibido() : BigDecimal.ZERO;
                    return cobrado.subtract(recibido);
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    default long contarVencidas(UUID comercianteId, LocalDate hoy) {
        return findCerradasPorComerciante(comercianteId).stream()
                .filter(p -> p.getFechaLimitePago() != null && p.getFechaLimitePago().isBefore(hoy))
                .filter(p -> {
                    BigDecimal cobrado = p.getTotalGanancia() != null ? p.getTotalGanancia() : BigDecimal.ZERO;
                    BigDecimal recibido = p.getEfectivoRecibido() != null ? p.getEfectivoRecibido() : BigDecimal.ZERO;
                    return cobrado.subtract(recibido).signum() > 0;
                })
                .count();
    }
}
