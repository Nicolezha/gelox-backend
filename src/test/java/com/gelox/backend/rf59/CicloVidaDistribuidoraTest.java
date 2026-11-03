package com.gelox.backend.rf59;

import com.gelox.backend.entities.Distribuidora;
import com.gelox.backend.entities.EstadoDistribuidora;
import com.gelox.backend.entities.RolUsuario;
import com.gelox.backend.entities.Usuario;
import com.gelox.backend.plataforma.DistribuidoraService;
import com.gelox.backend.plataforma.dto.CambiarEstadoRequest;
import com.gelox.backend.plataforma.dto.DistribuidoraDTO;
import com.gelox.backend.repositories.DistribuidoraRepository;
import com.gelox.backend.repositories.UsuarioRepository;
import com.gelox.backend.services.EmailService;
import com.gelox.backend.services.EventoSistemaService;
import com.gelox.backend.tenant.DistribuidoraEstadoCache;
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
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * RF59 — Ciclo de vida de distribuidoras (T117-BE2). Transiciones de estado,
 * motivo obligatorio, invalidación del caché, revocación de sesiones al
 * bloquear y reenvío del acceso del gerente titular.
 */
@ExtendWith(MockitoExtension.class)
class CicloVidaDistribuidoraTest {

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

    private Distribuidora distribuidora(UUID id, EstadoDistribuidora estado) {
        return Distribuidora.builder()
                .id(id)
                .razonSocial("Helados Prueba S.A.S.")
                .nit("900123456-7")
                .nombreComercial("Helados Prueba")
                .subdominio("helados-prueba")
                .correoGerente("gerente@heladosprueba.com")
                .estado(estado)
                .colorPrimario("#1F3864")
                .colorSecundario("#2E75B6")
                .fechaEstado(LocalDateTime.now())
                .build();
    }

    @Test
    @DisplayName("PRUEBA -> ACTIVO: cambia el estado y registra el evento")
    void cambiarEstado_transicionValida_actualizaEstado() {
        UUID id = UUID.randomUUID();
        Distribuidora d = distribuidora(id, EstadoDistribuidora.PRUEBA);
        when(distribuidoraRepository.findById(id)).thenReturn(Optional.of(d));
        when(distribuidoraRepository.save(any(Distribuidora.class))).thenAnswer(inv -> inv.getArgument(0));

        DistribuidoraDTO resultado = distribuidoraService.cambiarEstado(id,
                new CambiarEstadoRequest(EstadoDistribuidora.ACTIVO, "Pago verificado"));

        assertThat(resultado.estado()).isEqualTo(EstadoDistribuidora.ACTIVO);
        assertThat(resultado.motivoEstado()).isEqualTo("Pago verificado");
        verify(eventoSistemaService).registrarEvento(
                any(), any(), org.mockito.ArgumentMatchers.isNull());
    }

    @Test
    @DisplayName("INACTIVO -> ACTIVO: transición no válida (409), INACTIVO es final")
    void cambiarEstado_transicionInvalida_retorna409() {
        UUID id = UUID.randomUUID();
        when(distribuidoraRepository.findById(id)).thenReturn(Optional.of(distribuidora(id, EstadoDistribuidora.INACTIVO)));

        assertThatThrownBy(() -> distribuidoraService.cambiarEstado(id,
                new CambiarEstadoRequest(EstadoDistribuidora.ACTIVO, "Reactivar")))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Transición no válida");
        verify(distribuidoraRepository, never()).save(any());
    }

    @Test
    @DisplayName("motivo vacío (solo espacios) → 400 sin tocar la distribuidora")
    void cambiarEstado_motivoVacio_retorna400() {
        UUID id = UUID.randomUUID();

        assertThatThrownBy(() -> distribuidoraService.cambiarEstado(id,
                new CambiarEstadoRequest(EstadoDistribuidora.SUSPENDIDO, "   ")))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("motivo");
        verify(distribuidoraRepository, never()).findById(any());
    }

    @Test
    @DisplayName("distribuidora inexistente → 404")
    void cambiarEstado_distribuidoraInexistente_retorna404() {
        UUID id = UUID.randomUUID();
        when(distribuidoraRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> distribuidoraService.cambiarEstado(id,
                new CambiarEstadoRequest(EstadoDistribuidora.SUSPENDIDO, "Mora")))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("no encontrada");
    }

