package com.gelox.backend.rf62;

import com.gelox.backend.dto.CrearUsuarioDTO;
import com.gelox.backend.dto.EditarUsuarioDTO;
import com.gelox.backend.dto.UsuarioResponseDTO;
import com.gelox.backend.entities.RolUsuario;
import com.gelox.backend.entities.Usuario;
import com.gelox.backend.repositories.UsuarioRepository;
import com.gelox.backend.services.EmailService;
import com.gelox.backend.services.EventoSistemaService;
import com.gelox.backend.services.UsuarioService;
import com.gelox.backend.tenant.TenantContext;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.UserRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * RF62 — Gestión de usuarios por distribuidora (T121).
 * El Gerente solo ve y toca usuarios de su distribuidora, solo asigna Ventas e Inventarios
 * y no puede deshabilitar ni cambiar de rol al gerente titular.
 */
@ExtendWith(MockitoExtension.class)
class UsuariosPorDistribuidoraTest {

    private static final UUID TENANT_A = UUID.randomUUID();
    private static final UUID TENANT_B = UUID.randomUUID();

    @Mock
    UsuarioRepository usuarioRepository;

    @Mock
    EventoSistemaService eventoSistemaService;

    @Mock
    EmailService emailService;

    @InjectMocks
    UsuarioService usuarioService;

    private Usuario titularA;
    private Usuario vendedorA;
    private Usuario vendedorB;

    @BeforeEach
    void setUp() {
        titularA = usuario("uid-titular-a", "titular@a.com", RolUsuario.ADMINISTRADOR, TENANT_A);
        vendedorA = usuario("uid-vendedor-a", "vendedor@a.com", RolUsuario.ENCARGADO_VENTAS, TENANT_A);
        vendedorB = usuario("uid-vendedor-b", "vendedor@b.com", RolUsuario.ENCARGADO_VENTAS, TENANT_B);
        // El Gerente que hace la petición es de la distribuidora A
        TenantContext.set(TENANT_A);
    }

    @AfterEach
    void limpiar() {
        TenantContext.clear();
    }

    private static Usuario usuario(String uid, String correo, RolUsuario rol, UUID tenant) {
        return Usuario.builder()
                .id(UUID.randomUUID())
                .firebaseUid(uid)
                .nombre("Usuario " + uid)
                .correo(correo)
                .rol(rol)
                .activo(true)
                .tenantId(tenant)
                .build();
    }

    private static CrearUsuarioDTO crearDto(String rol) {
        CrearUsuarioDTO dto = new CrearUsuarioDTO();
        dto.setNombre("Nuevo");
        dto.setCorreo("nuevo@a.com");
        dto.setContrasenaTemporal("Pass1234!");
        dto.setRol(rol);
        return dto;
    }

    private static EditarUsuarioDTO editarDto(String rol) {
        EditarUsuarioDTO dto = new EditarUsuarioDTO();
        dto.setNombre("Editado");
        dto.setCorreo("editado@a.com");
        dto.setRol(rol);
        return dto;
    }

    private static void assertNoEncontrado(Throwable t) {
        assertThat(t).isInstanceOf(ResponseStatusException.class);
        ResponseStatusException e = (ResponseStatusException) t;
        assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(e.getReason()).isEqualTo("Usuario no encontrado");
    }

    // ── Roles permitidos ────────────────────────────────────────────────────

