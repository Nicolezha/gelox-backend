package com.gelox.backend.rf71;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gelox.backend.TestHelper;
import com.gelox.backend.auth.FirebaseAuthFilter;
import com.gelox.backend.entities.EstadoDistribuidora;
import com.gelox.backend.entities.RolUsuario;
import com.gelox.backend.entities.Usuario;
import com.gelox.backend.gobierno.Auditoria;
import com.gelox.backend.gobierno.AuditoriaEscritor;
import com.gelox.backend.gobierno.AuditoriaService;
import com.gelox.backend.repositories.UsuarioRepository;
import com.gelox.backend.tenant.DistribuidoraEstadoCache;
import com.gelox.backend.tenant.TenantContext;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseToken;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * RF71 — AuditoriaService.registrar para eventos que no son un método (FP-15 §2.16.3):
 * ACCESO_BLOQUEADO y TOKEN_INVALIDO en el filtro de autenticación; transacción nueva.
 */
@ExtendWith(MockitoExtension.class)
class AuditoriaServiceTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final UUID TENANT = UUID.randomUUID();

    @Mock
    AuditoriaEscritor escritor;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        TenantContext.clear();
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    @DisplayName("La fila se guarda en una transacción nueva (REQUIRES_NEW)")
    void guardaEnTransaccionNueva() throws Exception {
        Transactional tx = AuditoriaEscritor.class.getMethod("guardar", Auditoria.class)
                .getAnnotation(Transactional.class);

        assertThat(tx).isNotNull();
        assertThat(tx.propagation()).isEqualTo(Propagation.REQUIRES_NEW);
    }

    @Test
    @DisplayName("registrar(accion, entidad, detalle) toma IP, usuario y tenant de la petición; texto → {\"mensaje\"}")
    void registrarConContextoActual() throws Exception {
        Usuario gerente = TestHelper.buildUsuario("uid-g", "g@b.com", RolUsuario.ADMINISTRADOR, true);
        gerente.setTenantId(TENANT);
        SecurityContextHolder.getContext().setAuthentication(TestHelper.authParaUsuario(gerente));
        TenantContext.set(TENANT);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("190.1.2.3");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        new AuditoriaService(escritor).registrar("EXPORTAR_REPORTE", "Reporte", "ventas de noviembre");

        ArgumentCaptor<Auditoria> captor = ArgumentCaptor.forClass(Auditoria.class);
        verify(escritor).guardar(captor.capture());
        Auditoria fila = captor.getValue();
        assertThat(fila.getIp()).isEqualTo("190.1.2.3");
        assertThat(fila.getUsuarioId()).isEqualTo(gerente.getId());
        assertThat(fila.getTenantId()).isEqualTo(TENANT);
        assertThat(JSON.readTree(fila.getDetalle()).get("mensaje").asText()).isEqualTo("ventas de noviembre");
    }

    @Test
    @DisplayName("Filtro: distribuidora SUSPENDIDA registra ACCESO_BLOQUEADO con tenant y usuario")
    void filtroRegistraAccesoBloqueado() throws Exception {
        FirebaseAuth firebaseAuth = mock(FirebaseAuth.class);
        UsuarioRepository usuarioRepository = mock(UsuarioRepository.class);
        DistribuidoraEstadoCache cache = mock(DistribuidoraEstadoCache.class);
        AuditoriaService auditoria = mock(AuditoriaService.class);
        FirebaseToken token = mock(FirebaseToken.class);

        Usuario vendedor = TestHelper.buildUsuario("uid-v", "v@b.com", RolUsuario.ENCARGADO_VENTAS, true);
        vendedor.setTenantId(TENANT);
        when(firebaseAuth.verifyIdToken("tk")).thenReturn(token);
        when(token.getUid()).thenReturn("uid-v");
        when(token.getClaims()).thenReturn(Map.of("tenant_id", TENANT.toString()));
        when(usuarioRepository.findByFirebaseUid("uid-v")).thenReturn(Optional.of(vendedor));
        when(cache.estado(TENANT)).thenReturn(new DistribuidoraEstadoCache.Estado(EstadoDistribuidora.SUSPENDIDO, "B"));

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/productos");
        request.addHeader("Authorization", "Bearer tk");
        MockHttpServletResponse response = new MockHttpServletResponse();

        new FirebaseAuthFilter(firebaseAuth, usuarioRepository, cache, auditoria)
                .doFilter(request, response, mock(FilterChain.class));

        assertThat(response.getStatus()).isEqualTo(403);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> detalle = ArgumentCaptor.forClass(Map.class);
        verify(auditoria).registrar(eq("ACCESO_BLOQUEADO"), eq("Distribuidora"), eq(TENANT), detalle.capture(),
                eq(TENANT), eq(vendedor));
        assertThat(detalle.getValue()).containsEntry("estado", "SUSPENDIDO").containsEntry("ruta", "/api/productos");
    }

    @Test
    @DisplayName("Filtro: token inválido registra TOKEN_INVALIDO sin usuario ni tenant")
    void filtroRegistraTokenInvalido() throws Exception {
        FirebaseAuth firebaseAuth = mock(FirebaseAuth.class);
        AuditoriaService auditoria = mock(AuditoriaService.class);
        when(firebaseAuth.verifyIdToken("malo")).thenThrow(mock(FirebaseAuthException.class));

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/usuarios");
        request.addHeader("Authorization", "Bearer malo");
        MockHttpServletResponse response = new MockHttpServletResponse();

        new FirebaseAuthFilter(firebaseAuth, mock(UsuarioRepository.class), mock(DistribuidoraEstadoCache.class), auditoria)
                .doFilter(request, response, mock(FilterChain.class));

        assertThat(response.getStatus()).isEqualTo(401);
        verify(auditoria).registrar(eq("TOKEN_INVALIDO"), isNull(), isNull(), any(), isNull(), isNull());
    }

    @Test
    @DisplayName("Un fallo del escritor no se propaga")
    void falloDelEscritorNoSePropaga() {
        org.mockito.Mockito.doThrow(new RuntimeException("BD caída")).when(escritor).guardar(any());

        new AuditoriaService(escritor).registrar("ACCESO_BLOQUEADO", "Distribuidora", UUID.randomUUID(),
                Map.of("estado", "SUSPENDIDO"), TENANT, null);

        verify(escritor).guardar(any());
    }

    @Test
    @DisplayName("Sin X-Forwarded-For la IP es request.getRemoteAddr()")
    void ipSinForwarded() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.9");
        assertThat(AuditoriaService.ipCliente(request)).isEqualTo("127.0.0.9");

        request.addHeader("X-Forwarded-For", " 200.1.1.1 , 10.0.0.1");
        assertThat(AuditoriaService.ipCliente(request)).isEqualTo("200.1.1.1");
    }
}
