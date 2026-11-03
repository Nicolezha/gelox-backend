package com.gelox.backend.plataforma;

import com.gelox.backend.entities.Distribuidora;
import com.gelox.backend.entities.EstadoDistribuidora;
import com.gelox.backend.entities.RolUsuario;
import com.gelox.backend.entities.TipoEvento;
import com.gelox.backend.entities.Usuario;
import com.gelox.backend.plataforma.dto.CambiarEstadoRequest;
import com.gelox.backend.plataforma.dto.CrearDistribuidoraRequest;
import com.gelox.backend.plataforma.dto.DistribuidoraDTO;
import com.gelox.backend.repositories.DistribuidoraRepository;
import com.gelox.backend.repositories.UsuarioRepository;
import com.gelox.backend.security.RequiereRol;
import com.gelox.backend.services.EmailService;
import com.gelox.backend.services.EventoSistemaService;
import com.gelox.backend.tenant.DistribuidoraEstadoCache;
import com.gelox.backend.tenant.TenantContext;
import com.google.firebase.auth.AuthErrorCode;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.UserRecord;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * RF58/RF59 — Aprovisionamiento y ciclo de vida de distribuidoras (panel del
 * Superadministrador). El Superadministrador no tiene {@link TenantContext}:
 * toda fila que se guarde en nombre de la distribuidora nueva lleva su
 * tenantId asignado a mano, y el evento del sistema se registra fijando el
 * contexto temporalmente (igual que la landing pública, T119-BE3).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DistribuidoraService {

    private static final ZoneId ZONA_BOGOTA = ZoneId.of("America/Bogota");

    /** Identidad por defecto de GELOX para una distribuidora recién aprovisionada. */
    private static final String COLOR_PRIMARIO_DEFECTO = "#1F3864";
    private static final String COLOR_SECUNDARIO_DEFECTO = "#2E75B6";

    private static final String ALFABETO_PASSWORD =
            "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789";
    private static final int LARGO_PASSWORD = 12;
    private static final SecureRandom ALEATORIO = new SecureRandom();

    private final DistribuidoraRepository distribuidoraRepository;
    private final UsuarioRepository usuarioRepository;
    private final EventoSistemaService eventoSistemaService;
    private final EmailService emailService;
    private final DistribuidoraEstadoCache distribuidoraEstadoCache;

    // ─────────────────────────────────────────────────────────────────
    // RF58 — Aprovisionamiento
    // ─────────────────────────────────────────────────────────────────

    @Transactional
    @RequiereRol("SUPERADMINISTRADOR")
    public DistribuidoraDTO aprovisionar(CrearDistribuidoraRequest req) {
        String nit = req.nit().trim();
        String subdominio = req.subdominio().trim().toLowerCase();
        String correo = req.correoGerente().trim().toLowerCase();

        if (distribuidoraRepository.existsByNit(nit)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ya existe una distribuidora con ese NIT");
        }
        if (distribuidoraRepository.existsBySubdominio(subdominio)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ese subdominio ya está en uso");
        }
        if (usuarioRepository.existsByCorreo(correo) || existeEnFirebase(correo)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ya existe un usuario con ese correo");
        }

        Distribuidora distribuidora = distribuidoraRepository.save(Distribuidora.builder()
                .razonSocial(req.razonSocial().trim())
                .nit(nit)
                .nombreComercial(req.nombreComercial().trim())
                .subdominio(subdominio)
                .correoGerente(correo)
                .telefono(req.telefono())
                .direccion(req.direccion())
                .plan(req.plan().trim())
                .estado(EstadoDistribuidora.PRUEBA)
                .colorPrimario(COLOR_PRIMARIO_DEFECTO)
                .colorSecundario(COLOR_SECUNDARIO_DEFECTO)
                .build());

        UserRecord firebaseUser;
        try {
            firebaseUser = FirebaseAuth.getInstance().createUser(new UserRecord.CreateRequest()
                    .setEmail(correo)
                    .setPassword(generarPasswordTemporal())
                    .setDisplayName(req.nombreGerente().trim()));
        } catch (FirebaseAuthException e) {
            if (e.getAuthErrorCode() == AuthErrorCode.EMAIL_ALREADY_EXISTS) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Ya existe un usuario con ese correo");
            }
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "No se pudo crear la cuenta del gerente en Firebase");
        }

        Usuario gerente;
        try {
            FirebaseAuth.getInstance().setCustomUserClaims(firebaseUser.getUid(),
                    Map.of("rol", RolUsuario.ADMINISTRADOR.name(), "tenant_id", distribuidora.getId().toString()));

            gerente = usuarioRepository.save(Usuario.builder()
                    .firebaseUid(firebaseUser.getUid())
                    .nombre(req.nombreGerente().trim())
                    .correo(correo)
                    .rol(RolUsuario.ADMINISTRADOR)
                    .activo(true)
                    .tenantId(distribuidora.getId())
                    .build());

            distribuidora.setGerenteTitular(gerente);
            distribuidora = distribuidoraRepository.save(distribuidora);
        } catch (RuntimeException | FirebaseAuthException e) {
            eliminarUsuarioFirebaseSilencioso(firebaseUser.getUid());
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "No se pudo crear el usuario gerente de la distribuidora");
        }

        registrarEventoComoSuperadministrador(distribuidora.getId(), TipoEvento.APROVISIONAMIENTO_DISTRIBUIDORA,
                "Distribuidora aprovisionada: " + distribuidora.getNombreComercial(), gerente.getId());

        boolean correoEnviado = enviarCorreoAcceso(correo, req.nombreGerente().trim());
        return DistribuidoraDTO.from(distribuidora, correoEnviado);
    }

    // ─────────────────────────────────────────────────────────────────
    // RF59 — Ciclo de vida
    // ─────────────────────────────────────────────────────────────────

    @Transactional
    @RequiereRol("SUPERADMINISTRADOR")
    public DistribuidoraDTO cambiarEstado(UUID id, CambiarEstadoRequest req) {
        Distribuidora distribuidora = buscarPorId(id);

        if (!distribuidora.puedeCambiarA(req.estado())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Transición no válida");
        }

        EstadoDistribuidora anterior = distribuidora.getEstado();
        distribuidora.setEstado(req.estado());
        distribuidora.setMotivoEstado(req.motivo().trim());
        distribuidora.setFechaEstado(LocalDateTime.now(ZONA_BOGOTA));
        distribuidora = distribuidoraRepository.save(distribuidora);

        registrarEventoComoSuperadministrador(id, TipoEvento.CAMBIO_ESTADO_DISTRIBUIDORA,
                "Distribuidora " + anterior + " → " + req.estado() + ": " + req.motivo().trim(), null);

        if (req.estado() == EstadoDistribuidora.SUSPENDIDO || req.estado() == EstadoDistribuidora.INACTIVO) {
            distribuidoraEstadoCache.invalidar(id);
            revocarSesionesDeLaDistribuidora(id);
        }

        return DistribuidoraDTO.from(distribuidora, null);
    }

    @Transactional
    @RequiereRol("SUPERADMINISTRADOR")
    public DistribuidoraDTO reenviarAcceso(UUID id) {
        Distribuidora distribuidora = buscarPorId(id);
        Usuario gerente = distribuidora.getGerenteTitular();
        if (gerente == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "La distribuidora no tiene gerente titular");
        }

        try {
            FirebaseAuth.getInstance().updateUser(new UserRecord.UpdateRequest(gerente.getFirebaseUid())
                    .setPassword(generarPasswordTemporal()));
        } catch (FirebaseAuthException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "No se pudo generar una nueva contraseña temporal en Firebase");
        }

        boolean correoEnviado = enviarCorreoAcceso(gerente.getCorreo(), gerente.getNombre());
        return DistribuidoraDTO.from(distribuidora, correoEnviado);
    }

    // ─────────────────────────────────────────────────────────────────
    // Consulta
    // ─────────────────────────────────────────────────────────────────

    @RequiereRol("SUPERADMINISTRADOR")
    public List<DistribuidoraDTO> listar(String estado, String q) {
        String filtroTexto = q == null ? "" : q.trim().toLowerCase();
        return distribuidoraRepository.findAll().stream()
                .filter(d -> estado == null || estado.isBlank() || d.getEstado().name().equalsIgnoreCase(estado))
                .filter(d -> filtroTexto.isEmpty()
                        || d.getNombreComercial().toLowerCase().contains(filtroTexto)
                        || d.getNit().toLowerCase().contains(filtroTexto)
                        || d.getSubdominio().toLowerCase().contains(filtroTexto))
                .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                .map(d -> DistribuidoraDTO.from(d, null))
                .toList();
    }

    @RequiereRol("SUPERADMINISTRADOR")
    public DistribuidoraDTO obtener(UUID id) {
        return DistribuidoraDTO.from(buscarPorId(id), null);
    }

    // ─────────────────────────────────────────────────────────────────
    // Privados
    // ─────────────────────────────────────────────────────────────────

    private Distribuidora buscarPorId(UUID id) {
        return distribuidoraRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Distribuidora no encontrada"));
    }

    private boolean existeEnFirebase(String correo) {
        try {
            FirebaseAuth.getInstance().getUserByEmail(correo);
            return true;
        } catch (FirebaseAuthException e) {
            if (e.getAuthErrorCode() == AuthErrorCode.USER_NOT_FOUND) {
                return false;
            }
            log.warn("No se pudo verificar en Firebase si el correo {} ya existe: {}", correo, e.getMessage());
            return false;
        }
    }

    private void eliminarUsuarioFirebaseSilencioso(String uid) {
        try {
            FirebaseAuth.getInstance().deleteUser(uid);
        } catch (FirebaseAuthException e) {
            log.error("No se pudo revertir el usuario de Firebase {} tras un fallo de aprovisionamiento: {}",
                    uid, e.getMessage());
        }
    }

    /** No se envía la temporal por correo: se reutiliza el enlace de definición de contraseña, como en RF03. */
    private boolean enviarCorreoAcceso(String correo, String nombreGerente) {
        try {
            String enlace = FirebaseAuth.getInstance().generatePasswordResetLink(correo);
            emailService.enviarCorreoCuentaCreada(correo, nombreGerente, RolUsuario.ADMINISTRADOR.name(), enlace);
            return true;
        } catch (Exception e) {
            log.warn("No se pudo enviar el correo de acceso a {}: {}", correo, e.getMessage());
            return false;
        }
    }

    /** El Superadministrador no tiene TenantContext: se fija temporalmente para registrar el evento. */
    private void registrarEventoComoSuperadministrador(UUID tenantId, TipoEvento tipo, String descripcion, UUID usuarioId) {
        TenantContext.set(tenantId);
        try {
            eventoSistemaService.registrarEvento(tipo, descripcion, usuarioId);
        } finally {
            TenantContext.clear();
        }
    }

    private void revocarSesionesDeLaDistribuidora(UUID tenantId) {
        for (Usuario usuario : usuarioRepository.findByTenantId(tenantId)) {
            try {
                FirebaseAuth.getInstance().revokeRefreshTokens(usuario.getFirebaseUid());
            } catch (FirebaseAuthException e) {
                log.warn("No se pudo revocar la sesión de {}: {}", usuario.getFirebaseUid(), e.getMessage());
            }
        }
    }

    private String generarPasswordTemporal() {
        StringBuilder sb = new StringBuilder(LARGO_PASSWORD);
        for (int i = 0; i < LARGO_PASSWORD; i++) {
            sb.append(ALFABETO_PASSWORD.charAt(ALEATORIO.nextInt(ALFABETO_PASSWORD.length())));
        }
        return sb.toString();
    }
}
