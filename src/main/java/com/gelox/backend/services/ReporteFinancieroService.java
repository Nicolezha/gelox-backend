package com.gelox.backend.services;

import com.gelox.backend.dto.*;
import com.gelox.backend.repositories.ReporteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
@RequiredArgsConstructor
public class ReporteFinancieroService {

    private final ReporteRepository reporteRepository;

    public ReporteFinancieroDTO generarReporte(PeriodoFiltroDTO periodo) {
        BigDecimal inversion       = reporteRepository.getTotalInversion(periodo);
        BigDecimal ingVentanilla   = reporteRepository.getIngresosPorCanal("VENTANILLA", periodo);
        BigDecimal ingRural        = reporteRepository.getIngresosPorCanal("RURAL", periodo);
        BigDecimal ingComerciantes = reporteRepository.getIngresosComerciantesEnPeriodo(periodo);
        BigDecimal ingresosTotales = ingVentanilla.add(ingRural).add(ingComerciantes);
        BigDecimal utilidad        = ingresosTotales.subtract(inversion);
        BigDecimal margen          = inversion.compareTo(BigDecimal.ZERO) == 0
                ? null
                : utilidad.divide(inversion, 4, RoundingMode.HALF_UP)
                          .multiply(BigDecimal.valueOf(100));

        return new ReporteFinancieroDTO(
                inversion, ingVentanilla, ingRural,
                ingComerciantes, ingresosTotales, utilidad, margen
        );
    }

    public List<PuntoGraficaDTO> getGraficaInversionVsIngresos(PeriodoFiltroDTO periodo) {
        long totalDias = ChronoUnit.DAYS.between(periodo.fechaInicio(), periodo.fechaFin()) + 1;
        int totalSemanas = (int) Math.ceil(totalDias / 7.0);

        // Mapas semana → valor (semana empieza en 1)
        Map<Integer, BigDecimal> inversionMap = new HashMap<>();
        Map<Integer, BigDecimal> ingresosMap  = new HashMap<>();

        for (Object[] row : reporteRepository.getInversionPorSemana(periodo)) {
            inversionMap.put(((Number) row[0]).intValue(), toBigDecimal(row[1]));
        }
        for (Object[] row : reporteRepository.getIngresosVentasPorSemana(periodo)) {
            int sem = ((Number) row[0]).intValue();
            ingresosMap.merge(sem, toBigDecimal(row[1]), BigDecimal::add);
        }
        for (Object[] row : reporteRepository.getIngresosPllanillasPorSemana(periodo)) {
            int sem = ((Number) row[0]).intValue();
            ingresosMap.merge(sem, toBigDecimal(row[1]), BigDecimal::add);
        }

        List<PuntoGraficaDTO> puntos = new ArrayList<>();
        for (int sem = 1; sem <= totalSemanas; sem++) {
            LocalDate inicioSem = periodo.fechaInicio().plusDays((long) (sem - 1) * 7);
            puntos.add(new PuntoGraficaDTO(
                    "Sem " + sem + " (" + inicioSem + ")",
                    inversionMap.getOrDefault(sem, BigDecimal.ZERO),
                    ingresosMap.getOrDefault(sem, BigDecimal.ZERO)
            ));
        }
        return puntos;
    }

    public ReporteRentabilidadDTO getRentabilidadPorCanal(PeriodoFiltroDTO periodo) {
        Map<String, BigDecimal[]> canalData = new HashMap<>();
        canalData.put("VENTANILLA", new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
        canalData.put("RURAL",      new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});

        for (Object[] row : reporteRepository.getRentabilidadVentanillaRural(periodo)) {
            String canal    = (String) row[0];
            BigDecimal ing  = toBigDecimal(row[1]);
            BigDecimal cost = toBigDecimal(row[2]);
            canalData.put(canal, new BigDecimal[]{ing, cost});
        }

        BigDecimal ingComerciantes  = reporteRepository.getIngresosComerciantesEnPeriodo(periodo);
        BigDecimal costComerciantes = reporteRepository.getCostosComerciantesEnPeriodo(periodo);

        List<RentabilidadCanalDTO> canales = new ArrayList<>();
        for (String canal : List.of("VENTANILLA", "RURAL")) {
            BigDecimal[] vals = canalData.get(canal);
            canales.add(new RentabilidadCanalDTO(canal, vals[0], vals[1], calcularMargen(vals[0], vals[1])));
        }
        canales.add(new RentabilidadCanalDTO(
                "COMERCIANTES",
                ingComerciantes,
                costComerciantes,
                calcularMargen(ingComerciantes, costComerciantes)
        ));

        return new ReporteRentabilidadDTO(canales);
    }

    private BigDecimal calcularMargen(BigDecimal ingresos, BigDecimal costos) {
        if (ingresos.compareTo(BigDecimal.ZERO) == 0) return BigDecimal.ZERO;
        return ingresos.subtract(costos)
                       .divide(ingresos, 4, RoundingMode.HALF_UP)
                       .multiply(BigDecimal.valueOf(100));
    }

    private BigDecimal toBigDecimal(Object value) {
        if (value == null) return BigDecimal.ZERO;
        if (value instanceof BigDecimal bd) return bd;
        return new BigDecimal(value.toString());
    }
}
