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
    @Column(name = "politica_vencimiento", nullable = false, length = 15)
    private PoliticaVencimiento politicaVencimiento;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now(ZONA_BOGOTA);
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now(ZONA_BOGOTA);
    }
}
