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

    /** Método de pago registrado en ventas de canal VENTANILLA. Null para ventas rurales. */
    @Enumerated(EnumType.STRING)
    @Column(name = "metodo_de_pago", length = 20)
    private MetodoPago metodoDePago;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (fecha == null)     fecha     = LocalDateTime.now();
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (total == null)     total     = BigDecimal.ZERO;
    }
}
