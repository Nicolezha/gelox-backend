package com.gelox.backend.voz;

import com.gelox.backend.entities.TipoIntencionVoz;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Recuerda, en memoria, una operación a la espera de que el usuario diga
 * "confirmar" o "cancelar". Cada pendiente guarda su propio vencimiento:
 * {@value #TTL_DECISION_RECOMENDACION_SEGUNDOS} s para DECIDIR_RECOMENDACION (FP-15 §2.11.5)
 * y {@value #TTL_SEGUNDOS} s para las demás operaciones.
 * <p>
 * Es en memoria a propósito (Render corre una sola instancia): así no hace
 * falta agregar un estado "pendiente" a {@code EstadoComandoVoz} — el
 * comando solo se guarda en base de datos cuando termina.
 */
@Component
public class VozPendienteStore {

    public static final int TTL_SEGUNDOS = 45;

    public static final int TTL_DECISION_RECOMENDACION_SEGUNDOS = 15;

    /** Plazo para confirmar esta operación; es el expiraEnSegundos que recibe el frontend. */
    public static int ttlSegundos(TipoIntencionVoz intencion) {
        return intencion == TipoIntencionVoz.DECIDIR_RECOMENDACION ? TTL_DECISION_RECOMENDACION_SEGUNDOS : TTL_SEGUNDOS;
    }

    private final ConcurrentHashMap<UUID, VozPendiente> pendientes = new ConcurrentHashMap<>();

    public void put(UUID comandoId, UUID usuarioId, TipoIntencionVoz intencion, Object payload) {
        limpiarVencidos();
        pendientes.put(comandoId, new VozPendiente(
                comandoId, usuarioId, intencion, payload, Instant.now().plusSeconds(ttlSegundos(intencion))));
    }

    /** Quita y devuelve el pendiente — solo se puede tomar una vez. */
    public VozPendiente take(UUID comandoId, UUID usuarioId) {
        VozPendiente pendiente = pendientes.remove(comandoId);

        if (pendiente == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "No hay un comando pendiente de confirmación con ese id");
        }
        if (!pendiente.usuarioId().equals(usuarioId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Este comando pendiente pertenece a otro usuario");
        }
        if (Instant.now().isAfter(pendiente.expiraEn())) {
            throw new ResponseStatusException(HttpStatus.GONE, "La confirmación expiró. Repite el comando.");
        }
        return pendiente;
    }

    /** Pendiente vigente del usuario, sin quitarlo; si hay varios, el que vence más tarde. */
    public Optional<VozPendiente> peekPorUsuario(UUID usuarioId) {
        Instant ahora = Instant.now();
        return pendientes.values().stream()
                .filter(p -> p.usuarioId().equals(usuarioId))
                .filter(p -> !ahora.isAfter(p.expiraEn()))
                .max(Comparator.comparing(VozPendiente::expiraEn));
    }

    private void limpiarVencidos() {
        Instant ahora = Instant.now();
        pendientes.values().removeIf(p -> ahora.isAfter(p.expiraEn()));
        preguntas.values().removeIf(p -> ahora.isAfter(p.expiraEn()));
    }

    // ══════════════════════════════════════════════════════════════════════
    // Preguntas del asistente a la espera de respuesta (una por usuario)
    // ══════════════════════════════════════════════════════════════════════

    /** Plazo para contestar una pregunta del asistente sin repetir el comando. */
    public static final int TTL_PREGUNTA_SEGUNDOS = 90;

    /**
     * Pregunta que el asistente le hizo al usuario: con qué intención, sobre qué frase
     * original y con qué slots ya resueltos se debe volver a llamar al handler.
     */
    public record PreguntaPendiente(UUID usuarioId, TipoIntencionVoz intencion, String textoOriginal,
                                    Map<String, String> slots, Instant expiraEn) {}

    private final ConcurrentHashMap<UUID, PreguntaPendiente> preguntas = new ConcurrentHashMap<>();

    /** Una sola por usuario: la nueva reemplaza a la anterior. */
    public void putPregunta(UUID usuarioId, TipoIntencionVoz intencion, String textoOriginal,
                            Map<String, String> slots) {
        limpiarVencidos();
        preguntas.put(usuarioId, new PreguntaPendiente(usuarioId, intencion, textoOriginal,
                Map.copyOf(slots), Instant.now().plusSeconds(TTL_PREGUNTA_SEGUNDOS)));
    }

    /** Quita y devuelve la pregunta vigente del usuario, si hay. */
    public Optional<PreguntaPendiente> takePregunta(UUID usuarioId) {
        PreguntaPendiente pregunta = preguntas.remove(usuarioId);
        if (pregunta == null || Instant.now().isAfter(pregunta.expiraEn())) return Optional.empty();
        return Optional.of(pregunta);
    }

    public void descartarPregunta(UUID usuarioId) {
        preguntas.remove(usuarioId);
    }

    // ══════════════════════════════════════════════════════════════════════
    // Última recomendación de la que se habló por voz ("muéstramela completa")
    // ══════════════════════════════════════════════════════════════════════

    private final ConcurrentHashMap<UUID, UUID> ultimaRecomendacion = new ConcurrentHashMap<>();

    public void recordarRecomendacion(UUID usuarioId, UUID recomendacionId) {
        ultimaRecomendacion.put(usuarioId, recomendacionId);
    }

    public Optional<UUID> ultimaRecomendacion(UUID usuarioId) {
        return Optional.ofNullable(ultimaRecomendacion.get(usuarioId));
    }
}
