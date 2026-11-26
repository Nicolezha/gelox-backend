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
 * RF65 — Política de crédito. {@code comerciante} null es la política por
 * defecto de la distribuidora; una fila con comerciante es la excepción de
 * ese comerciante (FP-15 §2.17.1).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@EntityListeners(TenantEntityListener.class)
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
@Table(name = "politica_credito")
public class PoliticaCredito implements TenantAware {

    private static final ZoneId ZONA_BOGOTA = ZoneId.of("America/Bogota");

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    /** Null = política por defecto de la distribuidora. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "comerciante_id")
    private Comerciante comerciante;

    @Column(name = "cupo", nullable = false, precision = 15, scale = 2)
    private BigDecimal cupo;

    @Column(name = "plazo_dias", nullable = false)
    private Integer plazoDias;

    @Enumerated(EnumType.STRING)
    @Column(name = "politica_vencimiento", nullable = false, length = 12)
    private PoliticaVencimiento politicaVencimiento;

    /** Una excepción inactiva no se aplica: rige la política por defecto (FP-15 §2.17.1). */
    @Builder.Default
    @Column(name = "activo", nullable = false)
    private Boolean activo = true;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /** Usuario que hizo el último cambio; null en la fila sembrada por la migración. */
    @Column(name = "actualizado_por")
    private UUID actualizadoPor;

    @PrePersist
    protected void onCreate() {
        updatedAt = LocalDateTime.now(ZONA_BOGOTA);
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now(ZONA_BOGOTA);
    }
}
