package com.gelox.backend.rf58;

import com.gelox.backend.entities.Distribuidora;
import com.gelox.backend.entities.EstadoDistribuidora;
import com.gelox.backend.entities.RolUsuario;
import com.gelox.backend.entities.Usuario;
import com.gelox.backend.plataforma.DistribuidoraService;
import com.gelox.backend.plataforma.dto.CrearDistribuidoraRequest;
import com.gelox.backend.plataforma.dto.DistribuidoraDTO;
import com.gelox.backend.repositories.DistribuidoraRepository;
import com.gelox.backend.repositories.UsuarioRepository;
import com.gelox.backend.services.EmailService;
import com.gelox.backend.services.EventoSistemaService;
import com.gelox.backend.tenant.DistribuidoraEstadoCache;
import com.google.firebase.auth.AuthErrorCode;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.UserRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * RF58 — Aprovisionamiento de distribuidoras (T117-BE1). El Superadministrador
 * no tiene TenantContext: cada fila de la distribuidora nueva se persiste con
 * su tenantId asignado a mano.
 */
@ExtendWith(MockitoExtension.class)
class AprovisionamientoTest {

    @Mock
    DistribuidoraRepository distribuidoraRepository;

    @Mock
    UsuarioRepository usuarioRepository;

    @Mock
    EventoSistemaService eventoSistemaService;

    @Mock
    EmailService emailService;

    @Mock
    DistribuidoraEstadoCache distribuidoraEstadoCache;

    @InjectMocks
    DistribuidoraService distribuidoraService;

    private CrearDistribuidoraRequest requestValido() {
        return new CrearDistribuidoraRequest(
                "Helados Prueba S.A.S.", "900123456-7", "Helados Prueba", "helados-prueba",
                "Ayde Prueba", "gerente@heladosprueba.com", "3001234567", "Calle 1 #2-3", "BASICO");
    }

    private Distribuidora distribuidoraGuardada(UUID id) {
        return Distribuidora.builder()
                .id(id)
                .razonSocial("Helados Prueba S.A.S.")
                .nit("900123456-7")
                .nombreComercial("Helados Prueba")
                .subdominio("helados-prueba")
                .correoGerente("gerente@heladosprueba.com")
                .estado(EstadoDistribuidora.PRUEBA)
                .colorPrimario("#1F3864")
                .colorSecundario("#2E75B6")
                .createdAt(LocalDateTime.now())
                .build();
    }

    @Test
    @DisplayName("alta correcta: distribuidora PRUEBA, gerente creado y correoEnviado=true")
    void aprovisionar_altaCorrecta_quedaEnPruebaConGerente() throws Exception {
        UUID distribuidoraId = UUID.randomUUID();
        when(distribuidoraRepository.existsByNit("900123456-7")).thenReturn(false);
        when(distribuidoraRepository.existsBySubdominio("helados-prueba")).thenReturn(false);
        when(usuarioRepository.existsByCorreo("gerente@heladosprueba.com")).thenReturn(false);
        when(distribuidoraRepository.save(any(Distribuidora.class)))
                .thenReturn(distribuidoraGuardada(distribuidoraId));

        try (MockedStatic<FirebaseAuth> firebaseStatic = mockStatic(FirebaseAuth.class)) {
            FirebaseAuth firebaseAuth = mock(FirebaseAuth.class);
            firebaseStatic.when(FirebaseAuth::getInstance).thenReturn(firebaseAuth);

            FirebaseAuthException correoNoExiste = correoNoExiste();
            when(firebaseAuth.getUserByEmail("gerente@heladosprueba.com")).thenThrow(correoNoExiste);

            UserRecord nuevoUsuario = mock(UserRecord.class);
            when(nuevoUsuario.getUid()).thenReturn("uid-gerente-nuevo");
            when(firebaseAuth.createUser(any(UserRecord.CreateRequest.class))).thenReturn(nuevoUsuario);
            doNothing().when(firebaseAuth).setCustomUserClaims(anyString(), any());
            when(firebaseAuth.generatePasswordResetLink("gerente@heladosprueba.com"))
                    .thenReturn("https://gelox/reset?oob=abc");

            Usuario gerenteGuardado = Usuario.builder()
                    .id(UUID.randomUUID())
                    .firebaseUid("uid-gerente-nuevo")
                    .nombre("Ayde Prueba")
                    .correo("gerente@heladosprueba.com")
                    .rol(RolUsuario.ADMINISTRADOR)
                    .activo(true)
                    .tenantId(distribuidoraId)
                    .build();
            when(usuarioRepository.save(any(Usuario.class))).thenReturn(gerenteGuardado);

            DistribuidoraDTO resultado = distribuidoraService.aprovisionar(requestValido());

            assertThat(resultado.estado()).isEqualTo(EstadoDistribuidora.PRUEBA);
            assertThat(resultado.correoEnviado()).isTrue();
            verify(usuarioRepository).save(argThatTenantId(distribuidoraId));
            verify(emailService).enviarCorreoCuentaCreada(
                    "gerente@heladosprueba.com", "Ayde Prueba", "ADMINISTRADOR", "https://gelox/reset?oob=abc");
        }
    }

