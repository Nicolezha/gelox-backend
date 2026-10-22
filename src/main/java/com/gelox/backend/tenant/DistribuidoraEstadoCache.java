package com.gelox.backend.tenant;

import com.gelox.backend.entities.EstadoDistribuidora;
import com.gelox.backend.repositories.DistribuidoraRepository;
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
public class DistribuidoraEstadoCache {

    private static final long VENCIMIENTO_NANOS = Duration.ofSeconds(60).toNanos();

    private final DistribuidoraRepository distribuidoraRepository;

    private final Map<UUID, Entrada> cache = new ConcurrentHashMap<>();

    // Constructor explícito: con @RequiredArgsConstructor y el campo cache ya inicializado,
    // el analizador de Java de VS Code marca un falso error (Maven sí compila).
    public DistribuidoraEstadoCache(DistribuidoraRepository distribuidoraRepository) {
        this.distribuidoraRepository = distribuidoraRepository;
    }

    /** Estado actual de la distribuidora (con su nombre comercial), o null si no existe. */
    public Estado estado(UUID tenantId) {
        long ahora = System.nanoTime();
        Entrada entrada = cache.get(tenantId);
        if (entrada != null && ahora - entrada.cargadaEn() < VENCIMIENTO_NANOS) {
            return entrada.estado();
        }
        Estado estado = distribuidoraRepository.findById(tenantId)
                .map(d -> new Estado(d.getEstado(), d.getNombreComercial()))
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

    /** El nombre comercial viaja con el estado para el 403 de distribuidora bloqueada. */
    public record Estado(EstadoDistribuidora valor, String nombreComercial) {

        public boolean bloqueada() {
            return valor == EstadoDistribuidora.SUSPENDIDO || valor == EstadoDistribuidora.INACTIVO;
        }
    }

    private record Entrada(Estado estado, long cargadaEn) {
    }
}
