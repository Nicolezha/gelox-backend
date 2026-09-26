package com.gelox.backend.rf50;

import com.gelox.backend.TestHelper;
import com.gelox.backend.dto.PeriodoFiltroDTO;
import com.gelox.backend.dto.RentabilidadCanalDTO;
import com.gelox.backend.dto.ReporteFinancieroDTO;
import com.gelox.backend.dto.ReporteRentabilidadDTO;
import com.gelox.backend.entities.RolUsuario;
import com.gelox.backend.entities.TipoIntencionVoz;
import com.gelox.backend.entities.Usuario;
import com.gelox.backend.services.ReporteFinancieroService;
import com.gelox.backend.voz.VozContexto;
import com.gelox.backend.voz.VozPendiente;
import com.gelox.backend.voz.VozResultado;
import com.gelox.backend.voz.handlers.CierreDiaResumen;
import com.gelox.backend.voz.handlers.ConsultaFinancieraHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * El bloqueo por rol no se prueba aquí: el aspecto @RequiereRol no corre en un
 * test unitario. Eso se verifica en Postman.
 */
@ExtendWith(MockitoExtension.class)
class ConsultaFinancieraHandlerTest {

    /** Jueves. Es el "hoy" que VozService habría resuelto en Bogotá, p. ej. a las 20:00 (ya viernes en UTC). */
    private static final LocalDate HOY = LocalDate.of(2026, 9, 24);

    @Mock
    ReporteFinancieroService reporteFinancieroService;

    @Mock
    CierreDiaResumen cierreDiaResumen;

    ConsultaFinancieraHandler handler;
    Usuario usuario;

    @BeforeEach
    void setUp() {
        handler = new ConsultaFinancieraHandler(reporteFinancieroService, cierreDiaResumen);
        usuario = TestHelper.buildUsuario("uid-1", "voz@gelox-test.com", RolUsuario.ADMINISTRADOR, true);
    }

    private VozContexto ctx(String texto, LocalDate hoy) {
        return new VozContexto(texto, Map.of(), 0.9, usuario, hoy);
    }

    private static BigDecimal bd(String valor) {
        return new BigDecimal(valor);
    }

    private static ReporteFinancieroDTO reporte(String ventanilla, String rural, String comerciantes) {
        BigDecimal v = bd(ventanilla), r = bd(rural), c = bd(comerciantes);
        BigDecimal total = v.add(r).add(c);
        return new ReporteFinancieroDTO(bd("100000"), v, r, c, total, total.subtract(bd("100000")), bd("10"));
    }

