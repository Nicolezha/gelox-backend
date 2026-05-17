package com.gelox.backend.rf05;

import com.gelox.backend.TestHelper;
import com.gelox.backend.dto.ActualizarPerfilDTO;
import com.gelox.backend.entities.RolUsuario;
import com.gelox.backend.entities.Usuario;
import com.gelox.backend.exceptions.CorreoDuplicadoException;
import com.gelox.backend.repositories.UsuarioRepository;
import com.gelox.backend.services.PerfilService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * RF05 — Actualización de perfil.
 * Cubre CP14, CP15.
 */
@ExtendWith(MockitoExtension.class)
class ActualizarPerfilTest {

    @Mock
    UsuarioRepository usuarioRepository;

    @InjectMocks
    PerfilService perfilService;

    private static final UUID ID_USUARIO = UUID.randomUUID();
    private static final UUID ID_OTRO    = UUID.randomUUID();
    private Usuario usuarioActual;
    private Usuario otroUsuario;

    @BeforeEach
    void setUp() {
        usuarioActual = TestHelper.buildUsuario(
                "uid-encargado-001", "encargado@gelox-test.com",
                RolUsuario.ENCARGADO_INVENTARIO, true);
        // Forzar el ID para que el repositorio lo encuentre por ID
        ReflectionTestUtils.setField(usuarioActual, "id", ID_USUARIO);

        otroUsuario = TestHelper.buildUsuario(
                "uid-otro-001", "otro@gelox-test.com",
                RolUsuario.ENCARGADO_VENTAS, true);
        ReflectionTestUtils.setField(otroUsuario, "id", ID_OTRO);

        // Inyectar valores de supabase (solo para no fallar en otras rutas del servicio)
        ReflectionTestUtils.setField(perfilService, "supabaseUrl", "http://localhost:9999");
        ReflectionTestUtils.setField(perfilService, "supabaseServiceKey", "test-key");
    }

    // -----------------------------------------------------------------------
    // CP14 — actualizar-perfil-exitoso
    // -----------------------------------------------------------------------
    @Test
    @DisplayName("CP14 - actualizar perfil exitoso: nombre, correo y teléfono se guardan; updated_at se actualiza")
    void cp14_actualizarPerfilExitoso() {
        when(usuarioRepository.findById(ID_USUARIO)).thenReturn(Optional.of(usuarioActual));
        when(usuarioRepository.findByCorreo("nuevo@gelox-test.com")).thenReturn(Optional.empty());
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(inv -> inv.getArgument(0));

        ActualizarPerfilDTO dto = new ActualizarPerfilDTO();
        dto.setNombre("Nuevo Nombre");
        dto.setCorreo("nuevo@gelox-test.com");
        dto.setTelefono("3001234567");

        Usuario resultado = perfilService.actualizarPerfil(ID_USUARIO, dto);

        assertThat(resultado.getNombre()).isEqualTo("Nuevo Nombre");
        assertThat(resultado.getCorreo()).isEqualTo("nuevo@gelox-test.com");
        assertThat(resultado.getTelefono()).isEqualTo("3001234567");
        verify(usuarioRepository).save(argThat(u ->
                "Nuevo Nombre".equals(u.getNombre()) &&
                "nuevo@gelox-test.com".equals(u.getCorreo())
        ));
    }

    // -----------------------------------------------------------------------
    // CP14-extra — fotoUrl se actualiza solo si viene en el DTO
    // -----------------------------------------------------------------------
    @Test
    @DisplayName("CP14-extra - fotoUrl se actualiza cuando se envía en el DTO")
    void cp14Extra_fotoUrlSeActualiza() {
        when(usuarioRepository.findById(ID_USUARIO)).thenReturn(Optional.of(usuarioActual));
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(inv -> inv.getArgument(0));

        ActualizarPerfilDTO dto = new ActualizarPerfilDTO();
        dto.setNombre("Nombre");
        dto.setCorreo("encargado@gelox-test.com"); // mismo correo, sin cambio
        dto.setFotoUrl("https://storage.example.com/foto.jpg");

        Usuario resultado = perfilService.actualizarPerfil(ID_USUARIO, dto);

        assertThat(resultado.getFotoUrl()).isEqualTo("https://storage.example.com/foto.jpg");
    }

    // -----------------------------------------------------------------------
    // CP14-extra2 — fotoUrl no se borra si el DTO no la envía (null/blank)
    // -----------------------------------------------------------------------
    @Test
    @DisplayName("CP14-extra2 - fotoUrl no se sobreescribe si el DTO envía null")
    void cp14Extra2_fotoUrlNoBorra() {
        usuarioActual.setFotoUrl("https://foto-existente.com/img.jpg");
        when(usuarioRepository.findById(ID_USUARIO)).thenReturn(Optional.of(usuarioActual));
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(inv -> inv.getArgument(0));

        ActualizarPerfilDTO dto = new ActualizarPerfilDTO();
        dto.setNombre("Nombre");
        dto.setCorreo("encargado@gelox-test.com");
        dto.setFotoUrl(null); // no se envía fotoUrl

        Usuario resultado = perfilService.actualizarPerfil(ID_USUARIO, dto);

        assertThat(resultado.getFotoUrl()).isEqualTo("https://foto-existente.com/img.jpg");
    }

    // -----------------------------------------------------------------------
    // CP15 — correo-duplicado → CorreoDuplicadoException (HTTP 409)
    // -----------------------------------------------------------------------
    @Test
    @DisplayName("CP15 - correo duplicado: otro usuario ya tiene ese correo → CorreoDuplicadoException")
    void cp15_correoDuplicado() {
        when(usuarioRepository.findById(ID_USUARIO)).thenReturn(Optional.of(usuarioActual));
        // El correo nuevo ya está registrado por otroUsuario
        when(usuarioRepository.findByCorreo("otro@gelox-test.com")).thenReturn(Optional.of(otroUsuario));

        ActualizarPerfilDTO dto = new ActualizarPerfilDTO();
        dto.setNombre("Nombre");
        dto.setCorreo("otro@gelox-test.com"); // correo del otro usuario
        dto.setTelefono("3001234567");

        assertThatThrownBy(() -> perfilService.actualizarPerfil(ID_USUARIO, dto))
                .isInstanceOf(CorreoDuplicadoException.class)
                .hasMessageContaining("correo");

        // El perfil del usuario NO debe haber cambiado
        verify(usuarioRepository, never()).save(any());
    }

    // -----------------------------------------------------------------------
    // CP15-extra — mismo correo del mismo usuario no lanza excepción
    // -----------------------------------------------------------------------
    @Test
    @DisplayName("CP15-extra - actualizar con el mismo correo propio no lanza CorreoDuplicadoException")
    void cp15Extra_mismoCorreoPropio_noLanzaExcepcion() {
        when(usuarioRepository.findById(ID_USUARIO)).thenReturn(Optional.of(usuarioActual));
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(inv -> inv.getArgument(0));

        ActualizarPerfilDTO dto = new ActualizarPerfilDTO();
        dto.setNombre("Mismo Usuario");
        dto.setCorreo("encargado@gelox-test.com"); // mismo correo ya registrado para este usuario

        assertThatNoException().isThrownBy(
                () -> perfilService.actualizarPerfil(ID_USUARIO, dto)
        );
    }
}
