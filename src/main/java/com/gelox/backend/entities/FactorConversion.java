package com.gelox.backend.entities;

import com.gelox.backend.entities.tenant.TenantAware;
import com.gelox.backend.tenant.TenantEntityListener;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Filter;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * RF64 — 1 {@code unidadOrigen} del producto = {@code factor} {@code unidadDestino}
 * (la unidad base). CJ → UN sigue saliendo de producto.unidades_por_caja.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@EntityListeners(TenantEntityListener.class)
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
@Table(name = "factor_conversion")
public class FactorConversion implements TenantAware {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id")
    private UUID tenantId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "producto_id", nullable = false)
    private Producto producto;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "unidad_origen_id", nullable = false)
    private UnidadMedida unidadOrigen;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "unidad_destino_id", nullable = false)
    private UnidadMedida unidadDestino;

    @Column(nullable = false, precision = 12, scale = 4)
    private BigDecimal factor;
}
