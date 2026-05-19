package com.gelox.backend.services;

import com.gelox.backend.dto.CierreCajaDTO;
import com.gelox.backend.dto.CierreCajaResponseDTO;
import com.gelox.backend.entities.CierreCaja;
import com.gelox.backend.entities.Usuario;
import com.gelox.backend.repositories.CierreCajaRepository;
import com.gelox.backend.repositories.DashboardRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class CierreCajaService {

    private final CierreCajaRepository cierreCajaRepository;
    private final DashboardRepository dashboardRepository;

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
        return toResponse(guardado);
    }

    public CierreCajaResponseDTO obtenerPorFecha(LocalDate fecha) {
        CierreCaja cierre = cierreCajaRepository.findByFecha(fecha)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "No hay cierre de caja registrado para la fecha: " + fecha));
        return toResponse(cierre);
    }

    private CierreCajaResponseDTO toResponse(CierreCaja c) {
        boolean tieneDiferencias = c.getDiferenciaTotal().compareTo(BigDecimal.ZERO) != 0;
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
                tieneDiferencias
        );
    }
}