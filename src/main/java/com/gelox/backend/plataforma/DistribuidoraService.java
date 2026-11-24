package com.gelox.backend.plataforma;

import com.gelox.backend.entities.Distribuidora;
import com.gelox.backend.entities.EstadoDistribuidora;
import com.gelox.backend.entities.RolUsuario;
import com.gelox.backend.entities.TipoEvento;
import com.gelox.backend.entities.Usuario;
import com.gelox.backend.exceptions.CampoInvalidoException;
import com.gelox.backend.gobierno.Auditable;
import com.gelox.backend.gobierno.Auditoria;
import com.gelox.backend.gobierno.AuditoriaDetalle;
import com.gelox.backend.gobierno.AuditoriaRepository;
import com.gelox.backend.gobierno.CuotaService;
import com.gelox.backend.gobierno.PlanService;
import com.gelox.backend.gobierno.PlanSuscripcion;
import com.gelox.backend.gobierno.PlanSuscripcionRepository;
import com.gelox.backend.plataforma.dto.AsignarPlanRequest;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

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
    private final PlanSuscripcionRepository planRepository;   // RF70 — distribuidora.plan_id
    private final CuotaService cuotaService;                   // RF70 — uso de cuotas en el detalle
    private final AuditoriaRepository auditoriaRepository;     // RF70 — fechaPlan (último PLAN_ASIGNAR)

    // ─────────────────────────────────────────────────────────────────
    // RF58 — Aprovisionamiento
    // ─────────────────────────────────────────────────────────────────

    @Auditable(accion = "DISTRIBUIDORA_APROVISIONAR", entidad = "Distribuidora")
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
        // RF70 — plan_suscripcion.id (antes de consultar Firebase); distribuidora.plan se llena con su nombre
        PlanSuscripcion plan = (req.planId() == null ? Optional.<PlanSuscripcion>empty()
                : planRepository.findById(req.planId()))
                .orElseThrow(() -> new CampoInvalidoException(HttpStatus.BAD_REQUEST,
                        "Selecciona un plan válido", "planId"));
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
                .plan(plan.getNombre())
                .planId(plan.getId())
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

        Map<String, Object> detalle = new LinkedHashMap<>();
        detalle.put("plan", plan.getNombre());
        detalle.put("correoGerente", correo);
        AuditoriaDetalle.poner(detalle);
        return DistribuidoraDTO.from(distribuidora, correoEnviado, new DistribuidoraDTO.Extra(
                gerente.getNombre(), plan.getNombre(), distribuidora.getCreatedAt(), 1L, null, null));
    }

    // ─────────────────────────────────────────────────────────────────
    // RF59 — Ciclo de vida
    // ─────────────────────────────────────────────────────────────────

    @Auditable(accion = "DISTRIBUIDORA_CAMBIAR_ESTADO", entidad = "Distribuidora")
    @Transactional
    @RequiereRol("SUPERADMINISTRADOR")
    public DistribuidoraDTO cambiarEstado(UUID id, CambiarEstadoRequest req) {
        if (req.motivo() == null || req.motivo().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El motivo es obligatorio");
        }

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

        Map<String, Object> detalle = new LinkedHashMap<>();
        detalle.put("estadoAnterior", anterior);
        detalle.put("estadoNuevo", req.estado());
        detalle.put("motivo", req.motivo().trim());
        AuditoriaDetalle.poner(detalle);
        return aDTO(distribuidora, null, false, null);
    }

    // ─────────────────────────────────────────────────────────────────
    // RF70 — Asignar plan
    // ─────────────────────────────────────────────────────────────────

    /**
     * Aplica el plan de inmediato; el consumo no se reinicia. Si el uso actual ya supera alguna
     * cuota del plan nuevo se asigna igual y la respuesta trae {@code advertencias} (FP-15 §2.16.2):
     * la distribuidora solo deja de poder crear más de ese recurso.
     */
    @Auditable(accion = "PLAN_ASIGNAR", entidad = "Distribuidora")
    @Transactional
    @RequiereRol("SUPERADMINISTRADOR")
    public DistribuidoraDTO asignarPlan(UUID id, AsignarPlanRequest req) {
        Distribuidora distribuidora = buscarPorId(id);
        if (distribuidora.getEstado() == EstadoDistribuidora.INACTIVO) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "La distribuidora está inactiva: no admite cambios de plan");
        }
        if (req == null || req.planId() == null) {
            throw new CampoInvalidoException(HttpStatus.BAD_REQUEST, "El plan es obligatorio", "planId");
        }
        if (req.motivo() == null || req.motivo().isBlank()) {
            throw new CampoInvalidoException(HttpStatus.BAD_REQUEST, "El motivo es obligatorio", "motivo");
        }
        PlanSuscripcion nuevo = planRepository.findById(req.planId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Plan no encontrado"));
        if (nuevo.getId().equals(distribuidora.getPlanId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "La distribuidora ya tiene ese plan");
        }

        String planAnterior = Optional.ofNullable(distribuidora.getPlanId())
                .flatMap(planRepository::findById)
                .map(PlanSuscripcion::getNombre)
                .orElse(distribuidora.getPlan());
        List<CuotaService.CuotaExcedida> excedidas = cuotaService.excedidas(id, nuevo);
        boolean hayExcedidas = excedidas != null && !excedidas.isEmpty();

        distribuidora.setPlanId(nuevo.getId());
        distribuidora.setPlan(nuevo.getNombre());
        distribuidora = distribuidoraRepository.save(distribuidora);

        Map<String, Object> detalle = new LinkedHashMap<>();
        detalle.put("planAnterior", planAnterior);
        detalle.put("planNuevo", nuevo.getNombre());
        detalle.put("motivo", req.motivo().trim());
        detalle.put("cuotas", PlanService.cuotasDe(nuevo));
        if (hayExcedidas) detalle.put("cuotasExcedidas", excedidas);
        AuditoriaDetalle.poner(detalle);

        return aDTO(distribuidora, null, true, hayExcedidas ? excedidas : null, LocalDateTime.now(ZONA_BOGOTA));
    }

    @Auditable(accion = "DISTRIBUIDORA_REENVIAR_ACCESO", entidad = "Distribuidora")
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
        AuditoriaDetalle.poner(Map.of("correoGerente", gerente.getCorreo()));
        return aDTO(distribuidora, correoEnviado, false, null);
    }

    // ─────────────────────────────────────────────────────────────────
    // Consulta
    // ─────────────────────────────────────────────────────────────────

    @RequiereRol("SUPERADMINISTRADOR")
    @Transactional(readOnly = true)
    public List<DistribuidoraDTO> listar(String estado, String q) {
        String filtroTexto = q == null ? "" : q.trim().toLowerCase();
        Map<UUID, String> nombresPlan = planRepository.findAll().stream()
                .collect(Collectors.toMap(PlanSuscripcion::getId, PlanSuscripcion::getNombre));
        return distribuidoraRepository.findAll().stream()
                .filter(d -> estado == null || estado.isBlank() || d.getEstado().name().equalsIgnoreCase(estado))
                .filter(d -> filtroTexto.isEmpty()
                        || d.getNombreComercial().toLowerCase().contains(filtroTexto)
                        || d.getNit().toLowerCase().contains(filtroTexto)
                        || d.getSubdominio().toLowerCase().contains(filtroTexto))
                .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                .map(d -> DistribuidoraDTO.from(d, null, new DistribuidoraDTO.Extra(
                        nombreGerente(d), nombresPlan.getOrDefault(d.getPlanId(), d.getPlan()), fechaPlan(d),
                        usuarioRepository.countByTenantIdAndActivoTrue(d.getId()), null, null)))
                .toList();
    }

    /** Detalle con el uso de cada cuota ({usado, limite}) contra el plan asignado. */
    @RequiereRol("SUPERADMINISTRADOR")
    @Transactional(readOnly = true)
    public DistribuidoraDTO obtener(UUID id) {
        return aDTO(buscarPorId(id), null, true, null);
    }

    // ─────────────────────────────────────────────────────────────────
    // DTO del panel
    // ─────────────────────────────────────────────────────────────────

    private DistribuidoraDTO aDTO(Distribuidora d, Boolean correoEnviado, boolean conUso,
                                  List<CuotaService.CuotaExcedida> advertencias) {
        return aDTO(d, correoEnviado, conUso, advertencias, fechaPlan(d));
    }

    private DistribuidoraDTO aDTO(Distribuidora d, Boolean correoEnviado, boolean conUso,
                                  List<CuotaService.CuotaExcedida> advertencias, LocalDateTime fechaPlan) {
        PlanSuscripcion plan = Optional.ofNullable(d.getPlanId()).flatMap(planRepository::findById).orElse(null);
        return DistribuidoraDTO.from(d, correoEnviado, new DistribuidoraDTO.Extra(
                nombreGerente(d),
                plan != null ? plan.getNombre() : d.getPlan(),
                fechaPlan,
                usuarioRepository.countByTenantIdAndActivoTrue(d.getId()),
                conUso ? cuotaService.uso(d.getId(), plan) : null,
                advertencias));
    }

    /** Fecha del último PLAN_ASIGNAR de la distribuidora o, si nunca cambió de plan, la de su alta. */
    private LocalDateTime fechaPlan(Distribuidora d) {
        return auditoriaRepository
                .findFirstByAccionAndEntidadIdOrderByFechaHoraDesc("PLAN_ASIGNAR", d.getId())
                .map(Auditoria::getFechaHora)
                .orElse(d.getCreatedAt());
    }

    private static String nombreGerente(Distribuidora d) {
        return d.getGerenteTitular() != null ? d.getGerenteTitular().getNombre() : null;
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
