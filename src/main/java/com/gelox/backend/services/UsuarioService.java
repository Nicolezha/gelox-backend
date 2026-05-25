package com.gelox.backend.services;

import com.gelox.backend.dto.CrearUsuarioDTO;
import com.gelox.backend.dto.EditarUsuarioDTO;
import com.gelox.backend.dto.UsuarioResponseDTO;
import com.gelox.backend.entities.RolUsuario;
import com.gelox.backend.entities.TipoEvento;
import com.gelox.backend.entities.Usuario;
import com.gelox.backend.repositories.UsuarioRepository;
import com.gelox.backend.security.RequiereRol;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.UserRecord;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UsuarioService {

    private final UsuarioRepository usuarioRepository;
    private final EventoSistemaService eventoSistemaService;

    @RequiereRol("ADMINISTRADOR")
    public UsuarioResponseDTO crearUsuario(CrearUsuarioDTO dto) throws Exception {
        validarRol(dto.getRol());

        if (usuarioRepository.existsByCorreo(dto.getCorreo())) {
            throw new IllegalArgumentException("Ya existe un usuario con ese correo.");
        }

        UserRecord.CreateRequest request = new UserRecord.CreateRequest()
                .setEmail(dto.getCorreo())
                .setPassword(dto.getContrasenaTemporal())
                .setDisplayName(dto.getNombre());

        UserRecord firebaseUser = FirebaseAuth.getInstance().createUser(request);

        FirebaseAuth.getInstance().setCustomUserClaims(
                firebaseUser.getUid(), Map.of("rol", dto.getRol())
        );

        Usuario usuario = new Usuario();
        usuario.setNombre(dto.getNombre());
        usuario.setCorreo(dto.getCorreo());
        usuario.setFirebaseUid(firebaseUser.getUid());
        usuario.setRol(RolUsuario.valueOf(dto.getRol()));
        usuario.setActivo(true);

        Usuario saved = usuarioRepository.save(usuario);
        eventoSistemaService.registrarEvento(
                TipoEvento.NUEVO_REGISTRO,
                "Nuevo usuario registrado: " + saved.getNombre(),
                saved.getId()
        );
        return toDTO(saved);
    }

    @RequiereRol("ADMINISTRADOR")
    public UsuarioResponseDTO editarUsuario(UUID id, EditarUsuarioDTO dto) throws Exception {
        validarRol(dto.getRol());

        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado con id: " + id));

        FirebaseAuth.getInstance().updateUser(
                new UserRecord.UpdateRequest(usuario.getFirebaseUid())
                        .setEmail(dto.getCorreo())
                        .setDisplayName(dto.getNombre())
        );

        FirebaseAuth.getInstance().setCustomUserClaims(
                usuario.getFirebaseUid(), Map.of("rol", dto.getRol())
        );

        usuario.setNombre(dto.getNombre());
        usuario.setCorreo(dto.getCorreo());
        usuario.setRol(RolUsuario.valueOf(dto.getRol()));

        Usuario actualizado = usuarioRepository.save(usuario);
        eventoSistemaService.registrarEvento(
                TipoEvento.EDICION_USUARIO,
                "Usuario editado: " + actualizado.getNombre() + " (" + actualizado.getCorreo() + ")",
                actualizado.getId()
        );
        return toDTO(actualizado);
    }

    @RequiereRol("ADMINISTRADOR")
    public void deshabilitarUsuario(UUID id) throws Exception {
        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado con id: " + id));

        FirebaseAuth.getInstance().updateUser(
                new UserRecord.UpdateRequest(usuario.getFirebaseUid()).setDisabled(true)
        );

        usuario.setActivo(false);
        usuarioRepository.save(usuario);
        eventoSistemaService.registrarEvento(
                TipoEvento.DESHABILITAR_USUARIO,
                "Usuario deshabilitado: " + usuario.getNombre() + " (" + usuario.getCorreo() + ")",
                usuario.getId()
        );
    }

    @RequiereRol("ADMINISTRADOR")
    public List<UsuarioResponseDTO> listarUsuarios() {
        return usuarioRepository.findAll()
                .stream().map(this::toDTO).collect(Collectors.toList());
    }

    @RequiereRol("ADMINISTRADOR")
    public UsuarioResponseDTO obtenerUsuario(UUID id) {
        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado con id: " + id));
        return toDTO(usuario);
    }

    private void validarRol(String rol) {
        if (!"ENCARGADO_INVENTARIO".equals(rol) && !"ENCARGADO_VENTAS".equals(rol)) {
            throw new IllegalArgumentException(
                    "Rol inválido. Use 'ENCARGADO_INVENTARIO' o 'ENCARGADO_VENTAS'.");
        }
    }

    private UsuarioResponseDTO toDTO(Usuario u) {
        UsuarioResponseDTO dto = new UsuarioResponseDTO();
        dto.setId(u.getId());
        dto.setNombre(u.getNombre());
        dto.setCorreo(u.getCorreo());
        dto.setRol(u.getRol().name());
        dto.setActivo(u.getActivo());
        dto.setUltimoAcceso(u.getUltimoAcceso());
        return dto;
    }

    @RequiereRol("ADMINISTRADOR")
    public void habilitarUsuario(UUID id) throws Exception {
        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado con id: " + id));

        FirebaseAuth.getInstance().updateUser(
                new UserRecord.UpdateRequest(usuario.getFirebaseUid()).setDisabled(false)
        );

        usuario.setActivo(true);
        usuarioRepository.save(usuario);
        eventoSistemaService.registrarEvento(
                TipoEvento.HABILITAR_USUARIO,
                "Usuario habilitado: " + usuario.getNombre() + " (" + usuario.getCorreo() + ")",
                usuario.getId()
        );
    }
}
