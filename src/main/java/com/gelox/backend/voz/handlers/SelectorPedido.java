package com.gelox.backend.voz.handlers;

import com.gelox.backend.entities.PedidoProveedor;

import java.time.DateTimeException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Cuál pedido pendiente se quiere modificar cuando hay varios, sobre texto
 * normalizado: "al pedido 2…", "al segundo pedido…", "al más viejo…",
 * "al del 8 de octubre…", "al de ayer…", "al del lunes…". Los pendientes
 * se numeran del más reciente (1) al más antiguo, igual que los lista GELOX.
 *
 * @param textoSinSelector el texto sin la parte que eligió el pedido, para que
 *                         "pedido 2" no se tome como la cantidad
 */
record SelectorPedido(Integer posicion, LocalDate fecha, boolean masAntiguo, String textoSinSelector) {

    private static final Map<String, Integer> ORDINALES = Map.ofEntries(
            Map.entry("primer", 1), Map.entry("primero", 1), Map.entry("ultimo", 1),
            Map.entry("mas reciente", 1), Map.entry("mas nuevo", 1),
            Map.entry("segundo", 2), Map.entry("penultimo", 2), Map.entry("anterior", 2),
            Map.entry("tercer", 3), Map.entry("tercero", 3), Map.entry("cuarto", 4), Map.entry("quinto", 5));

    private static final Map<String, Integer> MESES = Map.ofEntries(
            Map.entry("enero", 1), Map.entry("febrero", 2), Map.entry("marzo", 3), Map.entry("abril", 4),
            Map.entry("mayo", 5), Map.entry("junio", 6), Map.entry("julio", 7), Map.entry("agosto", 8),
            Map.entry("septiembre", 9), Map.entry("setiembre", 9), Map.entry("octubre", 10),
            Map.entry("noviembre", 11), Map.entry("diciembre", 12));

    private static final Map<String, DayOfWeek> DIAS = Map.of(
            "lunes", DayOfWeek.MONDAY, "martes", DayOfWeek.TUESDAY, "miercoles", DayOfWeek.WEDNESDAY,
            "jueves", DayOfWeek.THURSDAY, "viernes", DayOfWeek.FRIDAY, "sabado", DayOfWeek.SATURDAY,
            "domingo", DayOfWeek.SUNDAY);

    /** "pedido 2", pero no "al pedido 10 cajas de Festival" (eso es la cantidad). */
    private static final Pattern POSICION = Pattern.compile(
            "\\bpedido\\s+(?:numero\\s+)?(\\d{1,2})\\b(?!\\s*(?:cajas?|cajitas?|unidad\\w*|de)\\b)");
    private static final Pattern ORDINAL = Pattern.compile(
            "\\b(primero|primer|ultimo|mas reciente|mas nuevo|segundo|penultimo|anterior|tercero|tercer|cuarto|quinto)\\b");
    private static final Pattern MAS_ANTIGUO = Pattern.compile("\\bmas (viejo|antiguo)\\b");
    private static final Pattern RELATIVO = Pattern.compile("\\bde\\s+(hoy|ayer|antier|anteayer)\\b");
    private static final Pattern FECHA = Pattern.compile(
            "\\bdel?\\s+(\\d{1,2})\\s+de\\s+(" + String.join("|", MESES.keySet()) + ")\\b|\\bdel\\s+(\\d{1,2})\\b");
    private static final Pattern DIA_SEMANA = Pattern.compile(
            "\\bdel\\s+(" + String.join("|", DIAS.keySet()) + ")\\b");

    static SelectorPedido de(String texto, LocalDate hoy) {
        Matcher m = POSICION.matcher(texto);
        if (m.find()) return new SelectorPedido(Integer.parseInt(m.group(1)), null, false, quitar(texto, m));

        m = MAS_ANTIGUO.matcher(texto);
        if (m.find()) return new SelectorPedido(null, null, true, quitar(texto, m));

        m = ORDINAL.matcher(texto);
        if (m.find()) return new SelectorPedido(ORDINALES.get(m.group(1)), null, false, quitar(texto, m));

        m = RELATIVO.matcher(texto);
        if (m.find()) {
            int diasAtras = switch (m.group(1)) {
                case "hoy" -> 0;
                case "ayer" -> 1;
                default -> 2;
            };
            return new SelectorPedido(null, hoy.minusDays(diasAtras), false, quitar(texto, m));
        }

        m = FECHA.matcher(texto);
        if (m.find()) {
            int dia = Integer.parseInt(m.group(1) != null ? m.group(1) : m.group(3));
            int mes = m.group(2) != null ? MESES.get(m.group(2)) : hoy.getMonthValue();
            try {
                LocalDate fecha = LocalDate.of(hoy.getYear(), mes, dia);
                if (fecha.isAfter(hoy)) fecha = fecha.minusYears(1);
                return new SelectorPedido(null, fecha, false, quitar(texto, m));
            } catch (DateTimeException e) {
                return new SelectorPedido(null, null, false, texto); // "el 31 de febrero": que pregunte
            }
        }

        m = DIA_SEMANA.matcher(texto);
        if (m.find()) {
            LocalDate fecha = hoy.with(TemporalAdjusters.previousOrSame(DIAS.get(m.group(1))));
            return new SelectorPedido(null, fecha, false, quitar(texto, m));
        }

        return new SelectorPedido(null, null, false, texto);
    }

    /** null si no se dijo a cuál, o si lo dicho no corresponde a ningún pendiente. */
    PedidoProveedor elegir(List<PedidoProveedor> pendientesDelMasReciente) {
        if (posicion != null) {
            return posicion >= 1 && posicion <= pendientesDelMasReciente.size()
                    ? pendientesDelMasReciente.get(posicion - 1) : null;
        }
        if (masAntiguo) return pendientesDelMasReciente.get(pendientesDelMasReciente.size() - 1);
        if (fecha != null) {
            return pendientesDelMasReciente.stream().filter(p -> fecha.equals(p.getFecha())).findFirst().orElse(null);
        }
        return null;
    }

    private static String quitar(String texto, Matcher m) {
        return (texto.substring(0, m.start()) + " " + texto.substring(m.end())).replaceAll("\\s+", " ").trim();
    }
}
