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
    private static final String TIPO = "(cajas?|cajitas?|unidad(?:es)?|unidaditas?)";
    private static final Pattern HAY_TIPO = Pattern.compile("\\b" + TIPO + "\\b");
    private static final Pattern HAY_PRODUCTO = Pattern.compile("\\b" + TIPO + "\\s+(?:mas\\s+)?(?:de\\s+)?\\S");

    record ItemExtraido(int cajas, int unidades, String fragmentoProducto, BigDecimal precioDicho) {}

    private ItemVozParser() {
    }

    /**
     * Grupos: 1 cantidad, 2 caja(s)/unidad(es), 3 producto, 4 precio dicho (opcional).
     * El precio admite "a 3500", "por 3500", "de 3500" y "$3500". Tolera "más"
     * entre el tipo y "de" ("agrega 2 unidades más de ..."), "cajita(s)" y que
     * no se diga el "de" ("dos cajas Festival").
     */
    static Pattern patron(String terminadores) {
        return Pattern.compile(
                "(\\d+)\\s*" + TIPO + "\\s+(?:mas\\s+)?(?:de\\s+)?(.+?)"
                        // "de 90 gramos" es el tamaño del producto, no el precio.
                        + "(?:\\s+(?:(?:a|por|de)\\s+\\$?|\\$)(\\d+)(?!\\s*(?:gramos?|gr|g|litros?|ml)\\b))?"
                        + "(?=" + terminadores + "|$)");
    }

    static List<ItemExtraido> extraer(Pattern patron, String textoNormalizado) {
        List<ItemExtraido> items = new ArrayList<>();
        Matcher matcher = patron.matcher(textoNormalizado);

        while (matcher.find()) {
            int cantidad = Integer.parseInt(matcher.group(1));
            boolean esCaja = matcher.group(2).startsWith("caj");
            BigDecimal precioDicho = matcher.group(4) == null ? null : new BigDecimal(matcher.group(4));
            int cajas = esCaja ? cantidad : 0;
            int unidades = esCaja ? 0 : cantidad;
            String producto = matcher.group(3).trim();

            // "una caja y seis unidades de Festival": la segunda cantidad es del mismo producto.
            Matcher otra = OTRA_CANTIDAD.matcher(producto);
            if (otra.matches()) {
                int segunda = Integer.parseInt(otra.group(1));
                if (otra.group(2).startsWith("caj")) cajas += segunda;
                else unidades += segunda;
                producto = otra.group(3).trim();
            }

            items.add(new ItemExtraido(cajas, unidades, producto, precioDicho));
        }
        return items;
    }

    private static final Pattern OTRA_CANTIDAD =
            Pattern.compile("y\\s+(\\d+)\\s*" + TIPO + "\\s+(?:mas\\s+)?(?:de\\s+)?(.+)");

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
