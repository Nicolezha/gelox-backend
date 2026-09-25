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

    ConsultaFinancieraHandler handler;
    Usuario usuario;

    @BeforeEach
    void setUp() {
        handler = new ConsultaFinancieraHandler(reporteFinancieroService);
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

    @ParameterizedTest(name = "\"{0}\" -> {1}..{2}")
    @CsvSource({
            "cuánto vendimos ayer,        2026-09-23, 2026-09-23",
            "cuánto vendimos esta semana, 2026-09-21, 2026-09-24",
            "cuánto vendimos este mes,    2026-09-01, 2026-09-24",
            "cuánto vendimos este año,    2026-01-01, 2026-09-24",
            "cuánto vendimos hoy,         2026-09-24, 2026-09-24"
    })
    @DisplayName("El período sale solo de ctx.hoy(), nunca del reloj de la máquina")
    void periodoDerivadoDeHoy(String texto, LocalDate inicio, LocalDate fin) {
        when(reporteFinancieroService.generarReporte(any())).thenReturn(reporte("0", "0", "0"));

        VozResultado r = handler.interpretar(ctx(texto, HOY));

        ArgumentCaptor<PeriodoFiltroDTO> captor = ArgumentCaptor.forClass(PeriodoFiltroDTO.class);
        verify(reporteFinancieroService).generarReporte(captor.capture());
        assertThat(captor.getValue()).isEqualTo(new PeriodoFiltroDTO(inicio, fin));
        assertThat(r.ok()).isTrue();
        assertThat(r.datos().get("tipo")).isEqualTo("VENTAS");
    }

    @Test
    @DisplayName("Ventas filtradas por canal: narra ingresos del canal, no el total")
    void ventasPorCanal() {
        when(reporteFinancieroService.generarReporte(any())).thenReturn(reporte("150000", "80000", "300000"));

        VozResultado r = handler.interpretar(ctx("¿Cuánto vendimos en ventanilla hoy?", HOY));

        assertThat(r.ok()).isTrue();
        assertThat(r.datos().get("canal")).isEqualTo("VENTANILLA");
        assertThat(r.datos().get("ingresos")).isEqualTo(bd("150000"));
        NumberFormat moneda = NumberFormat.getCurrencyInstance(Locale.of("es", "CO"));
        assertThat(r.textoRespuesta()).contains(moneda.format(bd("150000")));
    }

    @Test
    @DisplayName("Ventas sin canal: total y desglose, con canal null en datos")
    void ventasSinCanal() {
        when(reporteFinancieroService.generarReporte(any())).thenReturn(reporte("150000", "80000", "300000"));

        VozResultado r = handler.interpretar(ctx("cuánto vendimos hoy", HOY));

        assertThat(r.datos()).containsEntry("canal", null);
        assertThat(r.datos().get("ingresos")).isEqualTo(bd("530000"));
        assertThat(r.datos().get("ingresosRural")).isEqualTo(bd("80000"));
    }

    @Test
    @DisplayName("Ganancia por canal: ingresos - costos del canal pedido")
    void gananciaPorCanal() {
        when(reporteFinancieroService.getRentabilidadPorCanal(any())).thenReturn(new ReporteRentabilidadDTO(List.of(
                new RentabilidadCanalDTO("VENTANILLA", bd("150000"), bd("90000"), bd("66.67")),
                new RentabilidadCanalDTO("RURAL", bd("80000"), bd("50000"), bd("60.00")),
                new RentabilidadCanalDTO("COMERCIANTES", bd("300000"), bd("200000"), bd("50.00"))
        )));

        VozResultado r = handler.interpretar(ctx("¿Cuánto ganamos en rural hoy?", HOY));

        assertThat(r.ok()).isTrue();
        assertThat(r.datos().get("canal")).isEqualTo("RURAL");
        assertThat(r.datos().get("tipo")).isEqualTo("GANANCIA");
        assertThat(r.datos().get("ganancia")).isEqualTo(bd("30000"));
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
        assertThat(r.datos().get("ingresos")).isEqualTo(BigDecimal.ZERO);
        NumberFormat moneda = NumberFormat.getCurrencyInstance(Locale.of("es", "CO"));
        assertThat(r.textoRespuesta()).contains(moneda.format(BigDecimal.ZERO));
    }

    @Test
    @DisplayName("Sin ventas: ganancia de un canal en $0")
    void sinVentasGananciaCanal() {
        when(reporteFinancieroService.getRentabilidadPorCanal(any())).thenReturn(new ReporteRentabilidadDTO(List.of(
                new RentabilidadCanalDTO("VENTANILLA", BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO),
                new RentabilidadCanalDTO("RURAL", BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO),
                new RentabilidadCanalDTO("COMERCIANTES", BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO)
        )));

        VozResultado r = handler.interpretar(ctx("cuánto ganamos en ventanilla hoy", HOY));

        assertThat(r.datos().get("ganancia")).isEqualTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("Cierre del día: todavía no disponible, sin consultar reportes")
    void cierreDelDiaNoDisponible() {
        VozResultado r = handler.interpretar(ctx("Gelox, cierra el día", HOY));

        assertThat(r.ok()).isFalse();
        assertThat(r.textoRespuesta()).isEqualTo("El cierre del día por voz todavía no está disponible.");
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
        assertThat(ejecutado.datos().get("canal")).isEqualTo("COMERCIANTES");
        assertThat(ejecutado.datos().get("ingresos")).isEqualTo(bd("300000"));
    }
}
