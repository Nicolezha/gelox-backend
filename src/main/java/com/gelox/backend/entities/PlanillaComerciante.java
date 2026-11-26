package com.gelox.backend.entities;

import com.gelox.backend.entities.tenant.TenantAware;
import com.gelox.backend.tenant.TenantEntityListener;
import jakarta.persistence.*;
import org.hibernate.annotations.Filter;
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
@EntityListeners(TenantEntityListener.class)
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
@Table(name = "planilla_comerciante")
public class PlanillaComerciante implements TenantAware {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(name = "tenant_id")
    private UUID tenantId;

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

    /** RF66 — lo que se queda el comerciante, calculado al liquidar según el esquema vigente. */
    @Column(name = "ganancia_comerciante", precision = 15, scale = 2)
    private BigDecimal gananciaComerciante;

    /** Copia del esquema con el que se liquidó (no cambia si el esquema vigente cambia después). */
    @Enumerated(EnumType.STRING)
    @Column(name = "esquema_aplicado", length = 12)
    private TipoEsquemaLiquidacion esquemaAplicado;

    @Column(name = "valor_esquema", precision = 12, scale = 2)
    private BigDecimal valorEsquema;

    /** RF65 — fecha de la planilla + plazo de la política de crédito efectiva; el Gerente puede extenderla. */
    @Column(name = "fecha_limite_pago")
    private LocalDate fechaLimitePago;

    @Column(name = "timestamp_cierre")
    private LocalDateTime timestampCierre;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}
