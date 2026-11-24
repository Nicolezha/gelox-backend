package com.gelox.backend.gobierno;

import com.gelox.backend.entities.Distribuidora;
import com.gelox.backend.entities.EstadoDistribuidora;
import com.gelox.backend.exceptions.CampoInvalidoException;
import com.gelox.backend.gobierno.dto.PlanDTO;
import com.gelox.backend.gobierno.dto.PlanRequest;
import com.gelox.backend.repositories.DistribuidoraRepository;
import com.gelox.backend.security.RequiereRol;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;

/**
 * RF70 — Planes de suscripción del Superadministrador (/api/plataforma/planes).
 * Nombre único sin distinguir mayúsculas ni tildes (409), precio y cuotas no negativos (400),
 * un máximo null = sin límite. No se borra un plan con distribuidoras asignadas (409).
 */
@Service
@RequiredArgsConstructor
public class PlanService {

    private static final int MAX_NOMBRE = 30;

    /** Campos que se comparan para el detalle de PLAN_EDITAR (nombre del contrato → valor). */
    private static final Map<String, Function<PlanSuscripcion, Object>> CAMPOS = new LinkedHashMap<>();

    static {
        CAMPOS.put("nombre", PlanSuscripcion::getNombre);
        CAMPOS.put("descripcion", PlanSuscripcion::getDescripcion);
        CAMPOS.put("precioMensual", PlanSuscripcion::getPrecioMensual);
        CAMPOS.put("maxUsuarios", PlanSuscripcion::getMaxUsuarios);
        CAMPOS.put("maxProductos", PlanSuscripcion::getMaxProductos);
        CAMPOS.put("maxComerciantes", PlanSuscripcion::getMaxComerciantes);
        CAMPOS.put("maxAlmacenamientoMb", PlanSuscripcion::getMaxAlmacenamientoMb);
        CAMPOS.put("maxLlamadasIaMes", PlanSuscripcion::getMaxLlamadasIaMes);
    }

    private final PlanSuscripcionRepository planRepository;
    private final DistribuidoraRepository distribuidoraRepository;
    private final CuotaService cuotaService;

    @RequiereRol("SUPERADMINISTRADOR")
    @Transactional(readOnly = true)
    public List<PlanDTO> listar() {
        return planRepository.findAll().stream()
                .sorted(Comparator.comparing(PlanSuscripcion::getPrecioMensual,
                                Comparator.nullsFirst(Comparator.naturalOrder()))
                        .thenComparing(PlanSuscripcion::getNombre))
                .map(this::aDTO)
                .toList();
    }

    @RequiereRol("SUPERADMINISTRADOR")
    @Transactional(readOnly = true)
    public PlanDTO obtener(UUID id) {
        return aDTO(buscar(id));
    }

    @Auditable(accion = "PLAN_CREAR", entidad = "PlanSuscripcion")
    @RequiereRol("SUPERADMINISTRADOR")
    @Transactional
    public PlanDTO crear(PlanRequest req) {
        validar(req, null);
        PlanSuscripcion plan = new PlanSuscripcion();
        aplicar(plan, req);
        plan = planRepository.save(plan);

        Map<String, Object> detalle = new LinkedHashMap<>();
        detalle.put("plan", plan.getNombre());
        detalle.put("precioMensual", plan.getPrecioMensual());
        detalle.put("cuotas", cuotasDe(plan));
        AuditoriaDetalle.poner(detalle);
        return aDTO(plan);
    }

    @Auditable(accion = "PLAN_EDITAR", entidad = "PlanSuscripcion")
    @RequiereRol("SUPERADMINISTRADOR")
    @Transactional
    public PlanDTO editar(UUID id, PlanRequest req) {
        PlanSuscripcion plan = buscar(id);
        validar(req, id);

        Map<String, Object> antes = new LinkedHashMap<>();
        CAMPOS.forEach((campo, getter) -> antes.put(campo, getter.apply(plan)));
        String nombreAnterior = plan.getNombre();

        aplicar(plan, req);
        PlanSuscripcion guardado = planRepository.save(plan);

        // distribuidora.plan (texto) repite el nombre mientras exista el frontend anterior
        if (!guardado.getNombre().equals(nombreAnterior)) {
            List<Distribuidora> asignadas = distribuidoraRepository.findByPlanId(id);
            asignadas.forEach(d -> d.setPlan(guardado.getNombre()));
            distribuidoraRepository.saveAll(asignadas);
        }

        List<Map<String, Object>> cambios = new ArrayList<>();
        CAMPOS.forEach((campo, getter) -> {
            Object anterior = antes.get(campo);
            Object nuevo = getter.apply(guardado);
            if (!iguales(anterior, nuevo)) {
                Map<String, Object> cambio = new LinkedHashMap<>();
                cambio.put("campo", campo);
                cambio.put("antes", anterior);
                cambio.put("despues", nuevo);
                cambios.add(cambio);
            }
        });
        Map<String, Object> detalle = new LinkedHashMap<>();
        detalle.put("plan", nombreAnterior);
        detalle.put("cambios", cambios);
        AuditoriaDetalle.poner(detalle);
        return aDTO(guardado);
    }

