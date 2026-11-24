package com.gelox.backend.rf71;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gelox.backend.TestHelper;
import com.gelox.backend.entities.RolUsuario;
import com.gelox.backend.entities.Usuario;
import com.gelox.backend.gobierno.Auditable;
import com.gelox.backend.gobierno.Auditoria;
import com.gelox.backend.gobierno.AuditoriaAspect;
import com.gelox.backend.gobierno.AuditoriaEscritor;
import com.gelox.backend.gobierno.AuditoriaService;
import com.gelox.backend.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * RF71 — El aspecto guarda IP, usuario, rol y distribuidora de cada operación
 * {@link Auditable} que termina sin excepción (CP-71-01) y no deja fila si falla (CP-71-03).
 */
@ExtendWith(MockitoExtension.class)
class AuditoriaAspectTest {

    private static final UUID TENANT = UUID.randomUUID();
    private static final UUID ID_CREADO = UUID.randomUUID();
    private static final ObjectMapper JSON = new ObjectMapper();

    public record Creado(UUID id) {
    }

    /** Servicio de prueba con métodos auditados. */
    static class OperacionCritica {
        @Auditable(accion = "USUARIO_CREAR", entidad = "Usuario")
        public Creado crear() {
            return new Creado(ID_CREADO);
        }

        @Auditable(accion = "USUARIO_EDITAR", entidad = "Usuario")
        public ResponseEntity<String> editar(String nombre, UUID id) {
            return ResponseEntity.ok(nombre);
        }

        @Auditable(accion = "USUARIO_CREAR", entidad = "Usuario")
        public void fallar() {
            throw new IllegalStateException("Cuota del plan alcanzada");
        }

        @Auditable(accion = "SESION_CERRAR")
        public void cerrarSesion() {
            SecurityContextHolder.clearContext();
            TenantContext.clear();
        }

        public void sinAuditar() {
        }
    }

    @Mock
    AuditoriaEscritor escritor;

    private OperacionCritica proxy;
    private Usuario gerente;
    private MockHttpServletRequest request;

    @BeforeEach
    void setUp() {
        AspectJProxyFactory factory = new AspectJProxyFactory(new OperacionCritica());
        factory.setProxyTargetClass(true);
        factory.addAspect(new AuditoriaAspect(new AuditoriaService(escritor)));
        proxy = factory.getProxy();

        gerente = TestHelper.buildUsuario("uid-gerente", "gerente@b.com", RolUsuario.ADMINISTRADOR, true);
        gerente.setTenantId(TENANT);
        SecurityContextHolder.getContext().setAuthentication(TestHelper.authParaUsuario(gerente));
        TenantContext.set(TENANT);

        request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.5");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        TenantContext.clear();
        RequestContextHolder.resetRequestAttributes();
    }

    private Auditoria filaGuardada() {
        ArgumentCaptor<Auditoria> captor = ArgumentCaptor.forClass(Auditoria.class);
        verify(escritor).guardar(captor.capture());
        return captor.getValue();
    }

    @Test
    @DisplayName("CP-71-01: guarda IP, usuario, rol, tenant, acción, entidad, entidad_id y detalle JSON")
    void guardaIpUsuarioYTenant() throws Exception {
        Creado resultado = proxy.crear();

        assertThat(resultado.id()).isEqualTo(ID_CREADO);
        Auditoria fila = filaGuardada();
        assertThat(fila.getIp()).isEqualTo("10.0.0.5");
        assertThat(fila.getUsuarioId()).isEqualTo(gerente.getId());
        assertThat(fila.getRol()).isEqualTo("ADMINISTRADOR");
        assertThat(fila.getTenantId()).isEqualTo(TENANT);
        assertThat(fila.getAccion()).isEqualTo("USUARIO_CREAR");
        assertThat(fila.getEntidad()).isEqualTo("Usuario");
        assertThat(fila.getEntidadId()).isEqualTo(ID_CREADO);
        assertThat(JSON.readTree(fila.getDetalle()).get("metodo").asText()).isEqualTo("OperacionCritica.crear");
    }

    @Test
    @DisplayName("entidad_id es el primer argumento UUID del método")
    void entidadIdDesdeArgumento() {
        UUID id = UUID.randomUUID();

        proxy.editar("Nuevo nombre", id);

        assertThat(filaGuardada().getEntidadId()).isEqualTo(id);
    }

    @Test
    @DisplayName("IP: primera dirección de X-Forwarded-For (proxy de Render)")
    void ipDesdeXForwardedFor() {
        request.addHeader("X-Forwarded-For", "181.50.1.20, 10.0.0.1");

        proxy.crear();

        assertThat(filaGuardada().getIp()).isEqualTo("181.50.1.20");
    }

    @Test
    @DisplayName("Superadministrador: sin TenantContext ni tenant propio, tenant_id queda null")
    void superadministradorSinTenant() {
        TenantContext.clear();
        Usuario superadmin = TestHelper.buildUsuario("uid-super", "super@gelox.com", RolUsuario.SUPERADMINISTRADOR, true);
        SecurityContextHolder.getContext().setAuthentication(TestHelper.authParaUsuario(superadmin));

        proxy.crear();

        Auditoria fila = filaGuardada();
        assertThat(fila.getTenantId()).isNull();
        assertThat(fila.getRol()).isEqualTo("SUPERADMINISTRADOR");
    }

    @Test
    @DisplayName("El contexto se toma antes del método: cerrar sesión conserva usuario y tenant")
    void contextoAntesDelMetodo() {
        proxy.cerrarSesion();

        Auditoria fila = filaGuardada();
        assertThat(fila.getUsuarioId()).isEqualTo(gerente.getId());
        assertThat(fila.getTenantId()).isEqualTo(TENANT);
        assertThat(fila.getEntidad()).isNull();
    }

    @Test
    @DisplayName("CP-71-03: si el método lanza excepción no queda fila")
    void operacionFallidaNoSeAudita() {
        assertThatThrownBy(() -> proxy.fallar()).isInstanceOf(IllegalStateException.class);

        verify(escritor, never()).guardar(any());
    }

    @Test
    @DisplayName("Un fallo al guardar la auditoría no rompe la operación")
    void falloDeAuditoriaNoRompeOperacion() {
        doThrow(new RuntimeException("BD caída")).when(escritor).guardar(any());

        assertThat(proxy.crear().id()).isEqualTo(ID_CREADO);
    }

    @Test
    @DisplayName("Métodos sin @Auditable no se auditan")
    void metodoSinAnotacion() {
        proxy.sinAuditar();

        verify(escritor, never()).guardar(any());
    }
}