    @Test
    @DisplayName("suspender: invalida el caché y revoca la sesión de cada usuario de la distribuidora")
    void cambiarEstado_suspender_invalidaCacheYRevocaSesiones() throws Exception {
        UUID id = UUID.randomUUID();
        when(distribuidoraRepository.findById(id)).thenReturn(Optional.of(distribuidora(id, EstadoDistribuidora.ACTIVO)));
        when(distribuidoraRepository.save(any(Distribuidora.class))).thenAnswer(inv -> inv.getArgument(0));

        Usuario gerente = Usuario.builder().id(UUID.randomUUID()).firebaseUid("uid-gerente")
                .nombre("Gerente").correo("g@x.com").rol(RolUsuario.ADMINISTRADOR).activo(true).tenantId(id).build();
        Usuario vendedor = Usuario.builder().id(UUID.randomUUID()).firebaseUid("uid-vendedor")
                .nombre("Vendedor").correo("v@x.com").rol(RolUsuario.ENCARGADO_VENTAS).activo(true).tenantId(id).build();
        when(usuarioRepository.findByTenantId(id)).thenReturn(List.of(gerente, vendedor));

        try (MockedStatic<FirebaseAuth> firebaseStatic = mockStatic(FirebaseAuth.class)) {
            FirebaseAuth firebaseAuth = mock(FirebaseAuth.class);
            firebaseStatic.when(FirebaseAuth::getInstance).thenReturn(firebaseAuth);

            distribuidoraService.cambiarEstado(id, new CambiarEstadoRequest(EstadoDistribuidora.SUSPENDIDO, "Mora de pago"));

            verify(distribuidoraEstadoCache).invalidar(id);
            verify(firebaseAuth).revokeRefreshTokens("uid-gerente");
            verify(firebaseAuth).revokeRefreshTokens("uid-vendedor");
        }
    }

    @Test
    @DisplayName("reactivar: no revoca sesiones ni invalida el caché extra")
    void cambiarEstado_reactivar_noRevocaSesiones() {
        UUID id = UUID.randomUUID();
        when(distribuidoraRepository.findById(id)).thenReturn(Optional.of(distribuidora(id, EstadoDistribuidora.SUSPENDIDO)));
        when(distribuidoraRepository.save(any(Distribuidora.class))).thenAnswer(inv -> inv.getArgument(0));

        distribuidoraService.cambiarEstado(id, new CambiarEstadoRequest(EstadoDistribuidora.ACTIVO, "Pago regularizado"));

        verify(distribuidoraEstadoCache, never()).invalidar(any());
        verify(usuarioRepository, never()).findByTenantId(any());
    }

    @Test
    @DisplayName("reenviar acceso: nueva contraseña temporal y correo reenviado")
    void reenviarAcceso_conGerenteTitular_regeneraPasswordYEnviaCorreo() throws Exception {
        UUID id = UUID.randomUUID();
        Usuario gerente = Usuario.builder().id(UUID.randomUUID()).firebaseUid("uid-gerente")
                .nombre("Gerente").correo("gerente@heladosprueba.com").rol(RolUsuario.ADMINISTRADOR)
                .activo(true).tenantId(id).build();
        Distribuidora d = distribuidora(id, EstadoDistribuidora.PRUEBA);
        d.setGerenteTitular(gerente);
        when(distribuidoraRepository.findById(id)).thenReturn(Optional.of(d));

        try (MockedStatic<FirebaseAuth> firebaseStatic = mockStatic(FirebaseAuth.class)) {
            FirebaseAuth firebaseAuth = mock(FirebaseAuth.class);
            firebaseStatic.when(FirebaseAuth::getInstance).thenReturn(firebaseAuth);
            when(firebaseAuth.updateUser(any(UserRecord.UpdateRequest.class))).thenReturn(mock(UserRecord.class));
            when(firebaseAuth.generatePasswordResetLink(anyString())).thenReturn("https://gelox/reset?oob=xyz");

            DistribuidoraDTO resultado = distribuidoraService.reenviarAcceso(id);

            assertThat(resultado.correoEnviado()).isTrue();
            verify(firebaseAuth).updateUser(any(UserRecord.UpdateRequest.class));
            verify(emailService).enviarCorreoCuentaCreada(
                    "gerente@heladosprueba.com", "Gerente", "ADMINISTRADOR", "https://gelox/reset?oob=xyz");
        }
    }

    @Test
    @DisplayName("reenviar acceso sin gerente titular → 409")
    void reenviarAcceso_sinGerenteTitular_retorna409() {
        UUID id = UUID.randomUUID();
        when(distribuidoraRepository.findById(id)).thenReturn(Optional.of(distribuidora(id, EstadoDistribuidora.PRUEBA)));

        assertThatThrownBy(() -> distribuidoraService.reenviarAcceso(id))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("gerente titular");
    }

    @Test
    @DisplayName("listar filtra por estado")
    void listar_filtraPorEstado() {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        Distribuidora activa = distribuidora(id1, EstadoDistribuidora.ACTIVO);
        activa.setCreatedAt(LocalDateTime.now());
        Distribuidora suspendida = distribuidora(id2, EstadoDistribuidora.SUSPENDIDO);
        suspendida.setCreatedAt(LocalDateTime.now());
        when(distribuidoraRepository.findAll()).thenReturn(List.of(activa, suspendida));

        List<DistribuidoraDTO> resultado = distribuidoraService.listar("SUSPENDIDO", null);

        assertThat(resultado).hasSize(1);
        assertThat(resultado.get(0).id()).isEqualTo(id2);
    }
}
