package com.gelox.backend.services;

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
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReporteDiarioService {

    private final ReporteDiarioRepository reporteDiarioRepository;

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

        return new ReporteDiarioDTO(
                fecha,
                ingVentanilla,
                ingRural,
                ingComerciantes,
                totalIngresos,
                txVentanilla,
                txRural,
                txComerciantes,
                txVentanilla + txRural + txComerciantes
        );
    }

    // ══════════════════════════════════════════════════════════════════════════
    // RF41 — Lista paginada de transacciones del día
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * @param canal  VENTANILLA | RURAL | PLANILLA | null (todos)
     * @param page   página comenzando en 1
     * @param limit  registros por página
     */
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
            rows  = switch (canal.trim().toUpperCase()) {
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
     * Mapea una fila de Object[] al DTO.
     * Orden de columnas (definido en ReporteDiarioRepository):
     *   [0] id       (String)
     *   [1] tipo     (String)
     *   [2] cliente  (String, nullable)
     *   [3] productos(String)
     *   [4] cantidad (Number → int)
     *   [5] total    (Number → BigDecimal)
     *   [6] hora     (Timestamp, nullable)
     */
    private TransaccionDiaDTO toTransaccionDTO(Object[] row) {
        Timestamp ts = (Timestamp) row[6];
        LocalDateTime hora = ts != null ? ts.toLocalDateTime() : null;

        return new TransaccionDiaDTO(
                (String) row[0],
                (String) row[1],
                (String) row[2],
                (String) row[3],
                row[4] != null ? ((Number) row[4]).intValue() : 0,
                toBigDecimal(row[5]),
                hora
        );
    }

    private BigDecimal toBigDecimal(Object value) {
        if (value == null) return BigDecimal.ZERO;
        if (value instanceof BigDecimal bd) return bd;
        return new BigDecimal(value.toString());
    }
}