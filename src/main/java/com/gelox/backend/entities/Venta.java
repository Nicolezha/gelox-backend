package com.gelox.backend.entities;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "venta")
public class Venta {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "canal", nullable = false)
    private CanalVenta canal;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false)
    @Builder.Default
    private EstadoVenta estado = EstadoVenta.EN_PROCESO;

    @Column(name = "fecha", nullable = false)
    private LocalDateTime fecha;

    @Column(name = "total", nullable = false)
    @Builder.Default
    private BigDecimal total = BigDecimal.ZERO;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    /** RF33 — FK nullable hacia cliente_rural. Presente solo en ventas de canal RURAL. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cliente_rural_id")
    private ClienteRural clienteRural;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (fecha == null)     fecha     = LocalDateTime.now();
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (total == null)     total     = BigDecimal.ZERO;
    }
}
