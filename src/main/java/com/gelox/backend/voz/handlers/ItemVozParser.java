package com.gelox.backend.voz.handlers;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extrae "N cajas/unidades de PRODUCTO [a|por|de $PRECIO]" de un texto ya
 * normalizado. Lo comparten el flujo de venta de ventanilla y el rural; cada
 * uno aporta sus propias palabras que cierran un ítem.
 */
final class ItemVozParser {

    private static final Pattern HAY_DIGITO = Pattern.compile("\\d");
    private static final Pattern HAY_TIPO = Pattern.compile("\\b(cajas?|unidad(?:es)?)\\b");
    private static final Pattern HAY_PRODUCTO = Pattern.compile("\\b(cajas?|unidad(?:es)?)\\s+(?:mas\\s+)?de\\s+\\S");

    record ItemExtraido(int cajas, int unidades, String fragmentoProducto, BigDecimal precioDicho) {}

    private ItemVozParser() {
    }

    /**
     * Grupos: 1 cantidad, 2 caja(s)/unidad(es), 3 producto, 4 precio dicho (opcional).
     * El precio admite "a 3500", "por 3500", "de 3500" y "$3500". Tolera "más"
     * entre el tipo y "de" ("agrega 2 unidades más de ...").
     */
    static Pattern patron(String terminadores) {
        return Pattern.compile(
                "(\\d+)\\s*(cajas?|unidad(?:es)?)\\s+(?:mas\\s+)?de\\s+(.+?)"
                        + "(?:\\s+(?:(?:a|por|de)\\s+\\$?|\\$)(\\d+))?"
                        + "(?=" + terminadores + "|$)");
    }

    static List<ItemExtraido> extraer(Pattern patron, String textoNormalizado) {
        List<ItemExtraido> items = new ArrayList<>();
        Matcher matcher = patron.matcher(textoNormalizado);

        while (matcher.find()) {
            int cantidad = Integer.parseInt(matcher.group(1));
            boolean esCaja = matcher.group(2).startsWith("caja");
            BigDecimal precioDicho = matcher.group(4) == null ? null : new BigDecimal(matcher.group(4));

            items.add(new ItemExtraido(esCaja ? cantidad : 0, esCaja ? 0 : cantidad,
                    matcher.group(3).trim(), precioDicho));
        }
        return items;
    }

    /** Mensaje según qué parte de la frase falta (cantidad, cajas/unidades o producto). */
    static String explicarFalta(String textoNormalizado, String ejemplo) {
        if (!HAY_DIGITO.matcher(textoNormalizado).find()) {
            return "No entendí la cantidad. " + ejemplo;
        }
        if (!HAY_TIPO.matcher(textoNormalizado).find()) {
            return "Falta decir si son cajas o unidades. " + ejemplo;
        }
        if (!HAY_PRODUCTO.matcher(textoNormalizado).find()) {
            return "Falta el producto. " + ejemplo;
        }
        return "No entendí las cantidades. " + ejemplo;
    }
}
