package com.gelox.backend.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "cierre_caja", uniqueConstraints = @UniqueConstraint(columnNames = "fecha"))
public class CierreCaja {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "fecha", nullable = false, unique = true)
    private LocalDate fecha;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id")
    private Usuario usuario;

    @Column(name = "monto_calculado_ventanilla", nullable = false, precision = 15, scale = 2)
    private BigDecimal montoCalculadoVentanilla;

    @Column(name = "monto_calculado_rural", nullable = false, precision = 15, scale = 2)
    private BigDecimal montoCalculadoRural;

    @Column(name = "monto_calculado_comerciantes", nullable = false, precision = 15, scale = 2)
    private BigDecimal montoCalculadoComerciantes;

    @Column(name = "monto_calculado_total", nullable = false, precision = 15, scale = 2)
    private BigDecimal montoCalculadoTotal;

    @Column(name = "monto_fisico_ventanilla", nullable = false, precision = 15, scale = 2)
    private BigDecimal montoFisicoVentanilla;

    @Column(name = "monto_fisico_rural", nullable = false, precision = 15, scale = 2)
    private BigDecimal montoFisicoRural;

    @Column(name = "monto_fisico_comerciantes", nullable = false, precision = 15, scale = 2)
    private BigDecimal montoFisicoComerciantes;

    @Column(name = "monto_fisico_total", nullable = false, precision = 15, scale = 2)
    private BigDecimal montoFisicoTotal;

    @Column(name = "diferencia_ventanilla", nullable = false, precision = 15, scale = 2)
    private BigDecimal diferenciaVentanilla;

    @Column(name = "diferencia_rural", nullable = false, precision = 15, scale = 2)
    private BigDecimal diferenciaRural;

    @Column(name = "diferencia_comerciantes", nullable = false, precision = 15, scale = 2)
    private BigDecimal diferenciaComerciantes;

    @Column(name = "diferencia_total", nullable = false, precision = 15, scale = 2)
    private BigDecimal diferenciaTotal;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}