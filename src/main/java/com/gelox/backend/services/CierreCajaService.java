package com.gelox.backend.services;

import com.gelox.backend.dto.CierreCajaDTO;
import com.gelox.backend.dto.CierreCajaListItemDTO;
import com.gelox.backend.dto.CierreCajaPageResponseDTO;
import com.gelox.backend.dto.CierreCajaResponseDTO;
import com.gelox.backend.entities.CierreCaja;
import com.gelox.backend.entities.TipoEvento;
import com.gelox.backend.entities.Usuario;
import com.gelox.backend.repositories.CierreCajaRepository;
import com.gelox.backend.repositories.DashboardRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CierreCajaService {

    private final CierreCajaRepository cierreCajaRepository;
    private final DashboardRepository dashboardRepository;
    private final EventoSistemaService eventoSistemaService;

    @Transactional
    public CierreCajaResponseDTO registrarDineroFisico(LocalDate fecha, CierreCajaDTO dto, Usuario usuario) {
        if (cierreCajaRepository.existsByFecha(fecha)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Ya existe un cierre de caja registrado para la fecha: " + fecha);
        }

        BigDecimal calcVentanilla   = dashboardRepository.getTotalVentasPorCanal("VENTANILLA", fecha, fecha);
        BigDecimal calcRural        = dashboardRepository.getTotalVentasPorCanal("RURAL", fecha, fecha);
        BigDecimal calcComerciantes = dashboardRepository.getIngresosPlanilasDia(fecha);
        BigDecimal calcTotal        = calcVentanilla.add(calcRural).add(calcComerciantes);

        BigDecimal fisTotal = dto.montoFisicoVentanilla()
                .add(dto.montoFisicoRural())
                .add(dto.montoFisicoComerciantes());

        BigDecimal difVentanilla   = dto.montoFisicoVentanilla().subtract(calcVentanilla);
        BigDecimal difRural        = dto.montoFisicoRural().subtract(calcRural);
        BigDecimal difComerciantes = dto.montoFisicoComerciantes().subtract(calcComerciantes);
        BigDecimal difTotal        = fisTotal.subtract(calcTotal);

        CierreCaja cierre = CierreCaja.builder()
                .fecha(fecha)
                .usuario(usuario)
                .montoCalculadoVentanilla(calcVentanilla)
                .montoCalculadoRural(calcRural)
                .montoCalculadoComerciantes(calcComerciantes)
                .montoCalculadoTotal(calcTotal)
                .montoFisicoVentanilla(dto.montoFisicoVentanilla())
                .montoFisicoRural(dto.montoFisicoRural())
                .montoFisicoComerciantes(dto.montoFisicoComerciantes())
                .montoFisicoTotal(fisTotal)
                .diferenciaVentanilla(difVentanilla)
                .diferenciaRural(difRural)
                .diferenciaComerciantes(difComerciantes)
                .diferenciaTotal(difTotal)
                .build();

        CierreCaja guardado = cierreCajaRepository.save(cierre);
        eventoSistemaService.registrarEvento(
                TipoEvento.CIERRE_CAJA,
                "Cierre de caja registrado para la fecha " + fecha
                        + ". Total físico: " + fisTotal + ". Diferencia: " + difTotal,
                usuario.getId()
        );
        return toResponse(guardado);
    }

    @Transactional(readOnly = true)
    public CierreCajaResponseDTO obtenerPorFecha(LocalDate fecha) {
        CierreCaja cierre = cierreCajaRepository.findByFecha(fecha)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "No hay cierre de caja registrado para la fecha: " + fecha));
        return toResponse(cierre);
    }

    @Transactional(readOnly = true)
    public CierreCajaResponseDTO obtenerPorId(UUID id) {
        CierreCaja cierre = cierreCajaRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "No hay cierre de caja con id: " + id));
        return toResponse(cierre);
    }

    @Transactional(readOnly = true)
    public CierreCajaPageResponseDTO listar(LocalDate desde, LocalDate hasta, String estado, int page, int limit) {
        String estadoFiltro = StringUtils.hasText(estado) ? estado.trim() : null;
        Pageable pageable = PageRequest.of(page - 1, limit); // el front envía page desde 1

        Page<CierreCaja> pageResult = switch (estadoFiltro == null ? "" : estadoFiltro) {
            case "perfecto" -> cierreCajaRepository
                    .findByFechaBetweenAndDiferenciaTotalOrderByFechaDesc(desde, hasta, BigDecimal.ZERO, pageable);
            case "mayor" -> cierreCajaRepository
                    .findByFechaBetweenAndDiferenciaTotalGreaterThanOrderByFechaDesc(desde, hasta, BigDecimal.ZERO, pageable);
            case "menor" -> cierreCajaRepository
                    .findByFechaBetweenAndDiferenciaTotalLessThanOrderByFechaDesc(desde, hasta, BigDecimal.ZERO, pageable);
            default -> cierreCajaRepository
                    .findByFechaBetweenOrderByFechaDesc(desde, hasta, pageable);
        };

        List<CierreCajaListItemDTO> items = pageResult.getContent().stream()
                .map(c -> new CierreCajaListItemDTO(
                        c.getId(),
                        c.getFecha(),
                        c.getMontoCalculadoTotal(),
                        c.getMontoFisicoTotal(),
                        c.getDiferenciaTotal()))
                .toList();

        return new CierreCajaPageResponseDTO(
                items,
                pageResult.getTotalElements(),
                page,
                pageResult.getTotalPages());
    }

    private CierreCajaResponseDTO toResponse(CierreCaja c) {
        boolean tieneDiferencias = c.getDiferenciaTotal().compareTo(BigDecimal.ZERO) != 0;

        String responsable = null;
        if (c.getUsuario() != null) {
            responsable = c.getUsuario().getNombre() + " - " + c.getUsuario().getRol();
        }

        return new CierreCajaResponseDTO(
                c.getId(),
                c.getFecha(),
                c.getMontoCalculadoVentanilla(),
                c.getMontoCalculadoRural(),
                c.getMontoCalculadoComerciantes(),
                c.getMontoCalculadoTotal(),
                c.getMontoFisicoVentanilla(),
                c.getMontoFisicoRural(),
                c.getMontoFisicoComerciantes(),
                c.getMontoFisicoTotal(),
                c.getDiferenciaVentanilla(),
                c.getDiferenciaRural(),
                c.getDiferenciaComerciantes(),
                c.getDiferenciaTotal(),
                tieneDiferencias,
                c.getCreatedAt(),
                responsable
        );
    }
}
