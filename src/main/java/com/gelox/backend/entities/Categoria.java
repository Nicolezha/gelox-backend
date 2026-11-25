package com.gelox.backend.entities;

import com.gelox.backend.entities.tenant.TenantAware;
import com.gelox.backend.tenant.TenantEntityListener;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Filter;

import java.util.UUID;

/** RF64 — categoría del catálogo por distribuidora (reemplaza al enum CategoriaProducto). */
@Getter
@Setter
@NoArgsConstructor
@Entity
@EntityListeners(TenantEntityListener.class)
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
@Table(name = "categoria")
public class Categoria implements TenantAware {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id")
    private UUID tenantId;

    @Column(nullable = false, length = 80)
    private String nombre;

    @Column(nullable = false)
    private Integer orden = 0;

    @Column(nullable = false)
    private Boolean activo = true;
}
