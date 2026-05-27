package com.gelox.backend.services;

import com.gelox.backend.dto.*;
import com.gelox.backend.entities.Comerciante;
import com.gelox.backend.entities.TipoEvento;
import com.gelox.backend.entities.Usuario;
import com.gelox.backend.repositories.ComercianteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * Lógica de negocio para gestión de comerciantes independientes (RF34, RF35).
 */
@Service
@RequiredArgsConstructor
@Transactional
public class ComercianteService {

    private final ComercianteRepository comercianteRepo;
    private final EventoSistemaService  eventoService;

    // ══════════════════════════════════════════════════════════════════════
    // RF34 — CRUD de comerciantes
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Lista todos los comerciantes ordenados por nombre (A → Z).
     * Si se pasa {@code q}, filtra por coincidencia parcial insensible a mayúsculas.
     */
    @Transactional(readOnly = true)
    public List<ComercianteResponseDTO> listar(String q) {
        List<Comerciante> lista = (q != null && !q.isBlank())
                ? comercianteRepo.findByNombreContainingIgnoreCaseOrderByNombreAsc(q.trim())
                : comercianteRepo.findAllByOrderByNombreAsc();
        return lista.stream().map(ComercianteResponseDTO::from).toList();
    }

    /**
     * Crea un nuevo comerciante y registra el evento en bitácora.
     */
    public ComercianteResponseDTO crear(CrearComercianteRequest req, Usuario usuarioActual) {

        Comerciante comerciante = Comerciante.builder()
                .nombre(req.nombre())
                .municipio(req.municipio())
                .direccion(req.direccion())
                .telefono(req.telefono())
                .contactoEmergenciaNombre(req.contactoEmergenciaNombre())
                .contactoEmergenciaParentesco(req.contactoEmergenciaParentesco())
                .tallaUniforme(req.tallaUniforme())
                .fotoUrl(req.fotoUrl())
                .activo(true)
                .build();

        Comerciante guardado = comercianteRepo.save(comerciante);

        eventoService.registrarEvento(
                TipoEvento.NUEVO_REGISTRO,
                String.format("Nuevo comerciante registrado: %s (municipio: %s).",
                        guardado.getNombre(),
                        guardado.getMunicipio() != null ? guardado.getMunicipio() : "N/A"),
                usuarioActual.getId());

        return ComercianteResponseDTO.from(guardado);
    }

    /**
     * Actualiza todos los campos editables de un comerciante existente.
     */
    public ComercianteResponseDTO editar(UUID id, EditarComercianteRequest req, Usuario usuarioActual) {

        Comerciante comerciante = buscarOFallar(id);

        comerciante.setNombre(req.nombre());
        comerciante.setMunicipio(req.municipio());
        comerciante.setDireccion(req.direccion());
        comerciante.setTelefono(req.telefono());
        comerciante.setContactoEmergenciaNombre(req.contactoEmergenciaNombre());
        comerciante.setContactoEmergenciaParentesco(req.contactoEmergenciaParentesco());
        comerciante.setTallaUniforme(req.tallaUniforme());
        // Solo actualiza la foto si viene una nueva URL; preserva la existente si es null
        if (req.fotoUrl() != null) comerciante.setFotoUrl(req.fotoUrl());

        Comerciante actualizado = comercianteRepo.save(comerciante);

        eventoService.registrarEvento(
                TipoEvento.ACTUALIZACION_PERFIL,
                String.format("Datos del comerciante '%s' actualizados.", actualizado.getNombre()),
                usuarioActual.getId());

        return ComercianteResponseDTO.from(actualizado);
    }

    /**
     * Activa o desactiva un comerciante (RF34 — gestión de estado).
     */
    public ComercianteResponseDTO cambiarEstado(UUID id, boolean activo, Usuario usuarioActual) {

        Comerciante comerciante = buscarOFallar(id);
        boolean estadoAnterior  = Boolean.TRUE.equals(comerciante.getActivo());
        comerciante.setActivo(activo);

        Comerciante actualizado = comercianteRepo.save(comerciante);

        if (!estadoAnterior && activo) {
            eventoService.registrarEvento(
                    TipoEvento.ACTIVAR_PRODUCTO,  // reutilizamos el evento más cercano semánticamente
                    String.format("Comerciante '%s' activado.", actualizado.getNombre()),
                    usuarioActual.getId());
        } else if (estadoAnterior && !activo) {
            eventoService.registrarEvento(
                    TipoEvento.DESACTIVAR_PRODUCTO,
                    String.format("Comerciante '%s' desactivado.", actualizado.getNombre()),
                    usuarioActual.getId());
        }

        return ComercianteResponseDTO.from(actualizado);
    }

    // ══════════════════════════════════════════════════════════════════════
    // RF35 — Historial de planillas del comerciante
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Devuelve el historial de planillas cerradas de un comerciante,
     * filtrado por rango de fechas, en orden cronológico descendente.
     *
     * <p>Si no se pasan fechas, el método usa el mes en curso por defecto.</p>
     */
    @Transactional(readOnly = true)
    public List<PlanillaResumenDTO> obtenerPlanillas(UUID comercianteId,
                                                     LocalDate fechaInicio,
                                                     LocalDate fechaFin) {
        // Verificar que el comerciante exista
        buscarOFallar(comercianteId);

        // Valores por defecto: mes en curso
        LocalDate inicio = (fechaInicio != null) ? fechaInicio : LocalDate.now().withDayOfMonth(1);
        LocalDate fin    = (fechaFin    != null) ? fechaFin    : LocalDate.now();

        return comercianteRepo
                .findPlanillasResumen(comercianteId, inicio, fin)
                .stream()
                .map(PlanillaResumenDTO::fromRow)
                .toList();
    }

    // ── helpers ───────────────────────────────────────────────────────────

    private Comerciante buscarOFallar(UUID id) {
        return comercianteRepo.findById(id)
                .orElseThrow(() -> new NoSuchElementException(
                        "Comerciante no encontrado con id: " + id));
    }
}
