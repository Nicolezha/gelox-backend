package com.gelox.backend.gobierno;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gelox.backend.entities.Usuario;
import com.gelox.backend.tenant.TenantContext;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Map;
import java.util.UUID;

/**
 * RF71 — Registro en {@code auditoria}.
 * <ul>
 *   <li>{@link Auditable} + {@link AuditoriaAspect} para operaciones que son un método.</li>
 *   <li>{@link #registrar(String, String, Object)} para eventos que no lo son
 *       (ACCESO_BLOQUEADO, TOKEN_INVALIDO en el filtro de autenticación).</li>
 * </ul>
 * Cada fila se guarda en una transacción nueva. Un fallo al guardarla se deja en el log
 * y nunca rompe la operación ni la petición.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditoriaService {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final AuditoriaEscritor escritor;

    /** Quién, desde dónde y en qué distribuidora: se toma antes de ejecutar la operación. */
    public record Contexto(String ip, Usuario usuario, UUID tenantId) {
    }

    /** Evento con el contexto de la petición actual (IP, usuario autenticado y TenantContext). */
    public void registrar(String accion, String entidad, Object detalle) {
        registrar(accion, entidad, null, detalle, contextoActual());
    }

    /**
     * Evento con el contexto explícito: en el filtro de autenticación el usuario aún no está
     * en el SecurityContext ni el tenant en TenantContext.
     */
    public void registrar(String accion, String entidad, UUID entidadId, Object detalle,
                          UUID tenantId, Usuario usuario) {
        registrar(accion, entidad, entidadId, detalle, new Contexto(ipCliente(), usuario, tenantId));
    }

    public void registrar(String accion, String entidad, UUID entidadId, Object detalle, Contexto ctx) {
        try {
            Usuario usuario = ctx.usuario();
            escritor.guardar(Auditoria.builder()
                    .ip(ctx.ip())
                    .tenantId(ctx.tenantId())
                    .usuarioId(usuario != null ? usuario.getId() : null)
                    .rol(usuario != null && usuario.getRol() != null ? usuario.getRol().name() : null)
                    .accion(accion)
                    .entidad(entidad == null || entidad.isBlank() ? null : entidad)
                    .entidadId(entidadId)
                    .detalle(aJson(detalle))
                    .build());
        } catch (Exception e) {
            log.error("No se pudo registrar la auditoría de {}: {}", accion, e.getMessage());
        }
    }

    /** IP, usuario autenticado y tenant de la petición en curso. */
    public static Contexto contextoActual() {
        Usuario usuario = usuarioAutenticado();
        UUID tenantId = TenantContext.get();
        if (tenantId == null && usuario != null) tenantId = usuario.getTenantId();
        return new Contexto(ipCliente(), usuario, tenantId);
    }

    /** FP-15 §2.16.3 — primera dirección de X-Forwarded-For o, si no hay, request.getRemoteAddr(). */
    public static String ipCliente() {
        RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
        if (!(attrs instanceof ServletRequestAttributes servletAttrs)) return null;
        return ipCliente(servletAttrs.getRequest());
    }

    public static String ipCliente(HttpServletRequest request) {
        if (request == null) return null;
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            String primera = forwarded.split(",")[0].trim();
            if (!primera.isEmpty()) return primera;
        }
        return request.getRemoteAddr();
    }

    private static Usuario usuarioAutenticado() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof Usuario usuario) {
            return usuario;
        }
        return null;
    }

    /** El detalle se guarda como JSON en texto; un String suelto va como {"mensaje": ...}. */
    static String aJson(Object detalle) {
        if (detalle == null) return null;
        Object valor = detalle instanceof String texto ? Map.of("mensaje", texto) : detalle;
        try {
            return JSON.writeValueAsString(valor);
        } catch (JsonProcessingException e) {
            return null;
        }
    }
}
