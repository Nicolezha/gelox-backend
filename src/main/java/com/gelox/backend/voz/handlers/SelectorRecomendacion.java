package com.gelox.backend.voz.handlers;

import com.gelox.backend.recomendacion.dto.RecomendacionDTO;
import com.gelox.backend.voz.FechaVoz;
import com.gelox.backend.voz.NormalizadorVoz;
import com.gelox.backend.voz.PreguntaVoz;
import com.gelox.backend.voz.VozResultado;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Cuál recomendación se quiere cuando hay varias el mismo día. Se listan de la más antigua
 * (la primera) a la más nueva (la última) y el usuario responde con la hora ("la de las 3 y 5"),
 * el código ("A1B2C3D4"), el precio ("la de 118 mil") o el orden ("la segunda", "la última");
 * los criterios se pueden combinar ("la segunda de 500 mil").
 */
final class SelectorRecomendacion {

    private SelectorRecomendacion() {
    }

    /** Slot con los ids (separados por coma) entre los que se está eligiendo, si ya se filtraron. */
    static final String SLOT_OPCIONES = "opciones";
    /** Slot con el id de la recomendación ya elegida. */
    static final String SLOT_ELEGIDA = "recomendacionId";

    /** {@code conCriterio=false} si la respuesta no dijo hora, código, precio ni orden. */
    record Resultado(boolean conCriterio, List<RecomendacionDTO> coincidencias) {}

    private static final String[] ORDINALES = {"primera", "segunda", "tercera", "cuarta", "quinta", "sexta",
            "séptima", "octava", "novena", "décima"};

    private static final Map<String, Integer> ORDINAL_POSICION = Map.ofEntries(
            Map.entry("primera", 1), Map.entry("primero", 1), Map.entry("primer", 1),
            Map.entry("segunda", 2), Map.entry("segundo", 2), Map.entry("tercera", 3), Map.entry("tercero", 3),
            Map.entry("tercer", 3), Map.entry("cuarta", 4), Map.entry("cuarto", 4), Map.entry("quinta", 5),
            Map.entry("quinto", 5), Map.entry("sexta", 6), Map.entry("sexto", 6), Map.entry("septima", 7),
            Map.entry("octava", 8), Map.entry("novena", 9), Map.entry("decima", 10));

    private static final Pattern ORDINAL = Pattern.compile("\\b(" + String.join("|", ORDINAL_POSICION.keySet()) + ")\\b");
    private static final Pattern ULTIMA = Pattern.compile("\\b(ultima|ultimo|mas reciente|mas nueva|mas nuevo|mas tarde)\\b");
    private static final Pattern PENULTIMA = Pattern.compile("\\b(penultima|penultimo)\\b");
    private static final Pattern MAS_ANTIGUA = Pattern.compile("\\b(mas antigua|mas antiguo|mas vieja|mas viejo|mas temprano)\\b");
    /** "2", "la 2", "numero 2", "opcion 2": toda la respuesta es la posición. */
    private static final Pattern POSICION = Pattern.compile(
            "^(?:la |el )?(?:numero |opcion |#\\s*)?(\\d{1,2})$|\\b(?:numero|opcion)\\s+(\\d{1,2})\\b");

    /** "a las 3", "de las 3 y 15", "las 15 30", "a la 1 y media", "las 3 de la tarde". */
    private static final Pattern HORA = Pattern.compile(
            "\\b(?:a las|de las|las|a la|de la)\\s+(\\d{1,2})(?:\\s+(?:y\\s+)?(media|cuarto|\\d{1,2}))?"
                    + "(?:\\s+(?:de la\\s+)?(manana|tarde|noche|am|pm|a m|p m))?\\b");
    /** "3 de la tarde", "9 am" sin "las". */
    private static final Pattern HORA_SUFIJO = Pattern.compile(
            "\\b(\\d{1,2})(?:\\s+(?:y\\s+)?(media|cuarto|\\d{1,2}))?\\s+(?:de la\\s+)?(manana|tarde|noche|am|pm|a m|p m)\\b");

    private static final Pattern MILLONES = Pattern.compile("\\b(\\d+)\\s+millon(?:es)?(?:\\s+(?:de\\s+)?(\\d{1,6}))?\\b");
    private static final Pattern NUMERO_GRANDE = Pattern.compile("\\b(\\d{4,})\\b");

    private static final Set<String> RELLENO_CODIGO = Set.of("codigo", "el", "la", "de", "del", "con", "numero",
            "recomendacion", "es", "esa", "esta", "la que", "que", "tiene", "termina", "en", "empieza", "por");

