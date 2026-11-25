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
 * RF66 — Esquema de liquidación vigente de la distribuidora (FP-15 §2.14.1 y §2.17.2).
 * Hay una sola fila por distribuidora y se actualiza en su sitio. El historial
 * queda en la copia que guarda cada planilla (que un cambio posterior no altera)
 * y en la auditoría ESQUEMA_LIQUIDACION_CAMBIAR.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@EntityListeners(TenantEntityListener.class)
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
@Table(name = "esquema_liquidacion",
        uniqueConstraints = @UniqueConstraint(name = "uq_esquema_liquidacion_tenant", columnNames = "tenant_id"))
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

    /** MARGEN no usa valor (null); COMISION es un % (0 &lt; valor ≤ 100); TARIFA_FIJA es el monto fijo. */
    @Column(name = "valor", precision = 12, scale = 2)
    private BigDecimal valor;

    @Column(name = "vigente_desde", nullable = false)
    private LocalDateTime vigenteDesde;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /** Usuario que hizo el último cambio; null en la fila sembrada por la migración. */
    @Column(name = "actualizado_por")
    private UUID actualizadoPor;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now(ZoneId.of("America/Bogota"));
        if (vigenteDesde == null) vigenteDesde = now;
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now(ZoneId.of("America/Bogota"));
    }
}
