package com.gelox.backend.tenant;

import com.gelox.backend.entities.tenant.TenantAware;
import jakarta.persistence.PrePersist;

/**
 * Asigna el tenant de la petición a las entidades nuevas que no lo traen.
 */
public class TenantEntityListener {

    @PrePersist
    public void asignarTenant(Object entidad) {
        if (entidad instanceof TenantAware tenantAware && tenantAware.getTenantId() == null) {
            tenantAware.setTenantId(TenantContext.requerido());
        }
    }
}
