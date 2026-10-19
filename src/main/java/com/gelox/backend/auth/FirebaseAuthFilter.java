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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
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

    // Contacto de soporte de GELOX que se muestra a una distribuidora suspendida o inactiva
    @Value("${gelox.soporte.contacto:}")
    private String contactoSoporte;

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
                response.sendError(HttpServletResponse.SC_FORBIDDEN, "Usuario inactivo");
                return;
            }

            // El SUPERADMINISTRADOR es de la plataforma: no trabaja dentro de una distribuidora
            if (usuario.getRol() != RolUsuario.SUPERADMINISTRADOR) {
                tenantId = usuario.getTenantId();
                if (tenantId == null) {
                    escribirError(response, HttpServletResponse.SC_FORBIDDEN, Map.of("error", "Sin distribuidora"));
                    return;
                }

                Object claimTenant = decoded.getClaims().get("tenant_id");
                if (claimTenant != null && !tenantId.toString().equalsIgnoreCase(claimTenant.toString())) {
                    log.warn("Claim tenant_id {} no coincide con la distribuidora del usuario {}", claimTenant, firebaseUid);
                    escribirError(response, HttpServletResponse.SC_UNAUTHORIZED,
                            Map.of("error", "El token no corresponde a la distribuidora del usuario"));
                    return;
                }

                EstadoDistribuidora estado = distribuidoraEstadoCache.obtenerEstado(tenantId);
                if (estado == null) {
                    escribirError(response, HttpServletResponse.SC_FORBIDDEN, Map.of("error", "Sin distribuidora"));
                    return;
                }
                if (estado == EstadoDistribuidora.SUSPENDIDO || estado == EstadoDistribuidora.INACTIVO) {
                    boolean suspendida = estado == EstadoDistribuidora.SUSPENDIDO;
                    escribirError(response, HttpServletResponse.SC_FORBIDDEN, Map.of(
                            "error", suspendida ? "La distribuidora está suspendida" : "La distribuidora está inactiva",
                            "codigo", suspendida ? "DISTRIBUIDORA_SUSPENDIDA" : "DISTRIBUIDORA_INACTIVA",
                            "contacto", contactoSoporte != null ? contactoSoporte : ""));
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

    private void escribirError(HttpServletResponse response, int status, Map<String, String> cuerpo) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(OBJECT_MAPPER.writeValueAsString(cuerpo));
    }
}