    static Resultado elegir(String respuesta, List<RecomendacionDTO> candidatas) {
        String t = FechaVoz.quitarFechas(NormalizadorVoz.normalizar(respuesta));
        t = MILLONES.matcher(t).replaceAll(mr -> {
            long base = Long.parseLong(mr.group(1)) * 1_000_000L;
            long resto = mr.group(2) == null ? 0 : Long.parseLong(mr.group(2));
            return String.valueOf(base + resto);
        });

        boolean conCriterio = false;
        List<RecomendacionDTO> filtradas = candidatas;

        List<RecomendacionDTO> porCodigo = porCodigo(t, candidatas);
        if (!porCodigo.isEmpty()) {
            conCriterio = true;
            filtradas = porCodigo;
        }

        Matcher hora = HORA.matcher(t);
        boolean hayHora = hora.find();
        if (!hayHora) {
            hora = HORA_SUFIJO.matcher(t);
            hayHora = hora.find();
        }
        if (hayHora) {
            conCriterio = true;
            final int h = Integer.parseInt(hora.group(1));
            final Integer minuto = minuto(hora.group(2));
            final String sufijo = hora.group(3);
            filtradas = filtradas.stream().filter(r -> coincideHora(r.createdAt(), h, minuto, sufijo)).toList();
        }

        Matcher numero = NUMERO_GRANDE.matcher(t);
        List<BigDecimal> precios = new ArrayList<>();
        while (numero.find()) precios.add(new BigDecimal(numero.group(1)));
        if (!precios.isEmpty() && porCodigo.isEmpty()) {
            conCriterio = true;
            filtradas = porPrecio(filtradas, precios);
        }

        Integer posicion = null;
        if (ULTIMA.matcher(t).find()) posicion = filtradas.size();
        else if (PENULTIMA.matcher(t).find()) posicion = filtradas.size() - 1;
        else if (MAS_ANTIGUA.matcher(t).find()) posicion = 1;
        else {
            Matcher ordinal = ORDINAL.matcher(t);
            if (ordinal.find()) {
                posicion = ORDINAL_POSICION.get(ordinal.group(1));
            } else if (!hayHora) {
                Matcher pos = POSICION.matcher(t);
                if (pos.find()) posicion = Integer.parseInt(pos.group(1) != null ? pos.group(1) : pos.group(2));
            }
        }
        if (posicion != null) {
            conCriterio = true;
            filtradas = posicion >= 1 && posicion <= filtradas.size() ? List.of(filtradas.get(posicion - 1)) : List.of();
        }
        return new Resultado(conCriterio, filtradas);
    }

    // ══════════════════════════════════════════════════════════════════════
    // Pregunta "¿cuál?" con el listado
    // ══════════════════════════════════════════════════════════════════════

    /**
     * «Hay 3 recomendaciones pendientes del 15 de noviembre: la primera, de las 9:05 a. m., código
     * A1B2C3D4, por $118.720; …. ¿Cuál quieres aprobar? …». {@code slots} deben llevar ya la fecha y la acción.
     */
    static VozResultado preguntarCual(String prefijo, List<RecomendacionDTO> opciones, LocalDate fecha, LocalDate hoy,
                                      String verbo, Map<String, String> slots, boolean filtradas) {
        StringBuilder sb = new StringBuilder(prefijo == null ? "" : prefijo);
        sb.append(filtradas ? "Coinciden " + opciones.size() + " recomendaciones" :
                        "Hay " + opciones.size() + " recomendaciones pendientes " + deFecha(fecha, hoy))
                .append(": ");
        List<Map<String, Object>> listado = new ArrayList<>();
        for (int i = 0; i < opciones.size(); i++) {
            RecomendacionDTO r = opciones.get(i);
            if (i > 0) sb.append("; ");
            sb.append("la ").append(ordinal(i + 1)).append(", de las ").append(hora(r.createdAt()))
                    .append(", código ").append(codigo(r.id()))
                    .append(", por ").append(RecomendacionVozHandler.pesos(r.costoTotal()));

            Map<String, Object> opcion = new LinkedHashMap<>();
            opcion.put("numero", i + 1);
            opcion.put("recomendacionId", r.id());
            opcion.put("codigo", codigo(r.id()));
            opcion.put("hora", hora(r.createdAt()));
            opcion.put("costoTotal", r.costoTotal());
            opcion.put("presupuesto", r.presupuesto());
            listado.add(opcion);
        }
        sb.append(". ¿Cuál quieres ").append(verbo)
                .append("? Responde con la hora, el código, el precio o di 'la primera', 'la segunda' o 'la última'.");

        Map<String, String> siguientes = new LinkedHashMap<>(slots);
        siguientes.remove(SLOT_ELEGIDA);
        if (filtradas) {
            siguientes.put(SLOT_OPCIONES, opciones.stream().map(r -> r.id().toString()).collect(Collectors.joining(",")));
        } else {
            siguientes.remove(SLOT_OPCIONES);
        }
        Map<String, Object> datos = new LinkedHashMap<>();
        datos.put("opciones", listado);
        return new VozResultado(false, sb.toString(), datos, new PreguntaVoz(siguientes));
    }

