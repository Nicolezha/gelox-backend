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
public class ClasificadorIntencion implements ProcesadorLenguaje {

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

    /** "cierra el día", "cerremos la caja", "cuadre de caja", "resumen del día". */
    private static final Pattern CIERRE = Pattern.compile(
            "\\b(cierra|cierre|cerrar|cerremos|cerramos|cuadre|cuadra|cuadrar|cuadremos)\\b.*\\b(dia|caja)\\b"
                    + "|resumen del dia");

    /** Verbos que cambian un pedido ya hecho. */
    private static final String MODIFICAR = "\\b(agreg|anad|sum|mete|metele|incluye|quit|saca|elimin|borr|cambi|actualiz|modific|corrig|deja)\\w*";

    /**
     * El orden importa:
     * <ul>
     *   <li>navegación va antes para reconocer "ve a X pestaña", "abre la pantalla de X";</li>
     *   <li>finanzas va primero: "cuánto vendimos" contiene "vendi" y no es una venta;</li>
     *   <li>los pedidos a proveedor van antes que la venta, salvo que sea "rural"
     *   (un "pedido rural" es una venta rural, RF48);</li>
     *   <li>modificar va antes que generar: "al pedido pendiente agrégale…" también dice "pedido".</li>
     * </ul>
     */
    private static final List<Regla> REGLAS = List.of(
            new Regla(TipoIntencionVoz.NAVEGACION, Pattern.compile(
                    "\\b(ve a|ir a|ve a la|abre|ir al|llevame a|llévame a|navega a|pasa a|muestrame|abre la|pantalla de|pestana de|modulo de)\\b.*"
                            + "\\b(ventanilla|ventas|rural|comerciante|comerciantes|cliente|clientes|inventario|inventarios|catalogo|pedidos|pedido|reporte|reportes|cierre|cierres|usuario|usuarios|dashboard|inicio|ajustes|merma|entrada)\\b")),
            new Regla(TipoIntencionVoz.CONSULTAR_FINANZAS, Pattern.compile(
                    "cuant[oa]s?\\b.*\\b(ganamos|ganado|vendimos|vendido|vendio|vendieron|facturamos|hicimos|entro|entraron|llevamos)\\b"
                            + "|cuant[oa]s? ventas|cuanta plata|cuanto dinero"
                            + "|\\b(ganancias?|utilidad\\w*|ingresos|margen|rentabilidad|balance)\\b"
                            + "|\\bventas (de |del |de la |de este |de esta )?(hoy|ayer|dia|semana|mes|ano|enero|febrero|marzo|abril|mayo|junio|julio|agosto|septiembre|setiembre|octubre|noviembre|diciembre)\\b"
                            + "|\\b(enero|febrero|marzo|abril|mayo|junio|julio|agosto|septiembre|setiembre|octubre|noviembre|diciembre)\\b.*\\b(20\\d\\d)?\\b"
                            + "|\\bcomo (nos )?(fue|va|vamos|estamos)\\b"
                            + "|" + CIERRE.pattern())),
            new Regla(TipoIntencionVoz.MODIFICAR_PEDIDO, Pattern.compile(
                    "^(?!.*\\brural).*(" + MODIFICAR + ".*\\bpedido|\\bpedido\\b.*" + MODIFICAR + ")")),
            new Regla(TipoIntencionVoz.GENERAR_PEDIDO, Pattern.compile(
                    "^(?!.*\\brural).*(\\b(genera|crea|haz|hace|hacer|hagamos|arma|realiza|monta|pide|pidele|pedir|encarga|necesito|quiero|nuevo)\\w*.*\\bpedido"
                            + "|\\bpedido (nuevo|al proveedor|a proveedor)|\\bpidele\\b.*\\bproveedor|\\bproveedor\\b)")),
            new Regla(TipoIntencionVoz.REGISTRAR_VENTA, Pattern.compile(
                    "registr|\\bvend|anota|\\bcobr|factur|\\bventa\\b|\\brural\\b")),
            new Regla(TipoIntencionVoz.CONSULTAR_INVENTARIO, Pattern.compile(
                    "cuant[oa]s?\\b.*\\b(quedan?|hay|tenemos|tengo|sobran?|disponibles?)\\b"
                            + "|\\bqueda(n)?\\b|stock|inventario|existencia|disponib|agotad|acaba|\\bhay\\b|minimo"))
    );

    /** Lo usan el clasificador y ConsultaFinancieraHandler para saber si piden el cierre del día. */
    public static boolean esCierre(String textoNormalizado) {
        return CIERRE.matcher(textoNormalizado).find();
    }

    private static final Pattern CANTIDAD = Pattern.compile("\\b(\\d+)\\b");
    private static final Pattern PRODUCTO = Pattern.compile("\\bde\\s+(.+)$");
    private static final Pattern RELLENO_PRODUCTO = Pattern.compile("\\b(quedan|hay|por favor|ya|pendiente)\\b");

    @Override
    public ResultadoClasificacion procesar(String texto) {
        return clasificar(texto);
    }

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

        if (intencion == TipoIntencionVoz.NAVEGACION) {
            slots.put("destino", texto);
        }

        if (intencion == TipoIntencionVoz.CONSULTAR_FINANZAS) {
            if (esCierre(texto)) {
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
