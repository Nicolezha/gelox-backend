package com.gelox.backend.voz;

import java.text.Normalizer;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Normalización de texto compartida entre {@link ClasificadorIntencion} y
 * {@link ResolvedorProducto}: minúsculas, sin tildes, números escritos a
 * dígitos y montos con separador de miles ("2.500") a número plano.
 */
public final class NormalizadorVoz {

    private NormalizadorVoz() {
    }

    private static final Map<String, String> PALABRAS_NUMERO = Map.ofEntries(
            Map.entry("cero", "0"), Map.entry("un", "1"), Map.entry("uno", "1"), Map.entry("una", "1"),
            Map.entry("dos", "2"), Map.entry("tres", "3"), Map.entry("cuatro", "4"), Map.entry("cinco", "5"),
            Map.entry("seis", "6"), Map.entry("siete", "7"), Map.entry("ocho", "8"), Map.entry("nueve", "9"),
            Map.entry("diez", "10"), Map.entry("once", "11"), Map.entry("doce", "12"), Map.entry("trece", "13"),
            Map.entry("catorce", "14"), Map.entry("quince", "15"), Map.entry("dieciseis", "16"),
            Map.entry("diecisiete", "17"), Map.entry("dieciocho", "18"), Map.entry("diecinueve", "19"),
            Map.entry("veinte", "20"), Map.entry("veintiuno", "21"), Map.entry("veintidos", "22"),
            Map.entry("veintitres", "23"), Map.entry("veinticuatro", "24"), Map.entry("veinticinco", "25"),
            Map.entry("veintiseis", "26"), Map.entry("veintisiete", "27"), Map.entry("veintiocho", "28"),
            Map.entry("veintinueve", "29"), Map.entry("treinta", "30"), Map.entry("cuarenta", "40"),
            Map.entry("cincuenta", "50"), Map.entry("sesenta", "60"), Map.entry("setenta", "70"),
            Map.entry("ochenta", "80"), Map.entry("noventa", "90"), Map.entry("cien", "100"),
            Map.entry("veintiun", "21"), Map.entry("ciento", "100"), Map.entry("doscientos", "200"),
            Map.entry("trescientos", "300"), Map.entry("cuatrocientos", "400"), Map.entry("quinientos", "500"),
            Map.entry("seiscientos", "600"), Map.entry("setecientos", "700"), Map.entry("ochocientos", "800"),
            Map.entry("novecientos", "900")
    );

    // "treinta y dos" -> tokens quedan como "30 y 2" tras el mapeo palabra-a-palabra; aquí se suman.
    private static final Pattern DECENA_Y_UNIDAD = Pattern.compile("\\b([1-9]0)\\s+y\\s+([1-9])\\b");
    private static final Pattern CENTENA_Y_RESTO = Pattern.compile("\\b([1-9]00)\\s+([1-9]\\d?)\\b");
    /** "3 mil 500" -> 3500, "10 mil" -> 10000, "mil 200" -> 1200. */
    private static final Pattern MILES = Pattern.compile("\\b(?:(\\d{1,3})\\s+)?mil\\b(?:\\s+(\\d{1,3})\\b)?");
    /** Chrome escribe los montos como "2.500" o "2,500". */
    private static final Pattern MONTO_MILES = Pattern.compile("(?<=\\d)[.,](?=\\d{3}\\b)");
    /** Puntuación que agrega el reconocedor; la coma se conserva porque separa ítems. */
    private static final Pattern PUNTUACION = Pattern.compile("[¿?¡!.;:\"“”()]");
    /** Colombianismos y modismos cucuteños: lucas (miles de pesos), pacas (cajas), muletillas. */
    private static final Pattern LUCAS_PATTERN = Pattern.compile("\\b(\\d+)\\s+lucas?\\b");
    private static final Pattern MULETILLAS_COLOMBIANAS = Pattern.compile(
            "\\b(mano|ole|parce|parcero|toche|pingo|marica|mi rey|mi reina|veci|vecino|porfa|por fa|haga el favor|hagame el favor)\\b");
    private static final Pattern VERBOS_COLOQUIALES = Pattern.compile(
            "\\b(regaleme|regalame|deme|despacheme|pongame|anoteme|apunteme|sirvame)\\b");
    private static final Pattern PACAS_PATTERN = Pattern.compile("\\bpacas?\\b");

    private static final Pattern ESPACIOS = Pattern.compile("\\s+");

    public static String normalizar(String texto) {
        if (texto == null) return "";

        String t = texto.toLowerCase();
        t = Normalizer.normalize(t, Normalizer.Form.NFD).replaceAll("\\p{M}", "");

        // Limpieza de muletillas cucuteñas/colombianas comunes
        t = MULETILLAS_COLOMBIANAS.matcher(t).replaceAll(" ");

        // Normalización de verbos de pedido ("regálame", "deme" -> "registra")
        t = VERBOS_COLOQUIALES.matcher(t).replaceAll("registra");

        // "pacas" -> "cajas" (término comercial muy usado en Cúcuta)
        t = PACAS_PATTERN.matcher(t).replaceAll("cajas");

        // Jerga de dinero: "media luca" -> 500, "una luca" -> 1000, "barras" -> pesos
        t = t.replaceAll("\\bmedia luca\\b", "500")
             .replaceAll("\\buna luca\\b", "1000")
             .replaceAll("\\bbarras?\\b", "pesos");

        t = MONTO_MILES.matcher(t).replaceAll("");
        t = PUNTUACION.matcher(t).replaceAll(" ").replace(",", " , ").trim();
        if (t.isEmpty()) return "";

        StringBuilder sb = new StringBuilder();
        for (String palabra : t.split("\\s+")) {
            sb.append(PALABRAS_NUMERO.getOrDefault(palabra, palabra)).append(' ');
        }
        t = sb.toString().trim();

        t = DECENA_Y_UNIDAD.matcher(t).replaceAll(mr ->
                String.valueOf(Integer.parseInt(mr.group(1)) + Integer.parseInt(mr.group(2))));

        t = CENTENA_Y_RESTO.matcher(t).replaceAll(mr ->
                String.valueOf(Integer.parseInt(mr.group(1)) + Integer.parseInt(mr.group(2))));

        // "3 lucas" -> 3000
        t = LUCAS_PATTERN.matcher(t).replaceAll(mr ->
                String.valueOf(Integer.parseInt(mr.group(1)) * 1000));

        t = MILES.matcher(t).replaceAll(mr -> {
            int miles = mr.group(1) == null ? 1 : Integer.parseInt(mr.group(1));
            int resto = mr.group(2) == null ? 0 : Integer.parseInt(mr.group(2));
            return String.valueOf(miles * 1000 + resto);
        });

        return ESPACIOS.matcher(t).replaceAll(" ").replace(" ,", ",").trim();
    }
}
