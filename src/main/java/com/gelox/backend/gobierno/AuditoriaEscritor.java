package com.gelox.backend.gobierno;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * RF71 — Guarda la fila de auditoría en una transacción nueva (FP-15 §2.16.3): no depende de
 * la transacción de la operación auditada ni la arrastra si falla. Separado de
 * {@link AuditoriaService} para que el proxy de @Transactional se aplique.
 */
@Component
@RequiredArgsConstructor
public class AuditoriaEscritor {

    private final AuditoriaRepository auditoriaRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void guardar(Auditoria fila) {
        auditoriaRepository.save(fila);
    }
}
