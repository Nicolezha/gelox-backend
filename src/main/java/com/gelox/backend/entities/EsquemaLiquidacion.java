package com.gelox.backend.entities;

import com.gelox.backend.entities.tenant.TenantAware;
import com.gelox.backend.tenant.TenantEntityListener;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Filter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;

/**
 * RF66 — Esquema de liquidación vigente de la distribuidora (FP-15 §2.17.2).
 * Es un historial: la fila con {@code vigenteDesde} más reciente es la
 * actual. Cada planilla guarda una copia del esquema y el valor con los que
 * se liquidó, para que un cambio posterior no altere planillas ya cerradas.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@EntityListeners(TenantEntityListener.class)
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
@Table(name = "esquema_liquidacion")
public class EsquemaLiquidacion implements TenantAware {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false, length = 12)
    private TipoEsquemaLiquidacion tipo;

    /** MARGEN no usa valor (queda en 0); COMISION es un % (0–100); TARIFA_FIJA es el monto fijo. */
    @Column(name = "valor", nullable = false, precision = 15, scale = 4)
    @Builder.Default
    private BigDecimal valor = BigDecimal.ZERO;

    @Column(name = "vigente_desde", nullable = false)
    private LocalDateTime vigenteDesde;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now(ZoneId.of("America/Bogota"));
        if (vigenteDesde == null) vigenteDesde = now;
        createdAt = now;
    }
}
