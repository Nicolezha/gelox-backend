package com.gelox.backend.tenant;

import com.gelox.backend.entities.tenant.TenantAware;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

/**
 * Contenedor del tenant (distribuidora) de la petición en curso.
 * Lo llena FirebaseAuthFilter y se limpia al terminar la petición.
 */
public final class TenantContext {

    private static final ThreadLocal<UUID> TENANT_ACTUAL = new ThreadLocal<>();

    private TenantContext() {
    }

    public static void set(UUID tenantId) {
        TENANT_ACTUAL.set(tenantId);
    }

    public static UUID get() {
        return TENANT_ACTUAL.get();
    }

    public static void clear() {
        TENANT_ACTUAL.remove();
    }

    /** Devuelve el tenant actual o lanza 403 si la petición no tiene distribuidora. */
    public static UUID requerido() {
        UUID tenantId = TENANT_ACTUAL.get();
        if (tenantId == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Sin distribuidora");
        }
        return tenantId;
    }

    /**
     * Indica si la entidad es del tenant actual. findById no pasa por el filtro de Hibernate,
     * así que los servicios lo usan con Optional.filter para responder "no encontrado".
     */
    public static boolean pertenece(TenantAware entidad) {
        return requerido().equals(entidad.getTenantId());
    }
}
