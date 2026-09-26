package com.gelox.backend.voz.handlers;

import com.gelox.backend.dto.PeriodoFiltroDTO;
import com.gelox.backend.dto.ReporteFinancieroDTO;
import com.gelox.backend.entities.TipoIntencionVoz;
import com.gelox.backend.entities.Usuario;
import com.gelox.backend.security.RequiereRol;
import com.gelox.backend.services.ReporteFinancieroService;
import com.gelox.backend.voz.IntencionHandler;
import com.gelox.backend.voz.NormalizadorVoz;
import com.gelox.backend.voz.VozContexto;
import com.gelox.backend.voz.VozPendiente;
import com.gelox.backend.voz.VozResultado;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.text.NumberFormat;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * RF50 — "¿cuánto ganamos en ventanilla hoy?". Narra ventas o ganancia de un
 * período a partir de {@link ReporteFinancieroService}. Siempre devuelve el
 * desglose de los tres canales: el canal mencionado no filtra (así lo espera
 * VistaFinanzas.jsx). "Hoy" sale de {@link VozContexto#hoy()}, ya resuelto en
 * America/Bogota.
 */
@Component
@RequiredArgsConstructor
public class ConsultaFinancieraHandler implements IntencionHandler {

    /** "año" queda como "ano" tras normalizar. */
    private static final Pattern ANIO_PATTERN = Pattern.compile("\\bano\\b");

    private static final DateTimeFormatter FORMATO_FECHA_LARGA =
            DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy", Locale.of("es", "CO"));
    private static final DateTimeFormatter FORMATO_MES_LARGO =
            DateTimeFormatter.ofPattern("MMMM 'de' yyyy", Locale.of("es", "CO"));

    private final ReporteFinancieroService reporteFinancieroService;
    private final CierreDiaResumen cierreDiaResumen;

    /** {@code tipoConsulta} es "ventas" o "ganancia" (en minúsculas, como lo lee el frontend). */
    private record Payload(PeriodoFiltroDTO periodo, Periodo periodoEnum, String tipoConsulta) {}

    private enum Periodo { HOY, AYER, SEMANA, MES, ANIO }

    @Override
    public TipoIntencionVoz tipo() {
        return TipoIntencionVoz.CONSULTAR_FINANZAS;
    }

    @Override
    public boolean requiereConfirmacion() {
        return false;
    }

    @Override
    @RequiereRol("ADMINISTRADOR")
    public VozResultado interpretar(VozContexto ctx) {
        String texto = NormalizadorVoz.normalizar(ctx.texto());

        // RF51: el cierre del día lo narra CierreDiaResumen.
        if (texto.contains("cierra el dia") || texto.contains("cierre del dia") || texto.contains("resumen del dia")) {
            return cierreDiaResumen.generarResumen(ctx.hoy());
        }

        Periodo p = detectarPeriodo(texto);
        PeriodoFiltroDTO periodo = periodoFiltro(p, ctx.hoy());
        String tipoConsulta = detectarTipo(texto);

        return construirRespuesta(periodo, p, tipoConsulta);
    }

    /**
     * Solo llega aquí si VozService pidió confirmación por baja confianza (RNF-5).
     * Vuelve a consultar el reporte para narrar cifras frescas.
     */
    @Override
    @RequiereRol("ADMINISTRADOR")
    public VozResultado ejecutar(VozPendiente pendiente, Usuario usuario) {
        if (pendiente.payload() instanceof CierreDiaResumen.Payload cierrePayload) {
            return cierreDiaResumen.generarResumen(cierrePayload.hoy());
        }
        Payload payload = (Payload) pendiente.payload();
        return construirRespuesta(payload.periodo(), payload.periodoEnum(), payload.tipoConsulta());
    }

    private VozResultado construirRespuesta(PeriodoFiltroDTO periodo, Periodo p, String tipoConsulta) {
        ReporteFinancieroDTO reporte = reporteFinancieroService.generarReporte(periodo);
        NumberFormat moneda = NumberFormat.getCurrencyInstance(Locale.of("es", "CO"));
        String etiquetaLarga = etiqueta(p, periodo);
        String etiquetaCorta = etiquetaCorta(p);

        // LinkedHashMap y no Map.of: margenGanancia puede ser null.
        Map<String, Object> datos = new LinkedHashMap<>();
        datos.put("tipo", tipoConsulta);
        datos.put("periodo", etiquetaLarga);
        datos.put("ingresosVentanilla", reporte.ingresosVentanilla());
        datos.put("ingresosRural", reporte.ingresosRural());
        datos.put("ingresosComerciantes", reporte.ingresosComerciantes());
        datos.put("ingresosTotales", reporte.ingresosTotales());
        datos.put("utilidadNeta", reporte.utilidadNeta());
        datos.put("margenGanancia", reporte.margenGanancia());

        String texto;
        if ("ganancia".equals(tipoConsulta)) {
            List<Map<String, Object>> canalesVista = reporteFinancieroService.getRentabilidadPorCanal(periodo)
                    .canales().stream()
                    .map(c -> {
                        Map<String, Object> m = new LinkedHashMap<>();
                        m.put("canal", nombreVisible(c.canal()));
                        m.put("totalIngresos", c.totalIngresos());
                        m.put("totalCostos", c.totalCostos());
                        m.put("margen", c.margen());
                        return m;
                    })
                    .toList();
            datos.put("canales", canalesVista);
            texto = "La utilidad neta " + etiquetaCorta + " fue de " + moneda.format(reporte.utilidadNeta())
                    + (reporte.margenGanancia() != null
                        ? " con un margen de ganancia del " + reporte.margenGanancia() + " %."
                        : ".");
        } else {
            texto = "Las ventas " + etiquetaCorta + " suman " + moneda.format(reporte.ingresosTotales()) + ".";
        }

        return new VozResultado(true, texto, datos, new Payload(periodo, p, tipoConsulta));
    }

    private Periodo detectarPeriodo(String texto) {
        if (texto.contains("ayer")) return Periodo.AYER;
        if (texto.contains("semana")) return Periodo.SEMANA;
        if (texto.contains("mes")) return Periodo.MES;
        if (ANIO_PATTERN.matcher(texto).find()) return Periodo.ANIO;
        return Periodo.HOY;
    }

    /** SEMANA va de lunes a hoy, igual que ReporteFinancieroService.resolverPeriodo. */
    private PeriodoFiltroDTO periodoFiltro(Periodo p, LocalDate hoy) {
        return switch (p) {
            case HOY -> new PeriodoFiltroDTO(hoy, hoy);
            case AYER -> new PeriodoFiltroDTO(hoy.minusDays(1), hoy.minusDays(1));
            case SEMANA -> new PeriodoFiltroDTO(hoy.with(DayOfWeek.MONDAY), hoy);
            case MES -> new PeriodoFiltroDTO(hoy.withDayOfMonth(1), hoy);
            case ANIO -> new PeriodoFiltroDTO(hoy.withDayOfYear(1), hoy);
        };
    }

    /** Para datos.periodo: se muestra tal cual en pantalla ("Hoy · 21 de septiembre de 2026"). */
    private String etiqueta(Periodo p, PeriodoFiltroDTO periodo) {
        return switch (p) {
            case HOY -> "Hoy · " + periodo.fechaFin().format(FORMATO_FECHA_LARGA);
            case AYER -> "Ayer · " + periodo.fechaFin().format(FORMATO_FECHA_LARGA);
            case SEMANA -> "Esta semana · " + periodo.fechaInicio().getDayOfMonth() + " al "
                    + periodo.fechaFin().format(FORMATO_FECHA_LARGA);
            case MES -> "Este mes · " + periodo.fechaInicio().format(FORMATO_MES_LARGO);
            case ANIO -> "Este año · " + periodo.fechaInicio().getYear();
        };
    }

    /** Para el texto narrado por voz: corto, en minúscula y sin fecha. */
    private String etiquetaCorta(Periodo p) {
        return switch (p) {
            case HOY -> "hoy";
            case AYER -> "ayer";
            case SEMANA -> "esta semana";
            case MES -> "este mes";
            case ANIO -> "este año";
        };
    }

    private String nombreVisible(String canal) {
        return switch (canal) {
            case "VENTANILLA" -> "Ventanilla";
            case "RURAL" -> "Rural";
            case "COMERCIANTES" -> "Comerciantes";
            default -> canal;
        };
    }

    /** "vendimos" o "ingresos" caen en "ventas". */
    private String detectarTipo(String texto) {
        return (texto.contains("ganamos") || texto.contains("ganancia")) ? "ganancia" : "ventas";
    }
}
