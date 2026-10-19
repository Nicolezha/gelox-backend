package com.gelox.backend.entities;

import com.gelox.backend.entities.tenant.TenantAware;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Recomendacion de compra generada para una distribuidora (RF57).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "recomendacion_compra")
public class RecomendacionCompra implements TenantAware {

    private static final ZoneId ZONA_BOGOTA = ZoneId.of("America/Bogota");

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    /** Nulo hasta que la recomendacion se convierte en pedido (RF56). */
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pedido_proveedor_id", unique = true)
    private PedidoProveedor pedidoProveedor;

    @Column(name = "presupuesto", nullable = false, precision = 15, scale = 2)
    private BigDecimal presupuesto;

    @Column(name = "costo_total", nullable = false, precision = 15, scale = 2)
    private BigDecimal costoTotal;

    @Column(name = "costo_aprobado", nullable = false, precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal costoAprobado = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false, length = 20)
    @Builder.Default
    private EstadoRecomendacion estado = EstadoRecomendacion.GENERADA;

    @Enumerated(EnumType.STRING)
    @Column(name = "origen", nullable = false, length = 10)
    private OrigenRecomendacion origen;

    @Column(name = "confianza_baja", nullable = false)
    @Builder.Default
    private Boolean confianzaBaja = false;

    @Column(name = "dias_historial", nullable = false)
    private Integer diasHistorial;

    @Column(name = "dias_con_ventas", nullable = false)
    private Integer diasConVentas;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "recomendacion", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<ItemRecomendacion> items = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now(ZONA_BOGOTA);
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now(ZONA_BOGOTA);
    }
}