    @RequiereRol("SUPERADMINISTRADOR")
    @Transactional
    public void eliminar(UUID id) {
        PlanSuscripcion plan = buscar(id);
        if (distribuidoraRepository.existsByPlanId(id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "No se puede eliminar un plan con distribuidoras asignadas");
        }
        planRepository.delete(plan);
    }

    /** Cuotas del plan como en el contrato: {maxUsuarios, …, maxLlamadasIaMes}. */
    public static Map<String, Integer> cuotasDe(PlanSuscripcion p) {
        Map<String, Integer> cuotas = new LinkedHashMap<>();
        cuotas.put("maxUsuarios", p.getMaxUsuarios());
        cuotas.put("maxProductos", p.getMaxProductos());
        cuotas.put("maxComerciantes", p.getMaxComerciantes());
        cuotas.put("maxAlmacenamientoMb", p.getMaxAlmacenamientoMb());
        cuotas.put("maxLlamadasIaMes", p.getMaxLlamadasIaMes());
        return cuotas;
    }

    // ──────────────────────────────────────────────────────────────────────

    PlanSuscripcion buscar(UUID id) {
        return planRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Plan no encontrado"));
    }

    private void validar(PlanRequest req, UUID idActual) {
        String nombre = req == null || req.nombre() == null ? "" : req.nombre().trim();
        if (nombre.isEmpty()) {
            throw new CampoInvalidoException(HttpStatus.BAD_REQUEST, "El nombre del plan es obligatorio", "nombre");
        }
        if (nombre.length() > MAX_NOMBRE) {
            throw new CampoInvalidoException(HttpStatus.BAD_REQUEST,
                    "El nombre del plan admite hasta " + MAX_NOMBRE + " caracteres", "nombre");
        }
        String clave = normalizar(nombre);
        boolean repetido = planRepository.findAll().stream()
                .anyMatch(p -> !p.getId().equals(idActual) && normalizar(p.getNombre()).equals(clave));
        if (repetido) {
            throw new CampoInvalidoException(HttpStatus.CONFLICT, "Ya existe un plan con ese nombre", "nombre");
        }
        if (req.precioMensual() == null || req.precioMensual().compareTo(BigDecimal.ZERO) < 0) {
            throw new CampoInvalidoException(HttpStatus.BAD_REQUEST,
                    "El precio mensual es obligatorio y no puede ser negativo", "precioMensual");
        }
        validarCuota(req.maxUsuarios(), "maxUsuarios");
        validarCuota(req.maxProductos(), "maxProductos");
        validarCuota(req.maxComerciantes(), "maxComerciantes");
        validarCuota(req.maxAlmacenamientoMb(), "maxAlmacenamientoMb");
        validarCuota(req.maxLlamadasIaMes(), "maxLlamadasIaMes");
    }

    private static void validarCuota(Integer valor, String campo) {
        if (valor != null && valor < 0) {
            throw new CampoInvalidoException(HttpStatus.BAD_REQUEST, "Las cuotas no pueden ser negativas", campo);
        }
    }

    private static void aplicar(PlanSuscripcion plan, PlanRequest req) {
        plan.setNombre(req.nombre().trim());
        plan.setDescripcion(req.descripcion() == null || req.descripcion().isBlank() ? null : req.descripcion().trim());
        plan.setPrecioMensual(req.precioMensual());
        plan.setMaxUsuarios(req.maxUsuarios());
        plan.setMaxProductos(req.maxProductos());
        plan.setMaxComerciantes(req.maxComerciantes());
        plan.setMaxAlmacenamientoMb(req.maxAlmacenamientoMb());
        plan.setMaxLlamadasIaMes(req.maxLlamadasIaMes());
    }

    private PlanDTO aDTO(PlanSuscripcion plan) {
        List<PlanDTO.DistribuidoraDelPlan> distribuidoras = distribuidoraRepository.findByPlanId(plan.getId()).stream()
                // Las que operan primero (por antigüedad) y las inactivas al final
                .sorted(Comparator.comparing((Distribuidora d) -> d.getEstado() == EstadoDistribuidora.INACTIVO)
                        .thenComparing(Distribuidora::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .map(d -> new PlanDTO.DistribuidoraDelPlan(d.getId(), d.getNombreComercial(), d.getEstado(),
                        cuotaService.usados(d.getId())))
                .toList();
        return PlanDTO.from(plan, distribuidoras);
    }

    private static boolean iguales(Object a, Object b) {
        if (a instanceof BigDecimal x && b instanceof BigDecimal y) return x.compareTo(y) == 0;
        return Objects.equals(a, b);
    }

    static String normalizar(String texto) {
        return Normalizer.normalize(texto == null ? "" : texto, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").trim().toLowerCase();
    }
}
