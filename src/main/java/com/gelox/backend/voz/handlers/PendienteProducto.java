package com.gelox.backend.voz.handlers;

import com.gelox.backend.voz.NormalizadorVoz;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Venta a la que solo le falta saber cuál producto es: el nombre dicho
 * ({@code fragmento}) coincide con varios. Guarda lo dicho para que la
 * respuesta ("fresa") solo tenga que resolver eso y el resto se reinterprete.
 *
 * @param textoOriginal texto tal como se dijo, sin normalizar
 * @param fragmento     nombre ambiguo, normalizado
 * @param rural         si el pedido es del flujo rural
 * @param anterior      payload de la venta en curso sobre la que se estaba agregando, o null
 */
public record PendienteProducto(String textoOriginal, String fragmento, boolean rural, Object anterior)
        implements PendienteAclaracion {

    /** Texto original con el fragmento ambiguo reemplazado por la respuesta; null si no se encuentra. */
    public String completar(String respuesta) {
        String limpia = respuesta.trim().replaceAll("[.!?]+$", "");
        // "Fresa" → "Aloha Paleta Fresa"; si ya dijo el nombre completo se usa tal cual.
        String reemplazo = NormalizadorVoz.normalizar(limpia).contains(fragmento) ? limpia : fragmento + " " + limpia;

        Matcher matcher = patronTolerante(fragmento).matcher(textoOriginal);
        if (!matcher.find()) return null;
        return textoOriginal.substring(0, matcher.start()) + reemplazo + textoOriginal.substring(matcher.end());
    }

    /** El fragmento viene sin tildes ni mayúsculas; el texto original puede traerlas. */
    private static Pattern patronTolerante(String fragmento) {
        StringBuilder regex = new StringBuilder("(?iu)");
        for (char c : fragmento.toCharArray()) {
            switch (c) {
                case 'a' -> regex.append("[aáàä]");
                case 'e' -> regex.append("[eéèë]");
                case 'i' -> regex.append("[iíìï]");
                case 'o' -> regex.append("[oóòö]");
                case 'u' -> regex.append("[uúùü]");
                case 'n' -> regex.append("[nñ]");
                case ' ' -> regex.append("\\s+");
                default -> regex.append(Pattern.quote(String.valueOf(c)));
            }
        }
        return Pattern.compile(regex.toString());
    }
}
