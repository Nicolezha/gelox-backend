package com.gelox.backend.rf50;

import com.gelox.backend.dto.CierreCajaResponseDTO;
import com.gelox.backend.dto.ReporteDiarioDTO;
import com.gelox.backend.services.CierreCajaService;
import com.gelox.backend.services.ReporteDiarioService;
import com.gelox.backend.voz.VozResultado;
import com.gelox.backend.voz.handlers.CierreDiaResumen;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * RF51. El bloqueo por rol no se prueba aquí: el aspecto @RequiereRol no corre en
 * un test unitario.
 */
@ExtendWith(MockitoExtension.class)
class CierreDiaResumenTest {

    private static final LocalDate HOY = LocalDate.of(2026, 9, 24);

    @Mock
    ReporteDiarioService reporteDiarioService;

    @Mock
    CierreCajaService cierreCajaService;

    CierreDiaResumen resumen;

    @BeforeEach
    void setUp() {
        resumen = new CierreDiaResumen(reporteDiarioService, cierreCajaService);
    }

    private static BigDecimal bd(String valor) {
        return new BigDecimal(valor);
    }

    /** Total con escala 2 a propósito: el test de totales compara con isEqualByComparingTo. */
    private static ReporteDiarioDTO reporte() {
        return new ReporteDiarioDTO(HOY, bd("150000"), bd("80000"), bd("300000"), bd("530000.00"),
                10, 3, 2, 15, bd("12.50"), bd("-4.00"), bd("0.00"));
    }

