package com.gelox.backend.gobierno;

import com.gelox.backend.entities.Distribuidora;
import com.gelox.backend.entities.Usuario;
import com.gelox.backend.gobierno.dto.AuditoriaFilaDTO;
import com.gelox.backend.gobierno.dto.AuditoriaUsuarioDTO;
import com.gelox.backend.gobierno.dto.PaginaDTO;
import com.gelox.backend.repositories.DistribuidoraRepository;
import com.gelox.backend.repositories.UsuarioRepository;
import com.gelox.backend.security.RequiereRol;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * RF71 — Consulta del log de auditoría para el Superadministrador (/api/plataforma/auditoria).
 * Filtro por distribuidora: sus filas (tenant_id) y las del Superadministrador sobre ella
 * (entidad Distribuidora con ese entidad_id, que guardan tenant_id null).
 */
@Service
@RequiredArgsConstructor
public class AuditoriaConsultaService {

    static final int TAMANO_DEFECTO = 10;
    static final int TAMANO_MAXIMO = 100;

    private final AuditoriaRepository auditoriaRepository;
    private final DistribuidoraRepository distribuidoraRepository;
    private final UsuarioRepository usuarioRepository;

    /** Filtros de GET /api/plataforma/auditoria; null = sin filtro. desde y hasta son inclusivos. */
    public record Filtros(UUID distribuidoraId, UUID usuarioId, LocalDate desde, LocalDate hasta, String accion) {
    }

    @RequiereRol("SUPERADMINISTRADOR")
    @Transactional(readOnly = true)
    public PaginaDTO<AuditoriaFilaDTO> listar(Filtros filtros, Integer page, Integer size) {
        if (filtros.desde() != null && filtros.hasta() != null && filtros.desde().isAfter(filtros.hasta())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "La fecha inicial no puede ser posterior a la final");
        }
        int numero = page == null || page < 0 ? 0 : page;
        int tamano = size == null || size < 1 ? TAMANO_DEFECTO : Math.min(size, TAMANO_MAXIMO);

        Page<Auditoria> pagina = auditoriaRepository.findAll(especificacion(filtros),
                PageRequest.of(numero, tamano, Sort.by(Sort.Order.desc("fechaHora"), Sort.Order.desc("id"))));
        return PaginaDTO.de(pagina, this::aFilas);
    }

    /** Códigos de AccionAuditoria (FP-15 §2.16.3) para el filtro de acción. */
    @RequiereRol("SUPERADMINISTRADOR")
    public List<String> acciones() {
        return Arrays.stream(AccionAuditoria.values()).map(Enum::name).toList();
    }

    /** Usuarios que aparecen en el log (de la distribuidora, si se indica), por nombre. */
    @RequiereRol("SUPERADMINISTRADOR")
    @Transactional(readOnly = true)
    public List<AuditoriaUsuarioDTO> usuarios(UUID distribuidoraId) {
        List<UUID> ids = distribuidoraId == null
                ? auditoriaRepository.usuariosConFilas()
                : auditoriaRepository.usuariosConFilas(distribuidoraId);
        List<Usuario> usuarios = usuarioRepository.findAllById(ids);
        Map<UUID, String> nombres = nombresDistribuidora(usuarios.stream()
                .map(Usuario::getTenantId).filter(Objects::nonNull).collect(Collectors.toSet()));
        return usuarios.stream()
                .map(u -> new AuditoriaUsuarioDTO(u.getId(), u.getNombre(), u.getCorreo(),
                        u.getRol() != null ? u.getRol().name() : null, nombres.get(u.getTenantId())))
                .sorted(Comparator.comparing(AuditoriaUsuarioDTO::nombre,
                        Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
                .toList();
    }

    // ──────────────────────────────────────────────────────────────────────

    static Specification<Auditoria> especificacion(Filtros f) {
        return (root, query, cb) -> {
            List<Predicate> condiciones = new ArrayList<>();
            if (f.distribuidoraId() != null) {
                condiciones.add(cb.or(
                        cb.equal(root.get("tenantId"), f.distribuidoraId()),
                        cb.and(cb.equal(root.get("entidad"), "Distribuidora"),
                                cb.equal(root.get("entidadId"), f.distribuidoraId()))));
            }
            if (f.usuarioId() != null) {
                condiciones.add(cb.equal(root.get("usuarioId"), f.usuarioId()));
            }
            if (f.desde() != null) {
                condiciones.add(cb.greaterThanOrEqualTo(root.get("fechaHora"), f.desde().atStartOfDay()));
            }
            if (f.hasta() != null) {
                condiciones.add(cb.lessThan(root.get("fechaHora"), f.hasta().plusDays(1).atStartOfDay()));
            }
            if (f.accion() != null && !f.accion().isBlank()) {
                condiciones.add(cb.equal(root.get("accion"), f.accion().trim()));
            }
            return cb.and(condiciones.toArray(Predicate[]::new));
        };
    }

    private List<AuditoriaFilaDTO> aFilas(List<Auditoria> filas) {
        Set<UUID> idsDistribuidora = new HashSet<>();
        Set<UUID> idsUsuario = new HashSet<>();
        for (Auditoria a : filas) {
            UUID distribuidora = distribuidoraDe(a);
            if (distribuidora != null) idsDistribuidora.add(distribuidora);
            if (a.getUsuarioId() != null) idsUsuario.add(a.getUsuarioId());
        }
        Map<UUID, String> nombres = nombresDistribuidora(idsDistribuidora);
        Map<UUID, Usuario> usuarios = usuarioRepository.findAllById(idsUsuario).stream()
                .collect(Collectors.toMap(Usuario::getId, Function.identity()));

        return filas.stream().map(a -> {
            Usuario u = a.getUsuarioId() == null ? null : usuarios.get(a.getUsuarioId());
            String rol = a.getRol() != null ? a.getRol() : (u != null && u.getRol() != null ? u.getRol().name() : null);
            return new AuditoriaFilaDTO(a.getId(), a.getFechaHora(), a.getIp(), a.getTenantId(),
                    nombres.get(distribuidoraDe(a)), a.getUsuarioId(),
                    u != null ? u.getNombre() : null, u != null ? u.getCorreo() : null, rol,
                    a.getAccion(), a.getEntidad(), a.getEntidadId(), a.getDetalle());
        }).toList();
    }

    /** La del tenant o, si la fila es del Superadministrador sobre una distribuidora, esa. */
    private static UUID distribuidoraDe(Auditoria a) {
        if (a.getTenantId() != null) return a.getTenantId();
        return "Distribuidora".equals(a.getEntidad()) ? a.getEntidadId() : null;
    }

    /** HashMap (admite get(null)): las filas de plataforma no tienen distribuidora. */
    private Map<UUID, String> nombresDistribuidora(Collection<UUID> ids) {
        Map<UUID, String> nombres = new HashMap<>();
        if (ids.isEmpty()) return nombres;
        distribuidoraRepository.findAllById(ids)
                .forEach(d -> nombres.put(d.getId(), d.getNombreComercial()));
        return nombres;
    }
}
