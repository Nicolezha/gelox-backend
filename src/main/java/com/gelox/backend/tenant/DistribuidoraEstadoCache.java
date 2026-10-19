package com.gelox.backend.tenant;

import com.gelox.backend.entities.Distribuidora;
import com.gelox.backend.entities.EstadoDistribuidora;
import com.gelox.backend.repositories.DistribuidoraRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Estado de cada distribuidora en memoria por 60 s, para no consultar la base en cada petición.
 * Un cambio de estado tarda hasta 60 s en aplicarse, salvo que se llame a invalidar().
 */
@Component
@RequiredArgsConstructor
public class DistribuidoraEstadoCache {

    private static final long VENCIMIENTO_NANOS = Duration.ofSeconds(60).toNanos();

    private final DistribuidoraRepository distribuidoraRepository;

    private final Map<UUID, Entrada> cache = new ConcurrentHashMap<>();

    /** Estado actual de la distribuidora, o null si no existe. */
    public EstadoDistribuidora obtenerEstado(UUID tenantId) {
        long ahora = System.nanoTime();
        Entrada entrada = cache.get(tenantId);
        if (entrada != null && ahora - entrada.cargadaEn() < VENCIMIENTO_NANOS) {
            return entrada.estado();
        }
        EstadoDistribuidora estado = distribuidoraRepository.findById(tenantId)
                .map(Distribuidora::getEstado)
                .orElse(null);
        if (estado != null) {
            cache.put(tenantId, new Entrada(estado, ahora));
        } else {
            cache.remove(tenantId);
        }
        return estado;
    }

    public void invalidar(UUID tenantId) {
        cache.remove(tenantId);
    }

    private record Entrada(EstadoDistribuidora estado, long cargadaEn) {
    }
}