    /** Las candidatas restringidas a las del slot {@value #SLOT_OPCIONES}, si lo hay. */
    static List<RecomendacionDTO> opciones(List<RecomendacionDTO> candidatas, String slotOpciones) {
        if (slotOpciones == null || slotOpciones.isBlank()) return candidatas;
        Set<String> ids = Set.copyOf(Arrays.asList(slotOpciones.split(",")));
        List<RecomendacionDTO> filtradas = candidatas.stream().filter(r -> ids.contains(r.id().toString())).toList();
        return filtradas.isEmpty() ? candidatas : filtradas;
    }

    static Optional<RecomendacionDTO> porId(List<RecomendacionDTO> candidatas, String id) {
        if (id == null) return Optional.empty();
        return candidatas.stream().filter(r -> r.id().toString().equals(id)).findFirst();
    }

    /** "de hoy", "de ayer" o "del 15 de noviembre". */
    static String deFecha(LocalDate fecha, LocalDate hoy) {
        String descrita = FechaVoz.describir(fecha, hoy);
        return descrita.startsWith("el ") ? "del " + descrita.substring(3) : "de " + descrita;
    }

    /** Los 8 primeros caracteres del id, como se muestra en el historial. */
    static String codigo(UUID id) {
        return id.toString().substring(0, 8).toUpperCase(Locale.ROOT);
    }

    /** "9:05 a. m.", "3:40 p. m.". */
    static String hora(LocalDateTime momento) {
        if (momento == null) return "hora desconocida";
        int h = momento.getHour() % 12 == 0 ? 12 : momento.getHour() % 12;
        return h + ":" + String.format("%02d", momento.getMinute()) + (momento.getHour() < 12 ? " a. m." : " p. m.");
    }

    static String ordinal(int n) {
        return n <= ORDINALES.length ? ORDINALES[n - 1] : "número " + n;
    }

    // ══════════════════════════════════════════════════════════════════════
    // Criterios
    // ══════════════════════════════════════════════════════════════════════

    private static List<RecomendacionDTO> porCodigo(String t, List<RecomendacionDTO> candidatas) {
        String compacto = Arrays.stream(t.split("\\s+"))
                .filter(p -> !RELLENO_CODIGO.contains(p))
                .collect(Collectors.joining())
                .replaceAll("[^a-z0-9]", "");
        // Un código tiene letras o al menos 4 caracteres; "la 2" o "500000" no lo son.
        if (compacto.length() < 4 || !compacto.matches(".*[a-z].*")) return List.of();
        return candidatas.stream()
                .filter(r -> {
                    String codigo = codigo(r.id()).toLowerCase(Locale.ROOT);
                    return compacto.contains(codigo) || codigo.startsWith(compacto);
                })
                .toList();
    }

    private static Integer minuto(String grupo) {
        if (grupo == null) return null;
        return switch (grupo) {
            case "media" -> 30;
            case "cuarto" -> 15;
            default -> Integer.parseInt(grupo);
        };
    }

    private static boolean coincideHora(LocalDateTime momento, int h, Integer minuto, String sufijo) {
        if (momento == null) return false;
        int hora = momento.getHour();
        boolean horaOk;
        if (sufijo == null) {
            horaOk = hora == h || (h < 12 && hora == h + 12) || (h == 12 && hora == 0);
        } else {
            boolean tarde = sufijo.equals("tarde") || sufijo.equals("noche") || sufijo.equals("pm") || sufijo.equals("p m");
            int h24 = tarde ? (h < 12 ? h + 12 : h) : (h == 12 ? 0 : h);
            horaOk = hora == h24;
        }
        return horaOk && (minuto == null || Math.abs(momento.getMinute() - minuto) <= 1);
    }

    /** Coincidencia exacta con el costo total o el presupuesto; si no hay, la más cercana dentro de ±1 % (mín. $1.000). */
    private static List<RecomendacionDTO> porPrecio(List<RecomendacionDTO> candidatas, List<BigDecimal> precios) {
        List<RecomendacionDTO> exactas = candidatas.stream()
                .filter(r -> precios.stream().anyMatch(p -> igual(r.costoTotal(), p) || igual(r.presupuesto(), p)))
                .toList();
        if (!exactas.isEmpty()) return exactas;
        return candidatas.stream()
                .filter(r -> precios.stream().anyMatch(p -> cerca(r.costoTotal(), p) || cerca(r.presupuesto(), p)))
                .toList();
    }

    private static boolean igual(BigDecimal valor, BigDecimal dicho) {
        return valor != null && valor.subtract(dicho).abs().compareTo(BigDecimal.ONE) < 0;
    }

    private static boolean cerca(BigDecimal valor, BigDecimal dicho) {
        if (valor == null) return false;
        BigDecimal tolerancia = valor.abs().multiply(new BigDecimal("0.01")).max(new BigDecimal("1000"));
        return valor.subtract(dicho).abs().compareTo(tolerancia) <= 0;
    }
}