    @Test
    @DisplayName("NIT repetido → 409")
    void aprovisionar_nitRepetido_retorna409() {
        when(distribuidoraRepository.existsByNit("900123456-7")).thenReturn(true);

        assertThatThrownBy(() -> distribuidoraService.aprovisionar(requestValido()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("NIT");
        verify(distribuidoraRepository, never()).save(any());
    }

    @Test
    @DisplayName("subdominio repetido → 409")
    void aprovisionar_subdominioRepetido_retorna409() {
        when(distribuidoraRepository.existsByNit("900123456-7")).thenReturn(false);
        when(distribuidoraRepository.existsBySubdominio("helados-prueba")).thenReturn(true);

        assertThatThrownBy(() -> distribuidoraService.aprovisionar(requestValido()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("subdominio");
        verify(distribuidoraRepository, never()).save(any());
    }

    @Test
    @DisplayName("correo ya registrado en usuario → 409 sin tocar Firebase")
    void aprovisionar_correoRegistradoEnBD_retorna409() {
        when(distribuidoraRepository.existsByNit("900123456-7")).thenReturn(false);
        when(distribuidoraRepository.existsBySubdominio("helados-prueba")).thenReturn(false);
        when(usuarioRepository.existsByCorreo("gerente@heladosprueba.com")).thenReturn(true);

        assertThatThrownBy(() -> distribuidoraService.aprovisionar(requestValido()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("correo");
        verify(distribuidoraRepository, never()).save(any());
    }

    @Test
    @DisplayName("createUser falla en Firebase → rollback (no se guarda la distribuidora) y 502")
    void aprovisionar_firebaseCreateUserFalla_lanza502SinGuardar() throws Exception {
        when(distribuidoraRepository.existsByNit("900123456-7")).thenReturn(false);
        when(distribuidoraRepository.existsBySubdominio("helados-prueba")).thenReturn(false);
        when(usuarioRepository.existsByCorreo("gerente@heladosprueba.com")).thenReturn(false);
        when(distribuidoraRepository.save(any(Distribuidora.class)))
                .thenReturn(distribuidoraGuardada(UUID.randomUUID()));

        try (MockedStatic<FirebaseAuth> firebaseStatic = mockStatic(FirebaseAuth.class)) {
            FirebaseAuth firebaseAuth = mock(FirebaseAuth.class);
            firebaseStatic.when(FirebaseAuth::getInstance).thenReturn(firebaseAuth);
            FirebaseAuthException correoNoExiste = correoNoExiste();
            when(firebaseAuth.getUserByEmail("gerente@heladosprueba.com")).thenThrow(correoNoExiste);

            FirebaseAuthException fallo = mock(FirebaseAuthException.class);
            when(fallo.getAuthErrorCode()).thenReturn(AuthErrorCode.CERTIFICATE_FETCH_FAILED);
            try {
                when(firebaseAuth.createUser(any(UserRecord.CreateRequest.class))).thenThrow(fallo);
            } catch (FirebaseAuthException ignored) {
                // No se lanza aquí: es la configuración del mock.
            }

            assertThatThrownBy(() -> distribuidoraService.aprovisionar(requestValido()))
                    .isInstanceOf(ResponseStatusException.class)
                    .hasMessageContaining("Firebase");
            verify(usuarioRepository, never()).save(any());
        }
    }

    @Test
    @DisplayName("falla al insertar el usuario gerente → se revierte el usuario de Firebase (deleteUser)")
    void aprovisionar_insertUsuarioFalla_eliminaUsuarioFirebase() throws Exception {
        UUID distribuidoraId = UUID.randomUUID();
        when(distribuidoraRepository.existsByNit("900123456-7")).thenReturn(false);
        when(distribuidoraRepository.existsBySubdominio("helados-prueba")).thenReturn(false);
        when(usuarioRepository.existsByCorreo("gerente@heladosprueba.com")).thenReturn(false);
        when(distribuidoraRepository.save(any(Distribuidora.class)))
                .thenReturn(distribuidoraGuardada(distribuidoraId));

        try (MockedStatic<FirebaseAuth> firebaseStatic = mockStatic(FirebaseAuth.class)) {
            FirebaseAuth firebaseAuth = mock(FirebaseAuth.class);
            firebaseStatic.when(FirebaseAuth::getInstance).thenReturn(firebaseAuth);
            FirebaseAuthException correoNoExiste = correoNoExiste();
            when(firebaseAuth.getUserByEmail("gerente@heladosprueba.com")).thenThrow(correoNoExiste);

            UserRecord nuevoUsuario = mock(UserRecord.class);
            when(nuevoUsuario.getUid()).thenReturn("uid-gerente-nuevo");
            when(firebaseAuth.createUser(any(UserRecord.CreateRequest.class))).thenReturn(nuevoUsuario);
            doNothing().when(firebaseAuth).setCustomUserClaims(anyString(), any());
            when(usuarioRepository.save(any(Usuario.class))).thenThrow(new RuntimeException("fallo BD"));

            assertThatThrownBy(() -> distribuidoraService.aprovisionar(requestValido()))
                    .isInstanceOf(ResponseStatusException.class);

            verify(firebaseAuth).deleteUser("uid-gerente-nuevo");
        }
    }

    @Test
    @DisplayName("el correo de bienvenida falla: la distribuidora queda creada con correoEnviado=false")
    void aprovisionar_correoFalla_correoEnviadoFalse() throws Exception {
        UUID distribuidoraId = UUID.randomUUID();
        when(distribuidoraRepository.existsByNit("900123456-7")).thenReturn(false);
        when(distribuidoraRepository.existsBySubdominio("helados-prueba")).thenReturn(false);
        when(usuarioRepository.existsByCorreo("gerente@heladosprueba.com")).thenReturn(false);
        when(distribuidoraRepository.save(any(Distribuidora.class)))
                .thenReturn(distribuidoraGuardada(distribuidoraId));

        try (MockedStatic<FirebaseAuth> firebaseStatic = mockStatic(FirebaseAuth.class)) {
            FirebaseAuth firebaseAuth = mock(FirebaseAuth.class);
            firebaseStatic.when(FirebaseAuth::getInstance).thenReturn(firebaseAuth);
            FirebaseAuthException correoNoExiste = correoNoExiste();
            when(firebaseAuth.getUserByEmail("gerente@heladosprueba.com")).thenThrow(correoNoExiste);

            UserRecord nuevoUsuario = mock(UserRecord.class);
            when(nuevoUsuario.getUid()).thenReturn("uid-gerente-nuevo");
            when(firebaseAuth.createUser(any(UserRecord.CreateRequest.class))).thenReturn(nuevoUsuario);
            doNothing().when(firebaseAuth).setCustomUserClaims(anyString(), any());
            when(usuarioRepository.save(any(Usuario.class))).thenReturn(Usuario.builder()
                    .id(UUID.randomUUID()).firebaseUid("uid-gerente-nuevo").nombre("Ayde Prueba")
                    .correo("gerente@heladosprueba.com").rol(RolUsuario.ADMINISTRADOR).activo(true)
                    .tenantId(distribuidoraId).build());
            // enviarCorreoAcceso solo registra el mensaje: no necesita AuthErrorCode.
            when(firebaseAuth.generatePasswordResetLink(anyString()))
                    .thenThrow(mock(FirebaseAuthException.class));

            DistribuidoraDTO resultado = distribuidoraService.aprovisionar(requestValido());

            assertThat(resultado.correoEnviado()).isFalse();
            verify(distribuidoraRepository, times(2)).save(any(Distribuidora.class));
        }
    }

    private static Usuario argThatTenantId(UUID tenantId) {
        return org.mockito.ArgumentMatchers.argThat(u -> u != null && tenantId.equals(u.getTenantId()));
    }

    /** getUserByEmail lanza esto cuando el correo no existe en Firebase: el servicio lo trata como disponible. */
    private static FirebaseAuthException correoNoExiste() {
        FirebaseAuthException excepcion = mock(FirebaseAuthException.class);
        when(excepcion.getAuthErrorCode()).thenReturn(AuthErrorCode.USER_NOT_FOUND);
        return excepcion;
    }
}
