package com.gelox.backend.voz.handlers;

import com.gelox.backend.dto.PeriodoFiltroDTO;
import com.gelox.backend.dto.RentabilidadCanalDTO;
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

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * RF50 — "¿cuánto ganamos en ventanilla hoy?". Narra ventas o ganancia de un
 * período y, opcionalmente, de un canal, a partir de {@link ReporteFinancieroService}.
 * "Hoy" sale de {@link VozContexto#hoy()}, ya resuelto en America/Bogota.
 */
@Component
@RequiredArgsConstructor
public class ConsultaFinancieraHandler implements IntencionHandler {

    /** "año" queda como "ano" tras normalizar. */
    private static final Pattern ANIO_PATTERN = Pattern.compile("\\bano\\b");

    private final ReporteFinancieroService reporteFinancieroService;

    /** {@code canal} null = todos los canales; {@code tipoConsulta} es "VENTAS" o "GANANCIA". */
    private record Payload(PeriodoFiltroDTO periodo, String etiquetaPeriodo, String canal, String tipoConsulta) {}

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

        // El cierre del día por voz lo implementa T45-BE3.
        if (texto.contains("cierra el dia") || texto.contains("cierre del dia") || texto.contains("resumen del dia")) {
            return new VozResultado(false, "El cierre del día por voz todavía no está disponible.", Map.of(), null);
        }

        Periodo p = detectarPeriodo(texto);
        return construirRespuesta(periodoFiltro(p, ctx.hoy()), etiqueta(p), detectarCanal(texto), detectarTipo(texto));
    }

    /**
     * Solo llega aquí si VozService pidió confirmación por baja confianza (RNF-5).
     * Vuelve a consultar el reporte para narrar cifras frescas.
     */
    @Override
    @RequiereRol("ADMINISTRADOR")
    public VozResultado ejecutar(VozPendiente pendiente, Usuario usuario) {
        Payload payload = (Payload) pendiente.payload();
        return construirRespuesta(payload.periodo(), payload.etiquetaPeriodo(), payload.canal(), payload.tipoConsulta());
    }

    private VozResultado construirRespuesta(PeriodoFiltroDTO periodo, String etiquetaPeriodo,
                                            String canal, String tipoConsulta) {
        NumberFormat moneda = NumberFormat.getCurrencyInstance(Locale.of("es", "CO"));
        // LinkedHashMap y no Map.of: canal puede ser null.
        Map<String, Object> datos = new LinkedHashMap<>();
        datos.put("periodo", etiquetaPeriodo);
        datos.put("canal", canal);
        datos.put("tipo", tipoConsulta);
        String texto;

        if ("GANANCIA".equals(tipoConsulta)) {
            if (canal != null) {
                List<RentabilidadCanalDTO> canales = reporteFinancieroService.getRentabilidadPorCanal(periodo).canales();
                RentabilidadCanalDTO delCanal = canales.stream()
                        .filter(c -> c.canal().equals(canal))
                        .findFirst()
                        // getRentabilidadPorCanal siempre trae los 3 canales.
                        .orElseThrow(() -> new IllegalStateException("Canal no encontrado en rentabilidad: " + canal));
                BigDecimal ganancia = delCanal.totalIngresos().subtract(delCanal.totalCostos());
                datos.put("ingresos", delCanal.totalIngresos());
                datos.put("costos", delCanal.totalCostos());
                datos.put("ganancia", ganancia);
                texto = "Ganancia de " + canal.toLowerCase(Locale.ROOT) + " " + etiquetaPeriodo + ": "
                        + moneda.format(ganancia) + ".";
            } else {
                ReporteFinancieroDTO reporte = reporteFinancieroService.generarReporte(periodo);
                datos.put("utilidadNeta", reporte.utilidadNeta());
                datos.put("margenGanancia", reporte.margenGanancia());
                texto = "Ganancia " + etiquetaPeriodo + ": " + moneda.format(reporte.utilidadNeta()) + ".";
            }
        } else {
            ReporteFinancieroDTO reporte = reporteFinancieroService.generarReporte(periodo);
            BigDecimal ingresos = switch (canal) {
                case "VENTANILLA" -> reporte.ingresosVentanilla();
                case "RURAL" -> reporte.ingresosRural();
                case "COMERCIANTES" -> reporte.ingresosComerciantes();
                case null, default -> reporte.ingresosTotales();
            };
            datos.put("ingresos", ingresos);
            if (canal == null) {
                datos.put("ingresosVentanilla", reporte.ingresosVentanilla());
                datos.put("ingresosRural", reporte.ingresosRural());
                datos.put("ingresosComerciantes", reporte.ingresosComerciantes());
                texto = "Ventas totales " + etiquetaPeriodo + ": " + moneda.format(ingresos) + " (ventanilla "
                        + moneda.format(reporte.ingresosVentanilla()) + ", rural "
                        + moneda.format(reporte.ingresosRural()) + ", comerciantes "
                        + moneda.format(reporte.ingresosComerciantes()) + ").";
            } else {
                texto = "Ventas de " + canal.toLowerCase(Locale.ROOT) + " " + etiquetaPeriodo + ": "
                        + moneda.format(ingresos) + ".";
            }
        }

        return new VozResultado(true, texto, datos, new Payload(periodo, etiquetaPeriodo, canal, tipoConsulta));
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

    private String etiqueta(Periodo p) {
        return switch (p) {
            case HOY -> "hoy";
            case AYER -> "ayer";
            case SEMANA -> "esta semana";
            case MES -> "este mes";
            case ANIO -> "este año";
        };
    }

    /** null si no se menciona ningún canal (= todos). */
    private String detectarCanal(String texto) {
        if (texto.contains("ventanilla")) return "VENTANILLA";
        if (texto.contains("rural")) return "RURAL";
        if (texto.contains("comerciantes")) return "COMERCIANTES";
        return null;
    }

    /** "vendimos" o "ingresos" caen en VENTAS. */
    private String detectarTipo(String texto) {
        return (texto.contains("ganamos") || texto.contains("ganancia")) ? "GANANCIA" : "VENTAS";
    }
}
