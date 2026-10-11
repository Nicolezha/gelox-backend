package com.gelox.backend.voz.handlers;

import com.gelox.backend.dto.PeriodoFiltroDTO;
import com.gelox.backend.dto.ReporteFinancieroDTO;
import com.gelox.backend.entities.TipoIntencionVoz;
import com.gelox.backend.entities.Usuario;
import com.gelox.backend.security.RequiereRol;
import com.gelox.backend.services.ReporteFinancieroService;
import com.gelox.backend.voz.ClasificadorIntencion;
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
import java.util.regex.Matcher;
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
    private static final Pattern GANANCIA = Pattern.compile("\\b(ganamos|ganado|ganancias?|utilidad\\w*|margen|rentabilidad)\\b");

    private static final Pattern ANIO_PATTERN = Pattern.compile("\\bano\\b");

    private static final DateTimeFormatter FORMATO_FECHA_LARGA =
            DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy", Locale.of("es", "CO"));
    private static final DateTimeFormatter FORMATO_MES_LARGO =
            DateTimeFormatter.ofPattern("MMMM 'de' yyyy", Locale.of("es", "CO"));

    private static final Map<String, Integer> MESES = Map.ofEntries(
            Map.entry("enero", 1),
            Map.entry("febrero", 2),
            Map.entry("marzo", 3),
            Map.entry("abril", 4),
            Map.entry("mayo", 5),
            Map.entry("junio", 6),
            Map.entry("julio", 7),
            Map.entry("agosto", 8),
            Map.entry("septiembre", 9),
            Map.entry("setiembre", 9),
            Map.entry("octubre", 10),
            Map.entry("noviembre", 11),
            Map.entry("diciembre", 12)
    );
    private static final Pattern PATRON_MESES = Pattern.compile(
            "\\b(enero|febrero|marzo|abril|mayo|junio|julio|agosto|septiembre|setiembre|octubre|noviembre|diciembre)\\b");
    private static final Pattern ANIO_NUMERICO = Pattern.compile("\\b(20\\d\\d)\\b");

    private final ReporteFinancieroService reporteFinancieroService;
    private final CierreDiaResumen cierreDiaResumen;

    /** {@code tipoConsulta} es "ventas" o "ganancia" (en minúsculas, como lo lee el frontend). */
    private record Payload(PeriodoFiltroDTO periodo, Periodo periodoEnum, String tipoConsulta,
                           String etiquetaLarga, String etiquetaCorta) {}

    private record InfoPeriodo(PeriodoFiltroDTO filtro, Periodo tipo, String etiquetaLarga, String etiquetaCorta) {}

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
        if (ClasificadorIntencion.esCierre(texto)) {
            return cierreDiaResumen.generarResumen(ctx.hoy());
        }

        InfoPeriodo info = resolverPeriodo(texto, ctx.hoy());
        String tipoConsulta = detectarTipo(texto);

        return construirRespuesta(info.filtro(), info.tipo(), tipoConsulta, info.etiquetaLarga(), info.etiquetaCorta());
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
        return construirRespuesta(payload.periodo(), payload.periodoEnum(), payload.tipoConsulta(),
                payload.etiquetaLarga(), payload.etiquetaCorta());
    }

    private VozResultado construirRespuesta(PeriodoFiltroDTO periodo, Periodo p, String tipoConsulta,
                                            String etiquetaLarga, String etiquetaCorta) {
        ReporteFinancieroDTO reporte = reporteFinancieroService.generarReporte(periodo);
        NumberFormat moneda = NumberFormat.getCurrencyInstance(Locale.of("es", "CO"));

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
                        // "369.0000" se narraría con todos los ceros: máximo un decimal.
                        ? " con un margen de ganancia del " + reporte.margenGanancia()
                                .setScale(1, java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString() + " %."
                        : ".");
        } else {
            texto = "Las ventas " + etiquetaCorta + " suman " + moneda.format(reporte.ingresosTotales()) + ".";
        }

        return new VozResultado(true, texto, datos, new Payload(periodo, p, tipoConsulta, etiquetaLarga, etiquetaCorta));
    }

    private InfoPeriodo resolverPeriodo(String texto, LocalDate hoy) {
        Matcher mMes = PATRON_MESES.matcher(texto);
        if (mMes.find()) {
            String mesNombre = mMes.group(1);
            int mesNumero = MESES.get(mesNombre);
            Matcher mAnio = ANIO_NUMERICO.matcher(texto);
            int anio = mAnio.find() ? Integer.parseInt(mAnio.group(1)) : hoy.getYear();

            LocalDate inicio = LocalDate.of(anio, mesNumero, 1);
            LocalDate finDeMes = inicio.withDayOfMonth(inicio.lengthOfMonth());
            LocalDate fin = (anio == hoy.getYear() && mesNumero == hoy.getMonthValue() && hoy.isBefore(finDeMes)) ? hoy : finDeMes;

            String mesCap = mesNombre.substring(0, 1).toUpperCase() + mesNombre.substring(1);
            String etiquetaLarga = mesCap + " de " + anio;
            String etiquetaCorta = "en " + mesNombre + " de " + anio;

            return new InfoPeriodo(new PeriodoFiltroDTO(inicio, fin), Periodo.MES, etiquetaLarga, etiquetaCorta);
        }

        Periodo p = detectarPeriodo(texto);
        PeriodoFiltroDTO filtro = periodoFiltro(p, hoy);
        return new InfoPeriodo(filtro, p, etiqueta(p, filtro), etiquetaCorta(p));
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
        return GANANCIA.matcher(texto).find() ? "ganancia" : "ventas";
    }
}
