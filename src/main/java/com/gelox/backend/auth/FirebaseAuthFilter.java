package com.gelox.backend.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gelox.backend.entities.EstadoDistribuidora;
import com.gelox.backend.entities.RolUsuario;
import com.gelox.backend.entities.Usuario;
import com.gelox.backend.repositories.UsuarioRepository;
import com.gelox.backend.tenant.DistribuidoraEstadoCache;
import com.gelox.backend.tenant.TenantContext;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseToken;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class FirebaseAuthFilter extends OncePerRequestFilter {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final FirebaseAuth firebaseAuth;
    private final UsuarioRepository usuarioRepository;
    private final DistribuidoraEstadoCache distribuidoraEstadoCache;

    // Contacto de soporte de la plataforma (no de la distribuidora) para el 403 de distribuidora bloqueada
    @Value("${plataforma.soporte.correo:}")
    private String correoSoporte;

    @Value("${plataforma.soporte.whatsapp:}")
    private String whatsappSoporte;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = authHeader.substring(7);
        UUID tenantId = null;

        try {
            FirebaseToken decoded = firebaseAuth.verifyIdToken(token);
            String firebaseUid = decoded.getUid();

            Usuario usuario = usuarioRepository.findByFirebaseUid(firebaseUid)
                    .orElse(null);

            if (usuario == null) {
                log.warn("Token válido pero usuario no registrado en BD: {}", firebaseUid);
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Usuario no registrado");
                return;
            }

            if (!usuario.getActivo()) {
                escribirJson(response, HttpServletResponse.SC_FORBIDDEN,
                        Map.of("error", "Usuario inactivo", "codigo", "USUARIO_INACTIVO"));
                return;
            }

            // El SUPERADMINISTRADOR es de la plataforma: no trabaja dentro de una distribuidora
            if (usuario.getRol() != RolUsuario.SUPERADMINISTRADOR) {
                tenantId = resolverTenant(decoded, usuario);

                DistribuidoraEstadoCache.Estado estado = distribuidoraEstadoCache.estado(tenantId);
                if (estado == null) {
                    escribirJson(response, HttpServletResponse.SC_FORBIDDEN, Map.of("error", "Sin distribuidora"));
                    return;
                }
                if (estado.bloqueada()) {
                    responderBloqueo(response, estado);
                    return;
                }
            }

            List<SimpleGrantedAuthority> authorities = List.of(
                    new SimpleGrantedAuthority("ROLE_" + usuario.getRol().name())
            );

            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(usuario, null, authorities);

            SecurityContextHolder.getContext().setAuthentication(authentication);

        } catch (FirebaseAuthException e) {
            log.warn("Token Firebase inválido: {}", e.getMessage());
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Token inválido o expirado");
            return;
        } catch (ResponseStatusException e) {
            escribirJson(response, e.getStatusCode().value(), Map.of("error", e.getReason()));
            return;
        } catch (Exception e) {
            log.error("Error inesperado en FirebaseAuthFilter: {}", e.getMessage(), e);
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Error interno de autenticación");
            return;
        }

        try {
            if (tenantId != null) {
                TenantContext.set(tenantId);
            }
            filterChain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }

    /**
     * Distribuidora del usuario según el claim tenant_id, que debe coincidir con la base.
     * Los usuarios creados antes de la Iteración 2 no tienen el claim: se usa la base y se
     * reescriben los claims para la próxima sesión.
     */
    private UUID resolverTenant(FirebaseToken token, Usuario usuario) {
        UUID tenantBase = usuario.getTenantId();
        if (tenantBase == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Sin distribuidora");
        }

        Object claimTenant = token.getClaims().get("tenant_id");
        if (claimTenant == null) {
            try {
                firebaseAuth.setCustomUserClaims(usuario.getFirebaseUid(),
                        Map.of("rol", usuario.getRol().name(), "tenant_id", tenantBase.toString()));
            } catch (FirebaseAuthException e) {
                // No bloquea la petición: se vuelve a intentar en la siguiente
                log.warn("No se pudieron reescribir los claims de {}: {}", usuario.getFirebaseUid(), e.getMessage());
            }
            return tenantBase;
        }

        if (!tenantBase.toString().equalsIgnoreCase(claimTenant.toString())) {
            log.warn("Claim tenant_id {} no coincide con la distribuidora del usuario {}",
                    claimTenant, usuario.getFirebaseUid());
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Token inconsistente");
        }
        return tenantBase;
    }

    /** 403 de distribuidora SUSPENDIDO o INACTIVO; el frontend lo reconoce por el código. */
    private void responderBloqueo(HttpServletResponse response, DistribuidoraEstadoCache.Estado estado) throws IOException {
        boolean suspendida = estado.valor() == EstadoDistribuidora.SUSPENDIDO;

        Map<String, String> contacto = new LinkedHashMap<>();
        contacto.put("correo", correoSoporte != null ? correoSoporte : "");
        contacto.put("whatsapp", whatsappSoporte != null ? whatsappSoporte : "");

        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("error", suspendida ? "La distribuidora está suspendida" : "La distribuidora está inactiva");
        cuerpo.put("codigo", suspendida ? "DISTRIBUIDORA_SUSPENDIDA" : "DISTRIBUIDORA_INACTIVA");
        cuerpo.put("distribuidora", estado.nombreComercial());
        cuerpo.put("contacto", contacto);
        escribirJson(response, HttpServletResponse.SC_FORBIDDEN, cuerpo);
    }

    private void escribirJson(HttpServletResponse response, int status, Map<String, ?> cuerpo) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(OBJECT_MAPPER.writeValueAsString(cuerpo));
    }
}
