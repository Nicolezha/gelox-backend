package com.gelox.backend.services;

import com.gelox.backend.dto.*;
import com.gelox.backend.repositories.ReporteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
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

    private static final String[] DIAS_SEMANA = {"Lun", "Mar", "Mié", "Jue", "Vie", "Sáb", "Dom"};
    private static final String[] NOMBRES_MES = {
        "Ene", "Feb", "Mar", "Abr", "May", "Jun",
        "Jul", "Ago", "Sep", "Oct", "Nov", "Dic"
    };

    /**
     * Punto de entrada principal. Delega al builder correcto según el tipo de período.
     */
    public List<PuntoGraficaDTO> getGraficaInversionVsIngresos(PeriodoFiltroDTO periodo, TipoPeriodo tipo) {
        return switch (tipo) {
            case DIA    -> buildGraficaDia(periodo);
            case SEMANA -> buildGraficaPorDia(periodo);
            case MES    -> buildGraficaPorSemana(periodo);
            case ANIO   -> buildGraficaPorMes(periodo);
            case RANGO  -> buildGraficaRango(periodo);
        };
    }

    /** DIA — un único punto "Hoy" con el total del día */
    private List<PuntoGraficaDTO> buildGraficaDia(PeriodoFiltroDTO periodo) {
        BigDecimal inversion = reporteRepository.getTotalInversion(periodo);
        BigDecimal ingresos  = reporteRepository.getIngresosPorCanal("VENTANILLA", periodo)
                .add(reporteRepository.getIngresosPorCanal("RURAL", periodo))
                .add(reporteRepository.getIngresosComerciantesEnPeriodo(periodo));
        String etiqueta = periodo.fechaInicio().equals(LocalDate.now()) ? "Hoy" : periodo.fechaInicio().toString();
        return List.of(new PuntoGraficaDTO(etiqueta, inversion, ingresos));
    }

    /** SEMANA — un punto por día (Lun–Dom), máximo 7 puntos */
    private List<PuntoGraficaDTO> buildGraficaPorDia(PeriodoFiltroDTO periodo) {
        long totalDias = ChronoUnit.DAYS.between(periodo.fechaInicio(), periodo.fechaFin()) + 1;

        Map<Integer, BigDecimal> inversionMap = new HashMap<>();
        Map<Integer, BigDecimal> ingresosMap  = new HashMap<>();

        for (Object[] row : reporteRepository.getInversionPorDiaOffset(periodo)) {
            inversionMap.put(((Number) row[0]).intValue(), toBigDecimal(row[1]));
        }
        for (Object[] row : reporteRepository.getIngresosVentasPorDiaOffset(periodo)) {
            int offset = ((Number) row[0]).intValue();
            ingresosMap.merge(offset, toBigDecimal(row[1]), BigDecimal::add);
        }
        for (Object[] row : reporteRepository.getIngresosPllanillasPorDiaOffset(periodo)) {
            int offset = ((Number) row[0]).intValue();
            ingresosMap.merge(offset, toBigDecimal(row[1]), BigDecimal::add);
        }

        List<PuntoGraficaDTO> puntos = new ArrayList<>();
        for (int offset = 0; offset < totalDias; offset++) {
            puntos.add(new PuntoGraficaDTO(
                    DIAS_SEMANA[offset % 7],
                    inversionMap.getOrDefault(offset, BigDecimal.ZERO),
                    ingresosMap.getOrDefault(offset, BigDecimal.ZERO)
            ));
        }
        return puntos;
    }

    /** MES / RANGO — un punto por semana (Sem 1, Sem 2, …) */
    private List<PuntoGraficaDTO> buildGraficaPorSemana(PeriodoFiltroDTO periodo) {
        long totalDias = ChronoUnit.DAYS.between(periodo.fechaInicio(), periodo.fechaFin()) + 1;
        int totalSemanas = (int) Math.ceil(totalDias / 7.0);

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
            puntos.add(new PuntoGraficaDTO(
                    "Sem " + sem,
                    inversionMap.getOrDefault(sem, BigDecimal.ZERO),
                    ingresosMap.getOrDefault(sem, BigDecimal.ZERO)
            ));
        }
        return puntos;
    }

    /** ANIO — un punto por mes (Ene–Dic), solo los meses dentro del rango */
    private List<PuntoGraficaDTO> buildGraficaPorMes(PeriodoFiltroDTO periodo) {
        int mesInicio = periodo.fechaInicio().getMonthValue();
        int mesFin    = periodo.fechaFin().getMonthValue();

        Map<Integer, BigDecimal> inversionMap = new HashMap<>();
        Map<Integer, BigDecimal> ingresosMap  = new HashMap<>();

        for (Object[] row : reporteRepository.getInversionPorMes(periodo)) {
            inversionMap.put(((Number) row[0]).intValue(), toBigDecimal(row[1]));
        }
        for (Object[] row : reporteRepository.getIngresosVentasPorMes(periodo)) {
            int mes = ((Number) row[0]).intValue();
            ingresosMap.merge(mes, toBigDecimal(row[1]), BigDecimal::add);
        }
        for (Object[] row : reporteRepository.getIngresosPllanillasPorMes(periodo)) {
            int mes = ((Number) row[0]).intValue();
            ingresosMap.merge(mes, toBigDecimal(row[1]), BigDecimal::add);
        }

        List<PuntoGraficaDTO> puntos = new ArrayList<>();
        for (int mes = mesInicio; mes <= mesFin; mes++) {
            puntos.add(new PuntoGraficaDTO(
                    NOMBRES_MES[mes - 1],
                    inversionMap.getOrDefault(mes, BigDecimal.ZERO),
                    ingresosMap.getOrDefault(mes, BigDecimal.ZERO)
            ));
        }
        return puntos;
    }

    // ── Builders para RANGO inteligente ──────────────────────────────────────────────────────────

    /**
     * Enrutador: decide la granularidad según cuántos días tiene el rango.
     *   < 8 días  → barra por día  (etiqueta: dd/MM)
     *   8–28 días → barra por semana (etiqueta: Sem N)
     *   > 28 días → barra por mes  (etiqueta: Mmm AA)
     */
    private List<PuntoGraficaDTO> buildGraficaRango(PeriodoFiltroDTO periodo) {
        long dias = ChronoUnit.DAYS.between(periodo.fechaInicio(), periodo.fechaFin()) + 1;
        if (dias < 8) {
            return buildGraficaPorDiaConFecha(periodo);
        } else if (dias <= 28) {
            return buildGraficaPorSemana(periodo);
        } else {
            return buildGraficaRangoPorMes(periodo);
        }
    }

    /**
     * Barras diarias con etiqueta de fecha real (dd/MM).
     * Reutiliza los queries de offset por día ya existentes.
     */
    private List<PuntoGraficaDTO> buildGraficaPorDiaConFecha(PeriodoFiltroDTO periodo) {
        long totalDias = ChronoUnit.DAYS.between(periodo.fechaInicio(), periodo.fechaFin()) + 1;

        Map<Integer, BigDecimal> inversionMap = new HashMap<>();
        Map<Integer, BigDecimal> ingresosMap  = new HashMap<>();

        for (Object[] row : reporteRepository.getInversionPorDiaOffset(periodo)) {
            inversionMap.put(((Number) row[0]).intValue(), toBigDecimal(row[1]));
        }
        for (Object[] row : reporteRepository.getIngresosVentasPorDiaOffset(periodo)) {
            int offset = ((Number) row[0]).intValue();
            ingresosMap.merge(offset, toBigDecimal(row[1]), BigDecimal::add);
        }
        for (Object[] row : reporteRepository.getIngresosPllanillasPorDiaOffset(periodo)) {
            int offset = ((Number) row[0]).intValue();
            ingresosMap.merge(offset, toBigDecimal(row[1]), BigDecimal::add);
        }

        List<PuntoGraficaDTO> puntos = new ArrayList<>();
        for (int offset = 0; offset < totalDias; offset++) {
            LocalDate fecha = periodo.fechaInicio().plusDays(offset);
            String etiqueta = String.format("%02d/%02d", fecha.getDayOfMonth(), fecha.getMonthValue());
            puntos.add(new PuntoGraficaDTO(
                    etiqueta,
                    inversionMap.getOrDefault(offset, BigDecimal.ZERO),
                    ingresosMap.getOrDefault(offset, BigDecimal.ZERO)
            ));
        }
        return puntos;
    }

    /**
     * Barras mensuales con etiqueta "Mmm AA" (ej. "Ene 25").
     * Usa claves YYYYMM para soportar rangos que cruzan años.
     */
    private List<PuntoGraficaDTO> buildGraficaRangoPorMes(PeriodoFiltroDTO periodo) {
        Map<Integer, BigDecimal> inversionMap = new HashMap<>();
        Map<Integer, BigDecimal> ingresosMap  = new HashMap<>();

        for (Object[] row : reporteRepository.getInversionPorAnioMes(periodo)) {
            inversionMap.put(((Number) row[0]).intValue(), toBigDecimal(row[1]));
        }
        for (Object[] row : reporteRepository.getIngresosVentasPorAnioMes(periodo)) {
            int key = ((Number) row[0]).intValue();
            ingresosMap.merge(key, toBigDecimal(row[1]), BigDecimal::add);
        }
        for (Object[] row : reporteRepository.getIngresosPllanillasPorAnioMes(periodo)) {
            int key = ((Number) row[0]).intValue();
            ingresosMap.merge(key, toBigDecimal(row[1]), BigDecimal::add);
        }

        List<PuntoGraficaDTO> puntos = new ArrayList<>();
        YearMonth cursor = YearMonth.from(periodo.fechaInicio());
        YearMonth fin    = YearMonth.from(periodo.fechaFin());
        while (!cursor.isAfter(fin)) {
            int key = cursor.getYear() * 100 + cursor.getMonthValue();
            String etiqueta = NOMBRES_MES[cursor.getMonthValue() - 1]
                    + " " + String.valueOf(cursor.getYear()).substring(2);
            puntos.add(new PuntoGraficaDTO(
                    etiqueta,
                    inversionMap.getOrDefault(key, BigDecimal.ZERO),
                    ingresosMap.getOrDefault(key, BigDecimal.ZERO)
            ));
            cursor = cursor.plusMonths(1);
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

    public ReporteFinancieroCompletoDTO generarReporteCompleto(TipoPeriodo tipo,
                                                               LocalDate fechaInicio,
                                                               LocalDate fechaFin) {
        PeriodoFiltroDTO periodo = resolverPeriodo(tipo, fechaInicio, fechaFin);
        return new ReporteFinancieroCompletoDTO(
                tipo,
                periodo.fechaInicio(),
                periodo.fechaFin(),
                generarReporte(periodo),
                getGraficaInversionVsIngresos(periodo, tipo),
                getRentabilidadPorCanal(periodo)
        );
    }

    public PeriodoFiltroDTO resolverPeriodo(TipoPeriodo tipo, LocalDate fechaInicio, LocalDate fechaFin) {
        LocalDate hoy = LocalDate.now();
        return switch (tipo) {
            case DIA    -> new PeriodoFiltroDTO(hoy, hoy);
            case SEMANA -> new PeriodoFiltroDTO(hoy.with(DayOfWeek.MONDAY), hoy);
            case MES    -> new PeriodoFiltroDTO(hoy.withDayOfMonth(1), hoy);
            case ANIO   -> new PeriodoFiltroDTO(hoy.withDayOfYear(1), hoy);
            case RANGO  -> new PeriodoFiltroDTO(fechaInicio, fechaFin);
        };
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
