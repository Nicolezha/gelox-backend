package com.gelox.backend.entities;

import com.gelox.backend.entities.tenant.TenantAware;
import com.gelox.backend.tenant.TenantEntityListener;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Filter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/** RF63 — proveedor de la distribuidora (tabla proveedor, V10). */
@Getter
@Setter
@NoArgsConstructor
@Entity
@EntityListeners(TenantEntityListener.class)
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
@Table(name = "proveedor")
public class Proveedor implements TenantAware {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id")
    private UUID tenantId;

    @Column(nullable = false, length = 120)
    private String nombre;

    @Column(length = 20)
    private String nit;

    @Column(name = "contacto_nombre", length = 120)
    private String contactoNombre;

    @Column(length = 20)
    private String telefono;

    @Column(length = 150)
    private String correo;

    @Column(name = "tiempo_entrega_dias", nullable = false)
    private Integer tiempoEntregaDias = 0;

    @Column(name = "dias_habiles", nullable = false)
    private Boolean diasHabiles = true;

    @Column(name = "monto_minimo_pedido", nullable = false, precision = 15, scale = 2)
    private BigDecimal montoMinimoPedido = BigDecimal.ZERO;

    @Column(nullable = false)
    private Boolean activo = true;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    void antesDeInsertar() {
        createdAt = LocalDateTime.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void antesDeActualizar() {
        updatedAt = LocalDateTime.now();
    }
}
