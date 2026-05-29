package com.gelox.backend.entities;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "planilla_comerciante")
public class PlanillaComerciante {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "comerciante_id", nullable = false)
    private Comerciante comerciante;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @Column(name = "fecha", nullable = false)
    private LocalDate fecha;

    @Builder.Default
    @Column(name = "cerrada", nullable = false)
    private Boolean cerrada = false;

    @Column(name = "total_ganancia", precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal totalGanancia = BigDecimal.ZERO;

    @Column(name = "efectivo_recibido", precision = 15, scale = 2)
    private BigDecimal efectivoRecibido;

    @Column(name = "timestamp_cierre")
    private LocalDateTime timestampCierre;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}
