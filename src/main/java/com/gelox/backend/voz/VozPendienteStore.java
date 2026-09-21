package com.gelox.backend.voz;

import com.gelox.backend.entities.TipoIntencionVoz;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Recuerda, en memoria, una operación a la espera de que el usuario diga
 * "confirmar" o "cancelar", durante {@value #TTL_SEGUNDOS} segundos.
 * <p>
 * Es en memoria a propósito (Render corre una sola instancia): así no hace
 * falta agregar un estado "pendiente" a {@code EstadoComandoVoz} — el
 * comando solo se guarda en base de datos cuando termina.
 */
@Component
public class VozPendienteStore {

    public static final int TTL_SEGUNDOS = 15;

    private final ConcurrentHashMap<UUID, VozPendiente> pendientes = new ConcurrentHashMap<>();

    public void put(UUID comandoId, UUID usuarioId, TipoIntencionVoz intencion, Object payload) {
        limpiarVencidos();
        pendientes.put(comandoId, new VozPendiente(
                comandoId, usuarioId, intencion, payload, Instant.now().plusSeconds(TTL_SEGUNDOS)));
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

    private void limpiarVencidos() {
        Instant ahora = Instant.now();
        pendientes.values().removeIf(p -> ahora.isAfter(p.expiraEn()));
    }
}
