package com.gelox.backend.services;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gelox.backend.dto.DetalleProductoDTO;
import com.gelox.backend.dto.ReporteDiarioDTO;
import com.gelox.backend.dto.TransaccionDiaDTO;
import com.gelox.backend.dto.TransaccionesDiaPageDTO;
import com.gelox.backend.repositories.ReporteDiarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReporteDiarioService {

    private final ReporteDiarioRepository reporteDiarioRepository;
    private final ObjectMapper objectMapper;

    // ══════════════════════════════════════════════════════════════════════════
    // RF40 — Reporte consolidado del día
    // ══════════════════════════════════════════════════════════════════════════

    public ReporteDiarioDTO generarReporteDiario(LocalDate fecha) {
        BigDecimal ingVentanilla   = reporteDiarioRepository.getIngresosVentanillaDia(fecha);
        BigDecimal ingRural        = reporteDiarioRepository.getIngresosRuralDia(fecha);
        BigDecimal ingComerciantes = reporteDiarioRepository.getIngresosComerciantesDia(fecha);
        BigDecimal totalIngresos   = ingVentanilla.add(ingRural).add(ingComerciantes);

        long txVentanilla   = reporteDiarioRepository.countVentanillaDia(fecha);
        long txRural        = reporteDiarioRepository.countRuralDia(fecha);
        long txComerciantes = reporteDiarioRepository.countPlanillasDia(fecha);

        // Variación respecto al día anterior
        LocalDate ayer = fecha.minusDays(1);
        BigDecimal ayerVentanilla   = reporteDiarioRepository.getIngresosVentanillaDia(ayer);
        BigDecimal ayerRural        = reporteDiarioRepository.getIngresosRuralDia(ayer);
        BigDecimal ayerComerciantes = reporteDiarioRepository.getIngresosComerciantesDia(ayer);

        return new ReporteDiarioDTO(
                fecha,
                ingVentanilla,
                ingRural,
                ingComerciantes,
                totalIngresos,
                txVentanilla,
                txRural,
                txComerciantes,
                txVentanilla + txRural + txComerciantes,
                calcularVariacion(ingVentanilla, ayerVentanilla),
                calcularVariacion(ingRural, ayerRural),
                calcularVariacion(ingComerciantes, ayerComerciantes)
        );
    }

    // ══════════════════════════════════════════════════════════════════════════
    // RF41 — Lista paginada de transacciones del día
    // ══════════════════════════════════════════════════════════════════════════

    public TransaccionesDiaPageDTO listarTransacciones(
            LocalDate fecha, String canal, int page, int limit) {

        if (page < 1) page = 1;
        if (limit < 1 || limit > 100) limit = 20;
        int offset = (page - 1) * limit;

        List<Object[]> rows;
        long total;

        if (canal == null || canal.isBlank()) {
            rows  = reporteDiarioRepository.getTodasTransacciones(fecha, offset, limit);
            total = reporteDiarioRepository.countVentanillaDia(fecha)
                  + reporteDiarioRepository.countRuralDia(fecha)
                  + reporteDiarioRepository.countPlanillasDia(fecha);
        } else {
            rows = switch (canal.trim().toUpperCase()) {
                case "VENTANILLA" -> reporteDiarioRepository.getTransaccionesVentanilla(fecha, offset, limit);
                case "RURAL"      -> reporteDiarioRepository.getTransaccionesRural(fecha, offset, limit);
                case "PLANILLA"   -> reporteDiarioRepository.getTransaccionesPlanilla(fecha, offset, limit);
                default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Canal inválido. Use: VENTANILLA, RURAL o PLANILLA");
            };
            total = switch (canal.trim().toUpperCase()) {
                case "VENTANILLA" -> reporteDiarioRepository.countVentanillaDia(fecha);
                case "RURAL"      -> reporteDiarioRepository.countRuralDia(fecha);
                case "PLANILLA"   -> reporteDiarioRepository.countPlanillasDia(fecha);
                default           -> 0L;
            };
        }

        List<TransaccionDiaDTO> transacciones = rows.stream()
                .map(this::toTransaccionDTO)
                .toList();

        int totalPages = total == 0 ? 1 : (int) Math.ceil((double) total / limit);

        return new TransaccionesDiaPageDTO(transacciones, total, page, totalPages);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Mapeo interno
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Columnas esperadas (definidas en ReporteDiarioRepository):
     *   [0] id                  (String)
     *   [1] tipo                (String)
     *   [2] cliente             (String, nullable)
     *   [3] detalles_productos  (String/JSON)
     *   [4] total               (Number → BigDecimal)
     *   [5] hora                (Timestamp, nullable)
     *   [6] metodo_pago         (String, nullable)
     */
    private TransaccionDiaDTO toTransaccionDTO(Object[] row) {
        Timestamp ts = (Timestamp) row[5];
        LocalDateTime hora = ts != null ? ts.toLocalDateTime() : null;

        return new TransaccionDiaDTO(
                (String) row[0],
                (String) row[1],
                (String) row[2],
                parseDetalles(row[3]),
                toBigDecimal(row[4]),
                hora,
                (String) row[6]
        );
    }

    private List<DetalleProductoDTO> parseDetalles(Object raw) {
        if (raw == null) return List.of();
        try {
            return objectMapper.readValue(
                    raw.toString(),
                    new TypeReference<List<DetalleProductoDTO>>() {});
        } catch (Exception e) {
            return List.of();
        }
    }

    private BigDecimal calcularVariacion(BigDecimal hoy, BigDecimal ayer) {
        if (ayer == null || ayer.compareTo(BigDecimal.ZERO) == 0) return null;
        return hoy.subtract(ayer)
                .divide(ayer, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(1, RoundingMode.HALF_UP);
    }

    private BigDecimal toBigDecimal(Object value) {
        if (value == null) return BigDecimal.ZERO;
        if (value instanceof BigDecimal bd) return bd;
        return new BigDecimal(value.toString());
    }
}
