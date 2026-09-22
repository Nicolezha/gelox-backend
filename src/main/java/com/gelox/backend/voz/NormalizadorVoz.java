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
            Map.entry("ochenta", "80"), Map.entry("noventa", "90"), Map.entry("cien", "100")
    );

    // "treinta y dos" -> tokens quedan como "30 y 2" tras el mapeo palabra-a-palabra; aquí se suman.
    private static final Pattern DECENA_Y_UNIDAD = Pattern.compile("\\b([1-9]0)\\s+y\\s+([1-9])\\b");
    private static final Pattern MONTO_MILES = Pattern.compile("(?<=\\d)\\.(?=\\d{3}\\b)");
    private static final Pattern ESPACIOS = Pattern.compile("\\s+");

    public static String normalizar(String texto) {
        if (texto == null) return "";

        String t = texto.toLowerCase();
        t = Normalizer.normalize(t, Normalizer.Form.NFD).replaceAll("\\p{M}", "");

        StringBuilder sb = new StringBuilder();
        for (String palabra : t.split("\\s+")) {
            sb.append(PALABRAS_NUMERO.getOrDefault(palabra, palabra)).append(' ');
        }
        t = sb.toString().trim();

        t = DECENA_Y_UNIDAD.matcher(t).replaceAll(mr ->
                String.valueOf(Integer.parseInt(mr.group(1)) + Integer.parseInt(mr.group(2))));

        t = MONTO_MILES.matcher(t).replaceAll("");

        return ESPACIOS.matcher(t).replaceAll(" ").trim();
    }
}
