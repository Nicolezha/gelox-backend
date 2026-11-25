package com.gelox.backend.entities;

import com.gelox.backend.entities.tenant.TenantAware;
import com.gelox.backend.tenant.TenantEntityListener;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Filter;

import java.util.UUID;

/** RF63 — código con el que un proveedor conoce a un producto (homologación). */
@Getter
@Setter
@NoArgsConstructor
@Entity
@EntityListeners(TenantEntityListener.class)
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
@Table(name = "codigo_homologacion")
public class CodigoHomologacion implements TenantAware {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id")
    private UUID tenantId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "producto_id", nullable = false)
    private Producto producto;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "proveedor_id", nullable = false)
    private Proveedor proveedor;

    @Column(nullable = false, length = 60)
    private String codigo;
}
