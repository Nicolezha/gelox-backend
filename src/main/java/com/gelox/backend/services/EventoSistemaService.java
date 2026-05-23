package com.gelox.backend.services;

import com.gelox.backend.dto.EventoSistemaDTO;
import com.gelox.backend.entities.EventoSistema;
import com.gelox.backend.entities.TipoEvento;
import com.gelox.backend.repositories.EventoSistemaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneOffset;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class EventoSistemaService {

    private final EventoSistemaRepository repository;

    public void registrarEvento(TipoEvento tipo, String descripcion, UUID usuarioId) {
        EventoSistema evento = EventoSistema.builder()
                .tipo(tipo)
                .descripcion(descripcion)
                .usuarioId(usuarioId)
                .build();
        repository.save(evento);
        // Mantener solo los 10 eventos más recientes para evitar acumulación
        repository.eliminarEventosAntiguos();
    }

    /**
     * Al arrancar la aplicación, limpia de una vez todos los registros acumulados
     * dejando únicamente los 10 más recientes en la tabla evento_sistema.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void limpiarEventosAlInicio() {
        repository.eliminarEventosAntiguos();
    }

    @Transactional(readOnly = true)
    public Page<EventoSistemaDTO> obtenerEventos(Pageable pageable) {
        return repository.findAllByOrderByFechaDesc(pageable)
                .map(this::toDTO);
    }

    private EventoSistemaDTO toDTO(EventoSistema e) {
        return new EventoSistemaDTO(
                e.getId(),
                e.getTipo(),
                e.getDescripcion(),
                e.getUsuarioId(),
                e.getFecha().atOffset(ZoneOffset.UTC)
        );
    }
}
