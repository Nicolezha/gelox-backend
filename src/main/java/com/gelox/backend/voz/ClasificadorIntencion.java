package com.gelox.backend.voz;

import com.gelox.backend.entities.TipoIntencionVoz;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Clasifica el texto transcrito en una {@link TipoIntencionVoz} y extrae los
 * slots relevantes (producto, cantidad, canal, período…) por coincidencia de
 * patrones. No usa ningún servicio externo.
 */
@Component
public class ClasificadorIntencion {

    public record ResultadoClasificacion(TipoIntencionVoz intencion, Map<String, String> slots) {
    }

    private record Regla(TipoIntencionVoz intencion, Pattern patron) {
    }

    /**
     * modificar_pedido se evalúa antes que generar_pedido: frases como
     * "al pedido pendiente agrégale…" contienen la palabra "pedido" y deben
     * resolver a MODIFICAR_PEDIDO, no a GENERAR_PEDIDO.
     */
    private static final List<Regla> REGLAS = List.of(
            new Regla(TipoIntencionVoz.REGISTRAR_VENTA,
                    Pattern.compile("registra|vende|vendi|anota")),
            new Regla(TipoIntencionVoz.CONSULTAR_INVENTARIO,
                    Pattern.compile("cuantas?.*(quedan|hay)|stock|inventario|se esta acabando")),
            new Regla(TipoIntencionVoz.CONSULTAR_FINANZAS,
                    Pattern.compile("cuanto (ganamos|vendimos)|ganancia|ingresos|cierra el dia|cierre del dia|resumen del dia")),
            new Regla(TipoIntencionVoz.MODIFICAR_PEDIDO,
                    Pattern.compile("(agrega|agregale|quita|elimina|cambia|actualiza).*pedido"
                            + "|pedido.*(agrega|agregale|quita|elimina|cambia|actualiza)")),
            new Regla(TipoIntencionVoz.GENERAR_PEDIDO,
                    Pattern.compile("(genera|crea|haz|arma).*pedido"))
    );

    private static final Pattern CANTIDAD = Pattern.compile("\\b(\\d+)\\b");
    private static final Pattern PRODUCTO = Pattern.compile("\\bde\\s+(.+)$");
    private static final Pattern RELLENO_PRODUCTO = Pattern.compile("\\b(quedan|hay|por favor|ya|pendiente)\\b");

    public ResultadoClasificacion clasificar(String texto) {
        String normalizado = NormalizadorVoz.normalizar(texto);

        for (Regla regla : REGLAS) {
            if (regla.patron().matcher(normalizado).find()) {
                return new ResultadoClasificacion(regla.intencion(), extraerSlots(regla.intencion(), normalizado));
            }
        }
        return new ResultadoClasificacion(null, Map.of());
    }

    private Map<String, String> extraerSlots(TipoIntencionVoz intencion, String texto) {
        Map<String, String> slots = new LinkedHashMap<>();

        Matcher cantidad = CANTIDAD.matcher(texto);
        if (cantidad.find()) slots.put("cantidad", cantidad.group(1));

        Matcher producto = PRODUCTO.matcher(texto);
        if (producto.find()) {
            String nombre = RELLENO_PRODUCTO.matcher(producto.group(1)).replaceAll("");
            nombre = nombre.replaceAll("[^a-z0-9 ]", "").replaceAll("\\s+", " ").trim();
            if (!nombre.isEmpty()) slots.put("producto", nombre);
        }

        if (texto.contains("rural")) slots.put("canal", "RURAL");

        if (intencion == TipoIntencionVoz.CONSULTAR_FINANZAS) {
            if (texto.contains("cierra el dia") || texto.contains("cierre del dia")) {
                slots.put("tipo", "CIERRE");
            }
            if (texto.contains("ayer")) slots.put("periodo", "AYER");
            else if (texto.contains("semana")) slots.put("periodo", "SEMANA");
            else if (texto.contains("mes")) slots.put("periodo", "MES");
            else slots.put("periodo", "HOY");
        }

        return slots;
    }
}
