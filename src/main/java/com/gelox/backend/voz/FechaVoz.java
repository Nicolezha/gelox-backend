package com.gelox.backend.voz;

import java.time.DateTimeException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.time.temporal.TemporalAdjusters;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Fecha dicha en un comando de voz, sobre texto ya normalizado ({@link NormalizadorVoz}):
 * "hoy", "ayer", "antier", "el 15 de noviembre (de 2026)", "el primero de noviembre",
 * "15/11", "el lunes". Sin año se toma el más reciente que no sea futuro.
 */
public final class FechaVoz {

    private FechaVoz() {
    }

    static final Map<String, Integer> MESES = Map.ofEntries(
            Map.entry("enero", 1), Map.entry("febrero", 2), Map.entry("marzo", 3), Map.entry("abril", 4),
            Map.entry("mayo", 5), Map.entry("junio", 6), Map.entry("julio", 7), Map.entry("agosto", 8),
            Map.entry("septiembre", 9), Map.entry("setiembre", 9), Map.entry("octubre", 10),
            Map.entry("noviembre", 11), Map.entry("diciembre", 12));

    private static final String[] NOMBRES_MES = {"enero", "febrero", "marzo", "abril", "mayo", "junio", "julio",
            "agosto", "septiembre", "octubre", "noviembre", "diciembre"};

    private static final Map<String, DayOfWeek> DIAS = Map.of(
            "lunes", DayOfWeek.MONDAY, "martes", DayOfWeek.TUESDAY, "miercoles", DayOfWeek.WEDNESDAY,
            "jueves", DayOfWeek.THURSDAY, "viernes", DayOfWeek.FRIDAY, "sabado", DayOfWeek.SATURDAY,
            "domingo", DayOfWeek.SUNDAY);

    private static final Pattern ANTIER = Pattern.compile("\\b(antier|anteayer|antes de ayer)\\b");
    private static final Pattern AYER = Pattern.compile("\\bayer\\b");
    private static final Pattern HOY = Pattern.compile("\\bhoy\\b");
    private static final Pattern DIA_DE_MES = Pattern.compile(
            "\\b(\\d{1,2}|primero)\\s+de\\s+(" + String.join("|", MESES.keySet()) + ")\\b(?:\\s+(?:de|del)\\s+(\\d{4}))?");
    private static final Pattern NUMERICA = Pattern.compile("\\b(\\d{1,2})[/-](\\d{1,2})(?:[/-](\\d{2}|\\d{4}))?\\b");
    private static final Pattern ISO = Pattern.compile("\\b(\\d{4})-(\\d{2})-(\\d{2})\\b");
    private static final Pattern DIA_SUELTO = Pattern.compile(
            "\\b(?:el dia|del dia|dia|del)\\s+(\\d{1,2})\\b(?!\\s*(?:cajas?|unidad\\w*|productos?|pesos|mil|de\\s+\\D))");
    private static final Pattern DIA_SEMANA = Pattern.compile(
            "\\b(?:el|del|este|el pasado)\\s+(" + String.join("|", DIAS.keySet()) + ")\\b");

    /** Fecha del slot "fecha" en ISO (yyyy-MM-dd), como la devuelve el LLM. */
    public static Optional<LocalDate> deSlot(String slot) {
        if (slot == null || slot.isBlank()) return Optional.empty();
        try {
            return Optional.of(LocalDate.parse(slot.trim()));
        } catch (DateTimeParseException e) {
            return Optional.empty();
        }
    }

    /** Fecha dicha en el texto normalizado; vacío si no se dijo ninguna (o no existe, p. ej. 31 de febrero). */
    public static Optional<LocalDate> de(String normalizado, LocalDate hoy) {
        if (normalizado == null || normalizado.isBlank()) return Optional.empty();

        Matcher m = ISO.matcher(normalizado);
        if (m.find()) return crear(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)),
                Integer.parseInt(m.group(3)), hoy, true);

        m = DIA_DE_MES.matcher(normalizado);
        if (m.find()) {
            int dia = "primero".equals(m.group(1)) ? 1 : Integer.parseInt(m.group(1));
            boolean conAnio = m.group(3) != null;
            int anio = conAnio ? Integer.parseInt(m.group(3)) : hoy.getYear();
            return crear(anio, MESES.get(m.group(2)), dia, hoy, conAnio);
        }

        m = NUMERICA.matcher(normalizado);
        if (m.find()) {
            boolean conAnio = m.group(3) != null;
            int anio = !conAnio ? hoy.getYear()
                    : m.group(3).length() == 2 ? 2000 + Integer.parseInt(m.group(3)) : Integer.parseInt(m.group(3));
            return crear(anio, Integer.parseInt(m.group(2)), Integer.parseInt(m.group(1)), hoy, conAnio);
        }

        if (ANTIER.matcher(normalizado).find()) return Optional.of(hoy.minusDays(2));
        if (AYER.matcher(normalizado).find()) return Optional.of(hoy.minusDays(1));
        if (HOY.matcher(normalizado).find()) return Optional.of(hoy);

        m = DIA_SEMANA.matcher(normalizado);
        if (m.find()) return Optional.of(hoy.with(TemporalAdjusters.previousOrSame(DIAS.get(m.group(1)))));

        m = DIA_SUELTO.matcher(normalizado);
        if (m.find()) {
            // "el 20" dicho el 18: el 20 del mes pasado.
            Optional<LocalDate> fecha = crear(hoy.getYear(), hoy.getMonthValue(), Integer.parseInt(m.group(1)), hoy, true);
            return fecha.map(f -> f.isAfter(hoy) ? f.minusMonths(1) : f);
        }

        return Optional.empty();
    }

    /** El texto normalizado sin las fechas completas ("15 de noviembre de 2026", "15/11"), para que no se lean como montos. */
    public static String quitarFechas(String normalizado) {
        String sinFechas = DIA_DE_MES.matcher(normalizado).replaceAll(" ");
        sinFechas = NUMERICA.matcher(sinFechas).replaceAll(" ");
        sinFechas = ISO.matcher(sinFechas).replaceAll(" ");
        return sinFechas.replaceAll("\\s+", " ").trim();
    }

    /** "hoy", "ayer" o "el 15 de noviembre" (con el año si no es el actual). */
    public static String describir(LocalDate fecha, LocalDate hoy) {
        if (fecha.equals(hoy)) return "hoy";
        if (fecha.equals(hoy.minusDays(1))) return "ayer";
        String texto = "el " + fecha.getDayOfMonth() + " de " + NOMBRES_MES[fecha.getMonthValue() - 1];
        return fecha.getYear() == hoy.getYear() ? texto : texto + " de " + fecha.getYear();
    }

    private static Optional<LocalDate> crear(int anio, int mes, int dia, LocalDate hoy, boolean conAnio) {
        try {
            LocalDate fecha = LocalDate.of(anio, mes, dia);
            // "el 20 de diciembre" dicho en noviembre: el del año pasado.
            if (!conAnio && fecha.isAfter(hoy)) fecha = fecha.minusYears(1);
            return Optional.of(fecha);
        } catch (DateTimeException e) {
            return Optional.empty();
        }
    }
}
