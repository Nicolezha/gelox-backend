/**
 * Entidades JPA. Aquí se define el filtro por distribuidora que usan las entidades
 * TenantAware (@Filter) y que TenantFilterAspect activa en cada petición.
 */
@FilterDef(name = "tenantFilter", parameters = @ParamDef(name = "tenantId", type = UUID.class))
package com.gelox.backend.entities;

import org.hibernate.annotations.FilterDef;
import org.hibernate.annotations.ParamDef;

import java.util.UUID;