    @ParameterizedTest
    @ValueSource(strings = {"ADMINISTRADOR", "SUPERADMINISTRADOR"})
    @DisplayName("crear con rol ADMINISTRADOR o SUPERADMINISTRADOR → 400 sin tocar Firebase")
    void crear_rolNoPermitido_400(String rol) {
        try (MockedStatic<FirebaseAuth> firebase = mockStatic(FirebaseAuth.class)) {
            assertThatThrownBy(() -> usuarioService.crearUsuario(crearDto(rol)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Solo se permiten los roles de Ventas e Inventarios");
            firebase.verifyNoInteractions();
        }
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    @DisplayName("editar asignando ADMINISTRADOR a un usuario del tenant → 400")
    void editar_aAdministrador_400() {
        when(usuarioRepository.findById(vendedorA.getId())).thenReturn(Optional.of(vendedorA));
        when(usuarioRepository.findGerenteTitularId(TENANT_A)).thenReturn(Optional.of(titularA.getId()));

        try (MockedStatic<FirebaseAuth> firebase = mockStatic(FirebaseAuth.class)) {
            assertThatThrownBy(() -> usuarioService.editarUsuario(vendedorA.getId(), editarDto("ADMINISTRADOR")))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Solo se permiten los roles de Ventas e Inventarios");
        }
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    @DisplayName("el usuario creado lleva los claims rol y tenant_id de la distribuidora del Gerente")
    void crear_llevaClaimTenantId() throws Exception {
        when(usuarioRepository.existsByCorreo("nuevo@a.com")).thenReturn(false);
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(inv -> inv.getArgument(0));

        try (MockedStatic<FirebaseAuth> firebase = mockStatic(FirebaseAuth.class)) {
            FirebaseAuth auth = mock(FirebaseAuth.class);
            firebase.when(FirebaseAuth::getInstance).thenReturn(auth);
            UserRecord record = mock(UserRecord.class);
            when(record.getUid()).thenReturn("uid-nuevo");
            when(auth.createUser(any(UserRecord.CreateRequest.class))).thenReturn(record);

            UsuarioResponseDTO dto = usuarioService.crearUsuario(crearDto("ENCARGADO_INVENTARIO"));

            verify(auth).setCustomUserClaims("uid-nuevo",
                    Map.of("rol", "ENCARGADO_INVENTARIO", "tenant_id", TENANT_A.toString()));
            verify(usuarioRepository).save(argThat(u -> TENANT_A.equals(u.getTenantId())));
            assertThat(dto.isEsGerenteTitular()).isFalse();
        }
    }

    @Test
    @DisplayName("crear con correo ya registrado → se mantiene el error de RF03")
    void crear_correoDuplicado_mantieneError() {
        when(usuarioRepository.existsByCorreo("nuevo@a.com")).thenReturn(true);

        try (MockedStatic<FirebaseAuth> firebase = mockStatic(FirebaseAuth.class)) {
            assertThatThrownBy(() -> usuarioService.crearUsuario(crearDto("ENCARGADO_VENTAS")))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Ya existe un usuario con ese correo.");
        }
    }

    // ── Alcance por distribuidora ───────────────────────────────────────────

    @Test
    @DisplayName("obtener usuario de otra distribuidora → 404 «Usuario no encontrado»")
    void obtener_otroTenant_404() {
        when(usuarioRepository.findById(vendedorB.getId())).thenReturn(Optional.of(vendedorB));

        Throwable t = catchThrowable(
                () -> usuarioService.obtenerUsuario(vendedorB.getId()));
        assertNoEncontrado(t);
    }

    @Test
    @DisplayName("editar usuario de otra distribuidora → 404 sin tocar Firebase ni la base")
    void editar_otroTenant_404() {
        when(usuarioRepository.findById(vendedorB.getId())).thenReturn(Optional.of(vendedorB));

        try (MockedStatic<FirebaseAuth> firebase = mockStatic(FirebaseAuth.class)) {
            Throwable t = catchThrowable(
                    () -> usuarioService.editarUsuario(vendedorB.getId(), editarDto("ENCARGADO_INVENTARIO")));
            assertNoEncontrado(t);
            firebase.verifyNoInteractions();
        }
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    @DisplayName("deshabilitar usuario de otra distribuidora → 404")
    void deshabilitar_otroTenant_404() {
        when(usuarioRepository.findById(vendedorB.getId())).thenReturn(Optional.of(vendedorB));

        try (MockedStatic<FirebaseAuth> firebase = mockStatic(FirebaseAuth.class)) {
            Throwable t = catchThrowable(
                    () -> usuarioService.deshabilitarUsuario(vendedorB.getId()));
            assertNoEncontrado(t);
            firebase.verifyNoInteractions();
        }
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    @DisplayName("habilitar usuario de otra distribuidora → 404")
    void habilitar_otroTenant_404() {
        when(usuarioRepository.findById(vendedorB.getId())).thenReturn(Optional.of(vendedorB));

        try (MockedStatic<FirebaseAuth> firebase = mockStatic(FirebaseAuth.class)) {
            Throwable t = catchThrowable(
                    () -> usuarioService.habilitarUsuario(vendedorB.getId()));
            assertNoEncontrado(t);
            firebase.verifyNoInteractions();
        }
    }

    @Test
    @DisplayName("listar solo trae usuarios del propio tenant y marca al gerente titular")
    void listar_soloPropioTenant() {
        // Aunque el repositorio devolviera un usuario de B, el servicio lo descarta
        when(usuarioRepository.findAll()).thenReturn(List.of(titularA, vendedorA, vendedorB));
        when(usuarioRepository.findGerenteTitularId(TENANT_A)).thenReturn(Optional.of(titularA.getId()));

        List<UsuarioResponseDTO> lista = usuarioService.listarUsuarios();

        assertThat(lista).extracting(UsuarioResponseDTO::getId)
                .containsExactly(titularA.getId(), vendedorA.getId());
        assertThat(lista).extracting(UsuarioResponseDTO::isEsGerenteTitular)
                .containsExactly(true, false);
    }

    // ── Gerente titular ─────────────────────────────────────────────────────

    @Test
    @DisplayName("deshabilitar al gerente titular → 400 sin tocar Firebase")
    void deshabilitar_titular_400() {
        when(usuarioRepository.findById(titularA.getId())).thenReturn(Optional.of(titularA));
        when(usuarioRepository.findGerenteTitularId(TENANT_A)).thenReturn(Optional.of(titularA.getId()));

        try (MockedStatic<FirebaseAuth> firebase = mockStatic(FirebaseAuth.class)) {
            assertThatThrownBy(() -> usuarioService.deshabilitarUsuario(titularA.getId()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("gerente titular");
            firebase.verifyNoInteractions();
        }
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    @DisplayName("cambiar el rol del gerente titular → 400")
    void editar_cambiarRolTitular_400() {
        when(usuarioRepository.findById(titularA.getId())).thenReturn(Optional.of(titularA));
        when(usuarioRepository.findGerenteTitularId(TENANT_A)).thenReturn(Optional.of(titularA.getId()));

        try (MockedStatic<FirebaseAuth> firebase = mockStatic(FirebaseAuth.class)) {
            assertThatThrownBy(() -> usuarioService.editarUsuario(titularA.getId(), editarDto("ENCARGADO_VENTAS")))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("rol del gerente titular");
            firebase.verifyNoInteractions();
        }
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    @DisplayName("editar nombre y correo del titular sin cambiar su rol → permitido")
    void editar_titularMismoRol_ok() throws Exception {
        when(usuarioRepository.findById(titularA.getId())).thenReturn(Optional.of(titularA));
        when(usuarioRepository.findGerenteTitularId(TENANT_A)).thenReturn(Optional.of(titularA.getId()));
        when(usuarioRepository.findByCorreo("editado@a.com")).thenReturn(Optional.empty());
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(inv -> inv.getArgument(0));

        try (MockedStatic<FirebaseAuth> firebase = mockStatic(FirebaseAuth.class)) {
            FirebaseAuth auth = mock(FirebaseAuth.class);
            firebase.when(FirebaseAuth::getInstance).thenReturn(auth);

            UsuarioResponseDTO dto = usuarioService.editarUsuario(titularA.getId(), editarDto("ADMINISTRADOR"));

            assertThat(dto.getNombre()).isEqualTo("Editado");
            assertThat(dto.getRol()).isEqualTo("ADMINISTRADOR");
            assertThat(dto.isEsGerenteTitular()).isTrue();
        }
    }

    @Test
    @DisplayName("obtener al titular devuelve esGerenteTitular=true")
    void obtener_titular_marcado() {
        when(usuarioRepository.findById(titularA.getId())).thenReturn(Optional.of(titularA));
        when(usuarioRepository.findGerenteTitularId(TENANT_A)).thenReturn(Optional.of(titularA.getId()));

        assertThat(usuarioService.obtenerUsuario(titularA.getId()).isEsGerenteTitular()).isTrue();
    }

    // ── T121-BE2: revocar sesión ─────────────────────────────────────────────

    @Test
    @DisplayName("deshabilitar revoca los refresh tokens después de deshabilitar en Firebase")
    void deshabilitar_revocaSesion() throws Exception {
        when(usuarioRepository.findById(vendedorA.getId())).thenReturn(Optional.of(vendedorA));
        when(usuarioRepository.findGerenteTitularId(TENANT_A)).thenReturn(Optional.of(titularA.getId()));
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(inv -> inv.getArgument(0));

        try (MockedStatic<FirebaseAuth> firebase = mockStatic(FirebaseAuth.class)) {
            FirebaseAuth auth = mock(FirebaseAuth.class);
            firebase.when(FirebaseAuth::getInstance).thenReturn(auth);

            usuarioService.deshabilitarUsuario(vendedorA.getId());

            var orden = inOrder(auth);
            orden.verify(auth).updateUser(any(UserRecord.UpdateRequest.class));
            orden.verify(auth).revokeRefreshTokens("uid-vendedor-a");
        }
        // Con activo=false, FirebaseAuthFilter responde 403 a la siguiente petición
        verify(usuarioRepository).save(argThat(u -> !u.getActivo()));
    }
}
