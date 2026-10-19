package com.gelox.backend.entities.tenant;

import java.util.UUID;

/**
 * Entidad que pertenece a una distribuidora (tenant).
 */
public interface TenantAware {

    UUID getTenantId();

    void setTenantId(UUID tenantId);
}
