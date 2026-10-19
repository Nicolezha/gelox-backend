package com.gelox.backend.services;

import com.gelox.backend.dto.CrearUsuarioDTO;
import com.gelox.backend.dto.EditarUsuarioDTO;
import com.gelox.backend.dto.UsuarioResponseDTO;
import com.gelox.backend.entities.RolUsuario;
import com.gelox.backend.entities.TipoEvento;
import com.gelox.backend.entities.Usuario;
import com.gelox.backend.repositories.UsuarioRepository;
import com.gelox.backend.security.RequiereRol;
import com.gelox.backend.tenant.TenantContext;
import com.google.firebase.auth.AuthErrorCode;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.UserRecord;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class UsuarioService {

    private final UsuarioRepository usuarioRepository;
    private final EventoSistemaService eventoSistemaService;
    private final EmailService emailService;   // RF03 — notificaciones por correo

    @RequiereRol("ADMINISTRADOR")
    public UsuarioResponseDTO crearUsuario(CrearUsuarioDTO dto) throws Exception {
        validarRol(dto.getRol());
        dto.setCorreo(normalizarCorreo(dto.getCorreo()));
        // Antes de crear la cuenta en Firebase: sin distribuidora no se crea nada
        UUID tenantId = TenantContext.requerido();

        // RF03 — el correo no se puede repetir
        if (usuarioRepository.existsByCorreo(dto.getCorreo())) {
            throw new IllegalArgumentException("Ya existe un usuario con ese correo.");
        }

        UserRecord.CreateRequest request = new UserRecord.CreateRequest()
                .setEmail(dto.getCorreo())
                .setPassword(dto.getContrasenaTemporal())
                .setDisplayName(dto.getNombre());

        UserRecord firebaseUser;
        try {
            firebaseUser = FirebaseAuth.getInstance().createUser(request);
        } catch (FirebaseAuthException e) {
            if (e.getAuthErrorCode() == AuthErrorCode.EMAIL_ALREADY_EXISTS) {
                throw new IllegalArgumentException("Ya existe un usuario con ese correo.");
            }
            throw e;
        }

        FirebaseAuth.getInstance().setCustomUserClaims(
                firebaseUser.getUid(), Map.of("rol", dto.getRol(), "tenant_id", tenantId.toString())
        );

        Usuario usuario = new Usuario();
        usuario.setNombre(dto.getNombre());
        usuario.setCorreo(dto.getCorreo());
        usuario.setFirebaseUid(firebaseUser.getUid());
        usuario.setRol(RolUsuario.valueOf(dto.getRol()));
        usuario.setFotoUrl(dto.getFotoUrl());
        usuario.setActivo(true);
        // La relación distribuidora es de solo lectura (insertable = false): se persiste por tenant_id
        usuario.setTenantId(tenantId);

        Usuario saved = usuarioRepository.save(usuario);
        eventoSistemaService.registrarEvento(
                TipoEvento.NUEVO_REGISTRO,
                "Nuevo usuario registrado: " + saved.getNombre(),
                saved.getId()
        );

        // RF03 — notificar al usuario que su cuenta fue creada (un fallo de correo no revierte la creación)
        // No se envía la contraseña por correo: se incluye un enlace de Firebase para que el usuario
        // defina la suya (igual que la recuperación, RF02); la temporal la entrega el Gerente.
        try {
            String enlace = FirebaseAuth.getInstance().generatePasswordResetLink(saved.getCorreo());
            emailService.enviarCorreoCuentaCreada(saved.getCorreo(), saved.getNombre(),
                    saved.getRol().name(), enlace);
        } catch (Exception e) {
            log.warn("No se pudo enviar el correo de bienvenida a {}: {}", saved.getCorreo(), e.getMessage());
        }
        return toDTO(saved);
    }

    @RequiereRol("ADMINISTRADOR")
    public UsuarioResponseDTO editarUsuario(UUID id, EditarUsuarioDTO dto) throws Exception {
        validarRol(dto.getRol());

        Usuario usuario = usuarioRepository.findById(id)
                .filter(TenantContext::pertenece)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado con id: " + id));

        // RF03 — el correo no puede pertenecer a otro usuario
        dto.setCorreo(normalizarCorreo(dto.getCorreo()));
        boolean correoEnUso = usuarioRepository.findByCorreo(dto.getCorreo())
                .filter(otro -> !otro.getId().equals(id))
                .isPresent();
        if (correoEnUso) {
            throw new IllegalArgumentException("Ya existe otro usuario con ese correo.");
        }

        FirebaseAuth.getInstance().updateUser(
                new UserRecord.UpdateRequest(usuario.getFirebaseUid())
                        .setEmail(dto.getCorreo())
                        .setDisplayName(dto.getNombre())
        );

        FirebaseAuth.getInstance().setCustomUserClaims(
                usuario.getFirebaseUid(), Map.of("rol", dto.getRol(), "tenant_id", TenantContext.requerido().toString())
        );

        usuario.setNombre(dto.getNombre());
        usuario.setCorreo(dto.getCorreo());
        usuario.setRol(RolUsuario.valueOf(dto.getRol()));
        if (dto.getFotoUrl() != null) usuario.setFotoUrl(dto.getFotoUrl());

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
                .filter(TenantContext::pertenece)
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

        // RF03 — notificar al usuario que fue retirado/deshabilitado del sistema
        try {
            emailService.enviarCorreoCuentaDeshabilitada(usuario.getCorreo(), usuario.getNombre());
        } catch (Exception e) {
            log.warn("No se pudo enviar el correo de deshabilitación a {}: {}", usuario.getCorreo(), e.getMessage());
        }
    }

    @RequiereRol("ADMINISTRADOR")
    public List<UsuarioResponseDTO> listarUsuarios() {
        return usuarioRepository.findAll()
                .stream().map(this::toDTO).collect(Collectors.toList());
    }

    @RequiereRol("ADMINISTRADOR")
    public UsuarioResponseDTO obtenerUsuario(UUID id) {
        Usuario usuario = usuarioRepository.findById(id)
                .filter(TenantContext::pertenece)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado con id: " + id));
        return toDTO(usuario);
    }

    private String normalizarCorreo(String correo) {
        return correo == null ? null : correo.trim().toLowerCase();
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
        dto.setFotoUrl(u.getFotoUrl());
        return dto;
    }

    @RequiereRol("ADMINISTRADOR")
    public void habilitarUsuario(UUID id) throws Exception {
        Usuario usuario = usuarioRepository.findById(id)
                .filter(TenantContext::pertenece)
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
