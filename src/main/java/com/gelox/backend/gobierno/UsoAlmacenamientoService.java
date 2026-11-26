package com.gelox.backend.gobierno;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * RF70 — Almacenamiento usado por una distribuidora, según archivo_almacenado.
 * Lo usan CuotaService (cuota ALMACENAMIENTO_MB) y el panel de la plataforma (tarea 185).
 */
@Service
@RequiredArgsConstructor
public class UsoAlmacenamientoService {

    private final ArchivoAlmacenadoRepository archivoRepository;

    @Transactional(readOnly = true)
    public long usadoBytes(UUID tenantId) {
        return tenantId == null ? 0L : archivoRepository.sumarBytes(tenantId);
    }
}