    private static ReporteRentabilidadDTO rentabilidad() {
        return new ReporteRentabilidadDTO(List.of(
                new RentabilidadCanalDTO("VENTANILLA", bd("150000"), bd("90000"), bd("66.67")),
                new RentabilidadCanalDTO("RURAL", bd("80000"), bd("50000"), bd("60.00")),
                new RentabilidadCanalDTO("COMERCIANTES", bd("300000"), bd("200000"), bd("50.00"))
        ));
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> canales(VozResultado r) {
        return (List<Map<String, Object>>) r.datos().get("canales");
    }

    @ParameterizedTest(name = "\"{0}\" -> {1}..{2}")
    @CsvSource({
            "cuánto vendimos ayer,        2026-09-23, 2026-09-23, Ayer · 23 de septiembre de 2026",
            "cuánto vendimos esta semana, 2026-09-21, 2026-09-24, Esta semana · 21 al 24 de septiembre de 2026",
            "cuánto vendimos este mes,    2026-09-01, 2026-09-24, Este mes · septiembre de 2026",
            "cuánto vendimos este año,    2026-01-01, 2026-09-24, Este año · 2026",
            "cuánto vendimos hoy,         2026-09-24, 2026-09-24, Hoy · 24 de septiembre de 2026"
    })
    @DisplayName("El período sale solo de ctx.hoy(), nunca del reloj de la máquina")
    void periodoDerivadoDeHoy(String texto, LocalDate inicio, LocalDate fin, String etiqueta) {
        when(reporteFinancieroService.generarReporte(any())).thenReturn(reporte("0", "0", "0"));

        VozResultado r = handler.interpretar(ctx(texto, HOY));

        ArgumentCaptor<PeriodoFiltroDTO> captor = ArgumentCaptor.forClass(PeriodoFiltroDTO.class);
        verify(reporteFinancieroService).generarReporte(captor.capture());
        assertThat(captor.getValue()).isEqualTo(new PeriodoFiltroDTO(inicio, fin));
        assertThat(r.ok()).isTrue();
        assertThat(r.datos().get("tipo")).isEqualTo("ventas");
        assertThat(r.datos().get("periodo")).isEqualTo(etiqueta);
    }

    @Test
    @DisplayName("Mencionar un canal no filtra: misma respuesta que sin canal")
    void canalMencionadoNoFiltra() {
        when(reporteFinancieroService.generarReporte(any())).thenReturn(reporte("150000", "80000", "300000"));
        when(reporteFinancieroService.getRentabilidadPorCanal(any())).thenReturn(rentabilidad());

        VozResultado conCanal = handler.interpretar(ctx("cuánto ganamos en ventanilla hoy", HOY));
        VozResultado sinCanal = handler.interpretar(ctx("cuánto ganamos hoy", HOY));

        for (String clave : List.of("ingresosVentanilla", "ingresosRural", "ingresosComerciantes", "ingresosTotales")) {
            assertThat(conCanal.datos().get(clave)).as(clave).isEqualTo(sinCanal.datos().get(clave));
        }
        assertThat(conCanal.datos()).doesNotContainKey("canal");
        assertThat(conCanal.datos().get("ingresosTotales")).isEqualTo(bd("530000"));
    }

    @Test
    @DisplayName("Ventas: total y desglose de los tres canales, sin lista de canales")
    void ventasDesglose() {
        when(reporteFinancieroService.generarReporte(any())).thenReturn(reporte("150000", "80000", "300000"));

        VozResultado r = handler.interpretar(ctx("¿Cuánto vendimos en ventanilla hoy?", HOY));

        assertThat(r.ok()).isTrue();
        assertThat(r.datos().get("tipo")).isEqualTo("ventas");
        assertThat(r.datos().get("ingresosTotales")).isEqualTo(bd("530000"));
        assertThat(r.datos().get("ingresosVentanilla")).isEqualTo(bd("150000"));
        assertThat(r.datos().get("ingresosRural")).isEqualTo(bd("80000"));
        assertThat(r.datos().get("ingresosComerciantes")).isEqualTo(bd("300000"));
        assertThat(r.datos()).doesNotContainKey("canales");
        NumberFormat moneda = NumberFormat.getCurrencyInstance(Locale.of("es", "CO"));
        assertThat(r.textoRespuesta()).contains(moneda.format(bd("530000")));
        verify(reporteFinancieroService, never()).getRentabilidadPorCanal(any());
    }

    @Test
    @DisplayName("Ganancia: siempre los 3 canales, con nombre visible")
    void gananciaPorCanal() {
        when(reporteFinancieroService.generarReporte(any())).thenReturn(reporte("150000", "80000", "300000"));
        when(reporteFinancieroService.getRentabilidadPorCanal(any())).thenReturn(rentabilidad());

        VozResultado r = handler.interpretar(ctx("¿Cuánto ganamos en rural hoy?", HOY));

        assertThat(r.ok()).isTrue();
        assertThat(r.datos().get("tipo")).isEqualTo("ganancia");
        assertThat(canales(r)).hasSize(3);
        assertThat(canales(r)).extracting(m -> m.get("canal"))
                .containsExactly("Ventanilla", "Rural", "Comerciantes");
        assertThat(canales(r)).allSatisfy(m ->
                assertThat(m).containsOnlyKeys("canal", "totalIngresos", "totalCostos", "margen"));
        assertThat(canales(r).get(1))
                .containsEntry("totalIngresos", bd("80000"))
                .containsEntry("totalCostos", bd("50000"))
                .containsEntry("margen", bd("60.00"));
        verify(reporteFinancieroService).getRentabilidadPorCanal(new PeriodoFiltroDTO(HOY, HOY));
    }

    @Test
    @DisplayName("Sin ventas: ventas totales en $0")
    void sinVentasVentas() {
        when(reporteFinancieroService.generarReporte(any())).thenReturn(new ReporteFinancieroDTO(
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, null));

        VozResultado r = handler.interpretar(ctx("cuánto vendimos hoy", HOY));

        assertThat(r.ok()).isTrue();
        assertThat(r.datos().get("ingresosTotales")).isEqualTo(BigDecimal.ZERO);
        NumberFormat moneda = NumberFormat.getCurrencyInstance(Locale.of("es", "CO"));
        assertThat(r.textoRespuesta()).contains(moneda.format(BigDecimal.ZERO));
    }

    @Test
    @DisplayName("Sin ventas: ganancia con los 3 canales en $0, no una lista vacía")
    void sinVentasGananciaCanal() {
        when(reporteFinancieroService.generarReporte(any())).thenReturn(new ReporteFinancieroDTO(
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, null));
        when(reporteFinancieroService.getRentabilidadPorCanal(any())).thenReturn(new ReporteRentabilidadDTO(List.of(
                new RentabilidadCanalDTO("VENTANILLA", BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO),
                new RentabilidadCanalDTO("RURAL", BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO),
                new RentabilidadCanalDTO("COMERCIANTES", BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO)
        )));

        VozResultado r = handler.interpretar(ctx("cuánto ganamos en ventanilla hoy", HOY));

        assertThat(r.datos().get("utilidadNeta")).isEqualTo(BigDecimal.ZERO);
        assertThat(canales(r)).hasSize(3).allSatisfy(m -> {
            assertThat(m.get("totalIngresos")).isEqualTo(BigDecimal.ZERO);
            assertThat(m.get("totalCostos")).isEqualTo(BigDecimal.ZERO);
        });
    }

    @Test
    @DisplayName("Cierre del día: delega en CierreDiaResumen con ctx.hoy(), sin consultar reportes financieros")
    void cierreDelDia_delegaEnCierreDiaResumen() {
        VozResultado esperado = new VozResultado(true, "Cierre de hoy: ...", Map.of(), new CierreDiaResumen.Payload(HOY));
        when(cierreDiaResumen.generarResumen(HOY)).thenReturn(esperado);

        VozResultado r = handler.interpretar(ctx("Gelox, cierra el día", HOY));

        assertThat(r).isEqualTo(esperado);
        verifyNoInteractions(reporteFinancieroService);
    }

    @Test
    @DisplayName("ejecutar (confirmación por baja confianza) vuelve a consultar con el período del payload")
    void ejecutarReconsulta() {
        when(reporteFinancieroService.generarReporte(any())).thenReturn(reporte("150000", "80000", "300000"));
        VozResultado interpretado = handler.interpretar(ctx("cuánto vendimos este mes en comerciantes", HOY));

        VozPendiente pendiente = new VozPendiente(UUID.randomUUID(), usuario.getId(),
                TipoIntencionVoz.CONSULTAR_FINANZAS, interpretado.payload(), Instant.now().plusSeconds(15));
        VozResultado ejecutado = handler.ejecutar(pendiente, usuario);

        PeriodoFiltroDTO mes = new PeriodoFiltroDTO(LocalDate.of(2026, 9, 1), HOY);
        verify(reporteFinancieroService, times(2)).generarReporte(mes);
        assertThat(ejecutado.ok()).isTrue();
        assertThat(ejecutado.datos().get("tipo")).isEqualTo("ventas");
        assertThat(ejecutado.datos().get("periodo")).isEqualTo("Este mes · septiembre de 2026");
        assertThat(ejecutado.datos().get("ingresosComerciantes")).isEqualTo(bd("300000"));
        assertThat(ejecutado.datos()).isEqualTo(interpretado.datos());
    }
}
