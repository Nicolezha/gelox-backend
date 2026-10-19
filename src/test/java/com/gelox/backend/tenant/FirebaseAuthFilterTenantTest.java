package com.gelox.backend.tenant;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gelox.backend.TestHelper;
import com.gelox.backend.auth.FirebaseAuthFilter;
import com.gelox.backend.entities.EstadoDistribuidora;
import com.gelox.backend.entities.RolUsuario;
import com.gelox.backend.entities.Usuario;
import com.gelox.backend.repositories.UsuarioRepository;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseToken;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * T111 — FirebaseAuthFilter resuelve el tenant y bloquea distribuidoras suspendidas o inactivas.
 */
@ExtendWith(MockitoExtension.class)
class FirebaseAuthFilterTenantTest {

    private static final String TOKEN = "token-valido";
    private static final String UID = "uid-tenant-test";
    private static final UUID TENANT = UUID.randomUUID();
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Mock
    FirebaseAuth firebaseAuth;

    @Mock
    UsuarioRepository usuarioRepository;

    @Mock
    DistribuidoraEstadoCache distribuidoraEstadoCache;

    @Mock
    FirebaseToken decoded;

    @Mock
    FilterChain cadena;

    FirebaseAuthFilter filtro;
    MockHttpServletRequest request;
    MockHttpServletResponse response;

    @BeforeEach
    void setUp() throws Exception {
        filtro = new FirebaseAuthFilter(firebaseAuth, usuarioRepository, distribuidoraEstadoCache);
        ReflectionTestUtils.setField(filtro, "contactoSoporte", "soporte@gelox.test");

        request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + TOKEN);
        response = new MockHttpServletResponse();

        when(firebaseAuth.verifyIdToken(TOKEN)).thenReturn(decoded);
        when(decoded.getUid()).thenReturn(UID);
    }

    @AfterEach
    void limpiar() {
        SecurityContextHolder.clearContext();
        TenantContext.clear();
    }

    private Usuario usuario(RolUsuario rol, UUID tenantId) {
        Usuario u = TestHelper.buildUsuario(UID, "tenant@gelox-test.com", rol, true);
        u.setTenantId(tenantId);
        when(usuarioRepository.findByFirebaseUid(UID)).thenReturn(Optional.of(u));
        return u;
    }

    @Test
    @DisplayName("distribuidora SUSPENDIDA → 403 JSON con código DISTRIBUIDORA_SUSPENDIDA")
    void distribuidoraSuspendida_403ConCodigo() throws Exception {
        usuario(RolUsuario.ENCARGADO_VENTAS, TENANT);
        when(decoded.getClaims()).thenReturn(Map.of("tenant_id", TENANT.toString()));
        when(distribuidoraEstadoCache.obtenerEstado(TENANT)).thenReturn(EstadoDistribuidora.SUSPENDIDO);

        filtro.doFilter(request, response, cadena);

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentType()).startsWith("application/json");
        JsonNode cuerpo = MAPPER.readTree(response.getContentAsString());
        assertThat(cuerpo.get("codigo").asText()).isEqualTo("DISTRIBUIDORA_SUSPENDIDA");
        assertThat(cuerpo.get("error").asText()).isNotBlank();
        assertThat(cuerpo.get("contacto").asText()).isEqualTo("soporte@gelox.test");
        verify(cadena, never()).doFilter(any(), any());
    }

    @Test
    @DisplayName("distribuidora INACTIVA → 403 JSON con código DISTRIBUIDORA_INACTIVA")
    void distribuidoraInactiva_403ConCodigo() throws Exception {
        usuario(RolUsuario.ADMINISTRADOR, TENANT);
        when(decoded.getClaims()).thenReturn(Map.of());
        when(distribuidoraEstadoCache.obtenerEstado(TENANT)).thenReturn(EstadoDistribuidora.INACTIVO);

        filtro.doFilter(request, response, cadena);

        assertThat(response.getStatus()).isEqualTo(403);
        JsonNode cuerpo = MAPPER.readTree(response.getContentAsString());
        assertThat(cuerpo.get("codigo").asText()).isEqualTo("DISTRIBUIDORA_INACTIVA");
        verify(cadena, never()).doFilter(any(), any());
    }

    @Test
    @DisplayName("distribuidora ACTIVA → sigue la cadena con el tenant en el contexto y lo limpia al final")
    void distribuidoraActiva_pasaConTenant() throws Exception {
        usuario(RolUsuario.ENCARGADO_INVENTARIO, TENANT);
        when(decoded.getClaims()).thenReturn(Map.of("tenant_id", TENANT.toString()));
        when(distribuidoraEstadoCache.obtenerEstado(TENANT)).thenReturn(EstadoDistribuidora.ACTIVO);
        AtomicReference<UUID> tenantDuranteLaPeticion = new AtomicReference<>();
        doAnswer(inv -> {
            tenantDuranteLaPeticion.set(TenantContext.get());
            return null;
        }).when(cadena).doFilter(any(), any());

        filtro.doFilter(request, response, cadena);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(tenantDuranteLaPeticion.get()).isEqualTo(TENANT);
        assertThat(TenantContext.get()).isNull();
    }

    @Test
    @DisplayName("sin claim tenant_id usa la distribuidora guardada en la base")
    void sinClaim_usaElTenantDeLaBase() throws Exception {
        usuario(RolUsuario.ENCARGADO_VENTAS, TENANT);
        when(decoded.getClaims()).thenReturn(Map.of("rol", "ENCARGADO_VENTAS"));
        when(distribuidoraEstadoCache.obtenerEstado(TENANT)).thenReturn(EstadoDistribuidora.PRUEBA);
        AtomicReference<UUID> tenantDuranteLaPeticion = new AtomicReference<>();
        doAnswer(inv -> {
            tenantDuranteLaPeticion.set(TenantContext.get());
            return null;
        }).when(cadena).doFilter(any(), any());

        filtro.doFilter(request, response, cadena);

        assertThat(tenantDuranteLaPeticion.get()).isEqualTo(TENANT);
    }

    @Test
    @DisplayName("claim tenant_id distinto al de la base → 401 sin seguir la cadena")
    void claimDistintoALaBase_401() throws Exception {
        usuario(RolUsuario.ENCARGADO_VENTAS, TENANT);
        when(decoded.getClaims()).thenReturn(Map.of("tenant_id", UUID.randomUUID().toString()));

        filtro.doFilter(request, response, cadena);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(MAPPER.readTree(response.getContentAsString()).get("error").asText()).isNotBlank();
        verify(cadena, never()).doFilter(any(), any());
        verifyNoInteractions(distribuidoraEstadoCache);
    }

    @Test
    @DisplayName("SUPERADMINISTRADOR → pasa sin tenant y sin consultar el estado de distribuidora")
    void superadministrador_pasaSinTenant() throws Exception {
        usuario(RolUsuario.SUPERADMINISTRADOR, null);
        AtomicReference<UUID> tenantDuranteLaPeticion = new AtomicReference<>(TENANT);
        doAnswer(inv -> {
            tenantDuranteLaPeticion.set(TenantContext.get());
            return null;
        }).when(cadena).doFilter(any(), any());

        filtro.doFilter(request, response, cadena);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(tenantDuranteLaPeticion.get()).isNull();
        verifyNoInteractions(distribuidoraEstadoCache);
    }
}
