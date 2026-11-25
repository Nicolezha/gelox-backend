package com.gelox.backend.entities;

import com.gelox.backend.entities.tenant.TenantAware;
import com.gelox.backend.tenant.TenantEntityListener;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Filter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;

/**
 * RF65 — Abono de un comerciante contra su deuda. {@code planilla} es opcional:
 * null cuando el abono es general y no se aplica a una planilla concreta.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@EntityListeners(TenantEntityListener.class)
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
@Table(name = "abono_comerciante")
public class AbonoComerciante implements TenantAware {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "comerciante_id", nullable = false)
    private Comerciante comerciante;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "planilla_id")
    private PlanillaComerciante planilla;

    @Column(name = "fecha", nullable = false)
    private LocalDate fecha;

    @Column(name = "valor", nullable = false, precision = 15, scale = 2)
    private BigDecimal valor;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @Column(name = "observacion", columnDefinition = "TEXT")
    private String observacion;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (fecha == null) fecha = LocalDate.now(ZoneId.of("America/Bogota"));
        if (createdAt == null) createdAt = LocalDateTime.now(ZoneId.of("America/Bogota"));
    }
}