    private static CierreCajaResponseDTO cierre(BigDecimal diferenciaTotal) {
        BigDecimal calculado = bd("530000");
        return new CierreCajaResponseDTO(UUID.randomUUID(), HOY,
                bd("150000"), bd("80000"), bd("300000"), calculado,
                bd("150000"), bd("80000"), bd("300000"), calculado.add(diferenciaTotal),
                BigDecimal.ZERO, BigDecimal.ZERO, diferenciaTotal, diferenciaTotal,
                diferenciaTotal.signum() != 0, LocalDateTime.of(2026, 9, 24, 19, 0), "admin@gelox-test.com");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> conciliacion(VozResultado r) {
        return (Map<String, Object>) r.datos().get("conciliacion");
    }

    @Test
    @DisplayName("Con cierre y sin diferencias: conciliación registrada, texto 'sin diferencias'")
    void conCierreSinDiferencias() {
        when(reporteDiarioService.generarReporteDiario(HOY)).thenReturn(reporte());
        when(cierreCajaService.obtenerPorFecha(HOY)).thenReturn(cierre(BigDecimal.ZERO));

        VozResultado r = resumen.generarResumen(HOY);

        assertThat(r.ok()).isTrue();
        assertThat(r.datos().get("tipo")).isEqualTo("cierre");
        assertThat(r.datos().get("periodo")).isEqualTo("Hoy · 24 de septiembre de 2026");
        assertThat(conciliacion(r))
                .containsOnlyKeys("montoCalculadoTotal", "montoFisicoTotal", "diferenciaTotal", "tieneDiferencias")
                .containsEntry("tieneDiferencias", false)
                .containsEntry("montoCalculadoTotal", bd("530000"))
                .containsEntry("montoFisicoTotal", bd("530000"));
        assertThat(r.textoRespuesta()).contains("sin diferencias");
        assertThat(r.payload()).isEqualTo(new CierreDiaResumen.Payload(HOY));
    }

    @Test
    @DisplayName("Con diferencia negativa: texto con valor absoluto, datos conservan el signo")
    void conDiferencia() {
        when(reporteDiarioService.generarReporteDiario(HOY)).thenReturn(reporte());
        when(cierreCajaService.obtenerPorFecha(HOY)).thenReturn(cierre(bd("-5000")));

        VozResultado r = resumen.generarResumen(HOY);

        assertThat(r.ok()).isTrue();
        assertThat(r.textoRespuesta()).contains("diferencia de").doesNotContain("-");
        assertThat(r.datos().get("tipo")).isEqualTo("cierre");
        assertThat(conciliacion(r)).containsEntry("tieneDiferencias", true);
        assertThat((BigDecimal) conciliacion(r).get("diferenciaTotal")).isEqualByComparingTo("-5000");
        assertThat((BigDecimal) conciliacion(r).get("montoCalculadoTotal")).isEqualByComparingTo("530000");
        assertThat((BigDecimal) conciliacion(r).get("montoFisicoTotal")).isEqualByComparingTo("525000");
    }

    @Test
    @DisplayName("Sin cierre (404): el resumen se da igual, con conciliación pendiente")
    void sinCierre() {
        when(reporteDiarioService.generarReporteDiario(HOY)).thenReturn(reporte());
        when(cierreCajaService.obtenerPorFecha(HOY)).thenThrow(new ResponseStatusException(
                HttpStatus.NOT_FOUND, "No hay cierre de caja registrado para la fecha: " + HOY));

        VozResultado r = resumen.generarResumen(HOY);

        assertThat(r.ok()).isTrue();
        assertThat(r.datos().get("tipo")).isEqualTo("cierre");
        // La clave no se agrega: VistaCierreDia.jsx hace `!conciliacion` para pintar "Pendiente".
        assertThat(r.datos().get("conciliacion")).isNull();
        assertThat(r.datos()).doesNotContainKey("conciliacion");
        assertThat(r.textoRespuesta()).contains("conciliación está pendiente");
    }

    @Test
    @DisplayName("Totales iguales a ReporteDiarioDTO.totalIngresos")
    void totalesIgualesAlReporteDiario() {
        ReporteDiarioDTO reporte = reporte();
        when(reporteDiarioService.generarReporteDiario(HOY)).thenReturn(reporte);
        when(cierreCajaService.obtenerPorFecha(HOY)).thenReturn(cierre(BigDecimal.ZERO));

        VozResultado r = resumen.generarResumen(HOY);

        assertThat((BigDecimal) r.datos().get("totalIngresos")).isEqualByComparingTo(reporte.totalIngresos());
        assertThat((BigDecimal) r.datos().get("totalIngresos")).isEqualByComparingTo("530000");
        assertThat(r.datos()).containsEntry("totalTransacciones", 15L).containsEntry("fecha", HOY);
        assertThat(r.datos())
                .containsEntry("ingresosVentanilla", reporte.ingresoVentanilla())
                .containsEntry("ingresosRural", reporte.ingresoRural())
                .containsEntry("ingresosComerciantes", reporte.ingresoComerciantes())
                .doesNotContainKeys("ingresoVentanilla", "ingresoRural", "ingresoComerciantes");
    }

    @Test
    @DisplayName("Variaciones por canal tal cual vienen de ReporteDiarioDTO")
    void variacionesDelReporteDiario() {
        ReporteDiarioDTO reporte = reporte();
        when(reporteDiarioService.generarReporteDiario(HOY)).thenReturn(reporte);
        when(cierreCajaService.obtenerPorFecha(HOY)).thenReturn(cierre(BigDecimal.ZERO));

        VozResultado r = resumen.generarResumen(HOY);

        assertThat(r.datos())
                .containsEntry("variacionVentanilla", reporte.variacionVentanilla())
                .containsEntry("variacionRural", reporte.variacionRural())
                .containsEntry("variacionComerciantes", reporte.variacionComerciantes());
        assertThat(r.datos().get("variacionRural")).isEqualTo(bd("-4.00"));
    }

    @Test
    @DisplayName("Un error distinto de 404 al consultar el cierre se propaga")
    void errorNo404SePropaga() {
        when(reporteDiarioService.generarReporteDiario(HOY)).thenReturn(reporte());
        ResponseStatusException error = new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "falla");
        when(cierreCajaService.obtenerPorFecha(HOY)).thenThrow(error);

        assertThatThrownBy(() -> resumen.generarResumen(HOY)).isSameAs(error);
    }
}
