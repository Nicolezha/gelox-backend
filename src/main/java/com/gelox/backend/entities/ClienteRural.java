package com.gelox.backend.entities;

import com.gelox.backend.entities.tenant.TenantAware;
import com.gelox.backend.tenant.TenantEntityListener;
import jakarta.persistence.*;
import org.hibernate.annotations.Filter;
import lombok.*;

import java.util.UUID;

/**
 * RF33 — Cliente rural recurrente.
 * No tiene columna 'correo' en BD (el RF lo menciona pero no existe).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@EntityListeners(TenantEntityListener.class)
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
@Table(name = "cliente_rural")
public class ClienteRural implements TenantAware {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(name = "tenant_id")
    private UUID tenantId;

    @Column(nullable = false)
    private String nombre;

    @Column
    private String telefono;

    @Column(columnDefinition = "TEXT")
    private String direccion;

    @Column
    private String corregimiento;

    @Column
    private String correo;

    /** true cuando se registra explícitamente como cliente recurrente. */
    @Column(nullable = false)
    @Builder.Default
    private Boolean recurrente = false;
}
