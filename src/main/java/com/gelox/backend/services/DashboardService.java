package com.gelox.backend.services;

import com.gelox.backend.dto.InversionVsIngresosDTO;
import com.gelox.backend.dto.KpiDTO;
import com.gelox.backend.dto.PeriodoFiltroDTO;
import com.gelox.backend.dto.VentasPorCanalDTO;
import com.gelox.backend.dto.Top5ComerciantesDTO;
import com.gelox.backend.repositories.DashboardRepository;
import com.gelox.backend.repositories.ReporteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final DashboardRepository dashboardRepository;
    private final ReporteRepository reporteRepository;

    public KpiDTO obtenerKPIs(LocalDate fecha) {
        BigDecimal ingresosDia   = calcularIngresos(fecha);
        BigDecimal gananciaNeta  = calcularGanancia(fecha, ingresosDia);

        LocalDate ayer           = fecha.minusDays(1);
        BigDecimal ingresosAyer  = calcularIngresos(ayer);
        BigDecimal gananciaAyer  = calcularGanancia(ayer, ingresosAyer);

        return new KpiDTO(
                ingresosDia,
                gananciaNeta,
                dashboardRepository.getComerciantesActivos(),
                dashboardRepository.getTotalComerciantes(),
                calcularVariacion(ingresosAyer, ingresosDia),
                calcularVariacion(gananciaAyer, gananciaNeta)
        );
    }

    private BigDecimal calcularIngresos(LocalDate fecha) {
        return dashboardRepository.getIngresosVentasDia(fecha)
                .add(dashboardRepository.getIngresosPlanilasDia(fecha));
    }

    private BigDecimal calcularGanancia(LocalDate fecha, BigDecimal ingresos) {
        BigDecimal costos = dashboardRepository.getCostosVentasDia(fecha)
                .add(dashboardRepository.getCostosPlanilasDia(fecha));
        return ingresos.subtract(costos);
    }

    public List<InversionVsIngresosDTO> obtenerInversionVsIngresos() {
        LocalDate hoy    = LocalDate.now();
        LocalDate inicio = hoy.minusWeeks(8);
        PeriodoFiltroDTO periodo = new PeriodoFiltroDTO(inicio, hoy);

        Map<Integer, BigDecimal> inversionMap = new HashMap<>();
        Map<Integer, BigDecimal> ingresosMap  = new HashMap<>();

        for (Object[] row : reporteRepository.getInversionPorSemana(periodo)) {
            inversionMap.put(((Number) row[0]).intValue(), toBigDecimal(row[1]));
        }
        for (Object[] row : reporteRepository.getIngresosVentasPorSemana(periodo)) {
            ingresosMap.merge(((Number) row[0]).intValue(), toBigDecimal(row[1]), BigDecimal::add);
        }
        for (Object[] row : reporteRepository.getIngresosPllanillasPorSemana(periodo)) {
            ingresosMap.merge(((Number) row[0]).intValue(), toBigDecimal(row[1]), BigDecimal::add);
        }

        List<InversionVsIngresosDTO> resultado = new ArrayList<>();
        for (int sem = 1; sem <= 8; sem++) {
            LocalDate inicioSem = inicio.plusDays((long) (sem - 1) * 7);
            resultado.add(new InversionVsIngresosDTO(
                    "Sem " + sem + " (" + inicioSem + ")",
                    inversionMap.getOrDefault(sem, BigDecimal.ZERO),
                    ingresosMap.getOrDefault(sem, BigDecimal.ZERO)
            ));
        }
        return resultado;
    }

    public Top5ComerciantesDTO obtenerTop5Comerciantes(LocalDate fechaInicio, LocalDate fechaFin) {
        List<Object[]> rows = dashboardRepository.getTop5Comerciantes(fechaInicio, fechaFin);
        List<Top5ComerciantesDTO.ComercianteIngresoDTO> lista = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            Object[] row = rows.get(i);
            lista.add(new Top5ComerciantesDTO.ComercianteIngresoDTO(
                    UUID.fromString((String) row[0]),
                    (String) row[1],
                    toBigDecimal(row[2]),
                    i + 1
            ));
        }
        return new Top5ComerciantesDTO(lista);
    }

    private BigDecimal toBigDecimal(Object value) {
        if (value == null) return BigDecimal.ZERO;
        if (value instanceof BigDecimal bd) return bd;
        return new BigDecimal(value.toString());
    }

    public VentasPorCanalDTO obtenerVentasPorCanal(LocalDate fechaInicio, LocalDate fechaFin) {
        BigDecimal ventanilla   = dashboardRepository.getTotalVentasPorCanal("VENTANILLA", fechaInicio, fechaFin);
        BigDecimal rural        = dashboardRepository.getTotalVentasPorCanal("RURAL", fechaInicio, fechaFin);
        BigDecimal comerciantes = dashboardRepository.getTotalPlanillasCerradas(fechaInicio, fechaFin);

        BigDecimal total = ventanilla.add(rural).add(comerciantes);

        if (total.compareTo(BigDecimal.ZERO) == 0) {
            return new VentasPorCanalDTO(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
        }

        return new VentasPorCanalDTO(
                ventanilla.divide(total, 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP),
                rural.divide(total, 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP),
                comerciantes.divide(total, 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP)
        );
    }

    private BigDecimal calcularVariacion(BigDecimal anterior, BigDecimal actual) {
        if (anterior == null || anterior.compareTo(BigDecimal.ZERO) == 0) return null;
        return actual.subtract(anterior)
                .divide(anterior, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(2, RoundingMode.HALF_UP);
    }
}