package com.gelox.backend.services;

import com.gelox.backend.dto.ActualizarPerfilDTO;
import com.gelox.backend.dto.CambioContrasenaDTO;
import com.gelox.backend.entities.RolUsuario;
import com.gelox.backend.entities.Usuario;
import com.gelox.backend.exceptions.ContrasenaNoCoincideException;
import com.gelox.backend.exceptions.CorreoDuplicadoException;
import com.gelox.backend.repositories.UsuarioRepository;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.UserRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PerfilServiceTest {

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private FirebaseAuth firebaseAuth;

    @InjectMocks
    private PerfilService perfilService;

    private UUID userId;
    private Usuario usuario;
    private ActualizarPerfilDTO perfilDTO;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();

        usuario = new Usuario();
        usuario.setId(userId);
        usuario.setNombre("Gabriel Torres");
        usuario.setCorreo("gabriel@gelox.com");
        usuario.setTelefono("3001234567");
        usuario.setFirebaseUid("firebase-uid-abc");
        usuario.setRol(RolUsuario.ENCARGADO_VENTAS);
        usuario.setActivo(true);

        perfilDTO = new ActualizarPerfilDTO();
        perfilDTO.setNombre("Gabriel Torres Nuevo");
        perfilDTO.setCorreo("gabriel.nuevo@gelox.com");
        perfilDTO.setTelefono("3009876543");
    }

    @Test
    void actualizarPerfil_exitoso() {
        when(usuarioRepository.findById(userId)).thenReturn(Optional.of(usuario));
        when(usuarioRepository.findByCorreo(perfilDTO.getCorreo())).thenReturn(Optional.empty());
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(inv -> inv.getArgument(0));

        Usuario resultado = perfilService.actualizarPerfil(userId, perfilDTO);

        assertNotNull(resultado);
        assertEquals("Gabriel Torres Nuevo", resultado.getNombre());
        assertEquals("gabriel.nuevo@gelox.com", resultado.getCorreo());
        assertEquals("3009876543", resultado.getTelefono());
        verify(usuarioRepository, times(1)).save(usuario);
    }

    @Test
    void actualizarPerfil_correoDuplicado_lanzaCorreoDuplicadoException() {
        UUID otroId = UUID.randomUUID();
        Usuario otroUsuario = new Usuario();
        otroUsuario.setId(otroId);
        otroUsuario.setCorreo(perfilDTO.getCorreo());
        otroUsuario.setRol(RolUsuario.ENCARGADO_INVENTARIO);

        when(usuarioRepository.findById(userId)).thenReturn(Optional.of(usuario));
        when(usuarioRepository.findByCorreo(perfilDTO.getCorreo())).thenReturn(Optional.of(otroUsuario));

        assertThrows(CorreoDuplicadoException.class,
                () -> perfilService.actualizarPerfil(userId, perfilDTO));

        verify(usuarioRepository, never()).save(any());
    }

    @Test
    void cambiarContrasena_confirmacionNoCoincide_lanzaContrasenaNoCoincideException() {
        CambioContrasenaDTO dto = new CambioContrasenaDTO();
        dto.setContrasenaActual("actual123");
        dto.setNuevaContrasena("nueva456");
        dto.setConfirmacion("diferente789");

        assertThrows(ContrasenaNoCoincideException.class,
                () -> perfilService.cambiarContrasena("firebase-uid-abc", dto));

        verifyNoInteractions(firebaseAuth);
    }

    @Test
    void cambiarContrasena_firebaseAuthException_relanzaComoIllegalArgumentException() throws Exception {
        CambioContrasenaDTO dto = new CambioContrasenaDTO();
        dto.setContrasenaActual("actual123");
        dto.setNuevaContrasena("nueva456");
        dto.setConfirmacion("nueva456");

        UserRecord mockUserRecord = mock(UserRecord.class);
        when(firebaseAuth.getUser(anyString())).thenReturn(mockUserRecord);
        doThrow(mock(FirebaseAuthException.class)).when(firebaseAuth).updateUser(any(UserRecord.UpdateRequest.class));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> perfilService.cambiarContrasena("firebase-uid-abc", dto));

        assertTrue(ex.getMessage().startsWith("Error al actualizar la contraseña:"));
    }
}
