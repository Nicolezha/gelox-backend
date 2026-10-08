package com.gelox.backend.voz;

import com.gelox.backend.entities.MetodoClasificacion;
import com.gelox.backend.entities.TipoIntencionVoz;
import com.gelox.backend.voz.pln.ModeloIntencion;
import com.gelox.backend.voz.pln.PrediccionIntencion;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Clasifica el texto transcrito en una {@link TipoIntencionVoz} en dos niveles
 * (FP-15 §2.4, §2.5): primero el modelo de PLN de OpenNLP y, si su probabilidad
 * es menor a {@link #UMBRAL_INTENCION} o la frase es FUERA_DE_DOMINIO, las reglas
 * de patrones. Después extrae los slots (producto, cantidad, canal, período…).
 */
@Component
public class ClasificadorIntencion {

    /** FP-15 §2.5 — probabilidad mínima del modelo; es independiente del 0,70 de confianza de la transcripción. */
    public static final double UMBRAL_INTENCION = 0.60;

    /**
     * @param probabilidadIntencion probabilidad del modelo cuando hubo predicción, aunque al final se usen las reglas
     * @param metodo                MODELO, REGLAS o null si ninguna de las dos reconoció la frase
     */
    public record ResultadoClasificacion(TipoIntencionVoz intencion, Map<String, String> slots,
                                         Double probabilidadIntencion, MetodoClasificacion metodo) {
    }

    private final ModeloIntencion modelo;

    public ClasificadorIntencion(ModeloIntencion modelo) {
        this.modelo = modelo;
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

        // Nivel 1 — modelo de PLN
        Optional<PrediccionIntencion> prediccion = modelo.predecir(normalizado);
        Double probabilidad = prediccion.map(PrediccionIntencion::probabilidad).orElse(null);
        if (prediccion.isPresent()
                && !prediccion.get().esFueraDeDominio()
                && prediccion.get().probabilidad() >= UMBRAL_INTENCION) {
            TipoIntencionVoz intencion = TipoIntencionVoz.valueOf(prediccion.get().intencion());
            return new ResultadoClasificacion(intencion, extraerSlots(intencion, normalizado),
                    probabilidad, MetodoClasificacion.MODELO);
        }

        // Nivel 2 — reglas de patrones
        for (Regla regla : REGLAS) {
            if (regla.patron().matcher(normalizado).find()) {
                return new ResultadoClasificacion(regla.intencion(), extraerSlots(regla.intencion(), normalizado),
                        probabilidad, MetodoClasificacion.REGLAS);
            }
        }
        return new ResultadoClasificacion(null, Map.of(), probabilidad, null);
    }

    /** Intención según las reglas de patrones, sin consultar el modelo (texto sin normalizar). */
    public TipoIntencionVoz intencionPorReglas(String texto) {
        String normalizado = NormalizadorVoz.normalizar(texto);
        for (Regla regla : REGLAS) {
            if (regla.patron().matcher(normalizado).find()) return regla.intencion();
        }
        return null;
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
