package com.gelox.backend.voz;

import com.gelox.backend.entities.Producto;
import com.gelox.backend.repositories.ProductoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Resuelve un fragmento dicho a viva voz ("chococono bakano", "polet beilis")
 * contra el catálogo de productos activos. No usa ningún servicio externo.
 * <p>
 * Tolera la pronunciación y el reconocedor de voz en tres pasos:
 * <ol>
 *   <li>Del nombre del catálogo deja solo lo que alguien diría: sin empaque,
 *   gramaje ni siglas ("Aloha Mango Biche caja x24un x65g" → "aloha mango biche").</li>
 *   <li>Compara por sonido en español: b/v, s/z/c, ll/y, j/g/h, k/qu/c, sh/ch,
 *   letras dobles e inglés aproximado ("cukis" = "cookies").</li>
 *   <li>Empareja palabra por palabra sin importar el orden ni lo que no se dijo, y
 *   tolera palabras partidas o pegadas ("choco cono" = "chococono").</li>
 * </ol>
 * Si el segundo candidato queda a menos de 0.10 del primero, el resultado es
 * ambiguo y quien use esta lista debe preguntar cuál de los dos quiso decir.
 */
@Component
@RequiredArgsConstructor
public class ResolvedorProducto {

    private static final double PUNTAJE_MINIMO = 0.75;

    /** Una palabra dicha cuenta como "esa palabra del nombre" desde esta similitud. */
    private static final double SIMILITUD_PALABRA = 0.75;

    /** Si se dijo un tamaño ("de 90 gramos") que el nombre no tiene, ese producto pierde frente al que sí. */
    private static final double CASTIGO_TAMANO = 0.85;

    private static final Pattern NUMERO = Pattern.compile("\\d+");

    /** Palabras que no distinguen un producto de otro (artículos, marca del fabricante, empaque). */
    private static final Set<String> RELLENO = Set.of(
            "de", "del", "la", "el", "los", "las", "y", "con", "and", "a", "al", "en", "por", "sabor",
            "crem", "helado", "helados", "cremhelado",
            "x", "caja", "cajas", "cj", "cjs", "bolsa", "un", "und", "unds", "u", "g", "gr", "grs", "ml",
            "litros", "l", "cm", "sp", "ni", "nt", "nf", "ne", "pl", "mundial", "cmu",
            "pague", "lleve", "gratis", "gramos", "gramo", "unidades", "unidad",
            "pago", "efectivo", "transferencia");

    /** Abreviaturas y errores de digitación del catálogo. */
    private static final Map<String, String> ABREVIATURAS = Map.ofEntries(
            Map.entry("pal", "paleta"), Map.entry("paeta", "paleta"), Map.entry("gta", "galleta"),
            Map.entry("trad", "tradicional"), Map.entry("dul", "dulce"), Map.entry("surt", "surtido"),
            Map.entry("cerez", "cerezada"), Map.entry("bkno", "bakano"), Map.entry("prom", "promo"), Map.entry("np", "napolitano"),
            Map.entry("napolitan", "napolitano"), Map.entry("polket", "polet"), Map.entry("caramel", "caramelo"));

    private final ProductoRepository productoRepository;

    public record ProductoCandidato(UUID id, String nombre, double score, Integer unidadesPorCaja) {
    }

    public List<ProductoCandidato> resolver(String fragmento) {
        List<String> dichas = palabrasSinRepetir(fragmento);
        if (dichas.isEmpty()) return List.of();
        Set<String> numerosDichos = numeros(fragmento);

        // El catálogo repite el mismo producto con otro SKU o el empaque escrito distinto
        // ("Chococono Bakano Caja x 24 Un x95g" / "Chococono Bakano x95g"): preguntar "¿X o X?"
        // no tiene respuesta. Mismo nombre hablado y mismo tamaño = uno solo, el de más stock.
        Map<String, Producto> unoPorNombre = new LinkedHashMap<>();
        for (Producto p : productoRepository.findByActivoTrue()) {
            String clave = nombreHablado(p.getNombre()) + "|" + tamanos(p.getNombre());
            unoPorNombre.merge(clave, p, (a, b) -> stock(b) > stock(a) ? b : a);
        }

        return unoPorNombre.values().stream()
                .map(producto -> new ProductoCandidato(
                        producto.getId(),
                        producto.getNombre(),
                        puntaje(dichas, palabrasSinRepetir(producto.getNombre()))
                                * castigoPorTamano(numerosDichos, producto.getNombre()),
                        producto.getUnidadesPorCaja()))
                .filter(candidato -> candidato.score() >= PUNTAJE_MINIMO)
                .sorted(Comparator.comparingDouble(ProductoCandidato::score).reversed())
                .toList();
    }

    private static final Pattern TAMANO = Pattern.compile("(\\d+)\\s*(g|gr|grs|ml|l|lt|litros?)\\b");

    /** "x 85 g" y "x85g" → "85": el tamaño, sin contar cuántas unidades trae la caja. */
    private static String tamanos(String nombre) {
        Set<String> encontrados = new java.util.TreeSet<>();
        Matcher m = TAMANO.matcher(NormalizadorVoz.normalizar(nombre).replace("x", " "));
        while (m.find()) encontrados.add(m.group(1));
        return String.join(",", encontrados);
    }

    private static int stock(Producto p) {
        return p.getStockActual() == null ? 0 : p.getStockActual();
    }

    /**
     * Dos presentaciones del mismo helado solo se distinguen por el número
     * ("x80g" / "x90g"): si se dijo un número y el nombre tiene números pero
     * ninguno coincide, ese producto queda por debajo.
     */
    private static double castigoPorTamano(Set<String> numerosDichos, String nombre) {
        if (numerosDichos.isEmpty()) return 1.0;
        Set<String> delNombre = numeros(nombre);
        if (delNombre.isEmpty()) return 1.0;
        return delNombre.stream().anyMatch(numerosDichos::contains) ? 1.0 : CASTIGO_TAMANO;
    }

    private static Set<String> numeros(String texto) {
        Set<String> encontrados = new LinkedHashSet<>();
        Matcher m = NUMERO.matcher(NormalizadorVoz.normalizar(texto));
        while (m.find()) encontrados.add(m.group().replaceFirst("^0+(?=\\d)", ""));
        return encontrados;
    }

    /** El nombre como lo diría alguien: "Pal. Aloha Tajin x65g x24un" → "paleta aloha tajin". */
    public static String nombreHablado(String nombre) {
        String t = NormalizadorVoz.normalizar(nombre)
                .replaceAll("[^a-z0-9 ]", " ")
                .replaceAll("([a-z]{2,})x(\\d)", "$1 x$2")   // "npx50" → "np x50"
                .replaceAll("(?<=[a-z])(?=\\d)", " ");       // "tarro10l" → "tarro 10l"

        Set<String> palabras = new LinkedHashSet<>();
        for (String p : t.split("\\s+")) {
            if (p.isEmpty() || Character.isDigit(p.charAt(0)) || p.matches("x\\d.*")) continue;
            p = ABREVIATURAS.getOrDefault(p, p);
            if (!RELLENO.contains(p)) palabras.add(p);
        }
        return String.join(" ", palabras);
    }

    private static List<String> palabrasFoneticas(String texto) {
        String hablado = nombreHablado(texto);
        if (hablado.isEmpty()) return List.of();
        return Arrays.stream(hablado.split(" ")).map(ResolvedorProducto::fonetica).toList();
    }

    /** "Chococono Prom Cono Bkno" → "cono" ya está dicho dentro de "chococono". */
    private static List<String> palabrasSinRepetir(String texto) {
        List<String> palabras = palabrasFoneticas(texto);
        return palabras.stream()
                .filter(p -> palabras.stream().noneMatch(otra -> !otra.equals(p) && otra.endsWith(p)))
                .toList();
    }

    /** Clave de sonido en español (con algo de inglés aproximado) para comparar lo dicho. */
    static String fonetica(String palabra) {
        String p = palabra
                .replace("ph", "f").replace("sh", "ch").replace("ck", "k").replace("qu", "k")
                .replace("oo", "u").replace("ee", "i").replace("ea", "i").replace("w", "u")
                .replaceAll("c(?=[ei])", "s").replaceAll("g(?=[ei])", "j")
                .replace("ch", "#")
                .replace("c", "k").replace("z", "s").replace("v", "b").replace("ll", "y")
                .replace("x", "ks").replace("j", "h").replace("y", "i")
                .replace("h", "")
                .replace("#", "ch");
        return p.replaceAll("(.)\\1+", "$1");
    }

    /**
     * Qué tan bien lo dicho describe el nombre (0..1). Lo dicho pesa entero;
     * lo que el nombre tiene de más solo resta un poco, para que "festival"
     * prefiera "Festival" sobre "Festival Mini" sin descartarlo.
     */
    private static double puntaje(List<String> dichas, List<String> nombre) {
        if (nombre.isEmpty()) return 0.0;

        // 1) Palabra por palabra, sin importar el orden.
        double suma = 0;
        Set<Integer> cubiertas = new LinkedHashSet<>();
        for (String dicha : dichas) {
            double mejor = 0;
            int indiceMejor = -1;
            for (int i = 0; i < nombre.size(); i++) {
                double s = similitud(dicha, nombre.get(i));
                if (s > mejor) {
                    mejor = s;
                    indiceMejor = i;
                }
            }
            if (mejor >= SIMILITUD_PALABRA) cubiertas.add(indiceMejor);
            suma += mejor >= 0.5 ? mejor : 0;
        }
        double porPalabras = suma / dichas.size();
        double coberturaPalabras = (double) cubiertas.size() / nombre.size();

        // 2) Todo junto contra cada tramo seguido del nombre: palabras partidas o pegadas.
        String dichoJunto = String.join("", dichas);
        double porTramo = 0;
        double coberturaTramo = 0;
        for (int i = 0; i < nombre.size(); i++) {
            StringBuilder tramo = new StringBuilder();
            for (int j = i; j < nombre.size(); j++) {
                tramo.append(nombre.get(j));
                double s = similitud(dichoJunto, tramo.toString());
                if (s > porTramo) {
                    porTramo = s;
                    coberturaTramo = (double) (j - i + 1) / nombre.size();
                }
            }
        }

        double base = Math.max(porPalabras, porTramo);
        double cobertura = porPalabras >= porTramo ? coberturaPalabras : coberturaTramo;
        return base * (0.9 + 0.1 * cobertura);
    }

    /**
     * 1 - Levenshtein relativo. En palabras cortas solo se perdona una vocal de más o de
     * menos ("touch" ≈ "tosh"): cambiar una consonante ya es otra palabra ("coca" ≠ "kola").
     */
    private static double similitud(String a, String b) {
        if (a.equals(b)) return 1.0;
        if (Math.min(a.length(), b.length()) <= 4) {
            boolean mismasConsonantes = a.replaceAll("[aeiou]", "").equals(b.replaceAll("[aeiou]", ""));
            return mismasConsonantes && distanciaLevenshtein(a, b) == 1 ? 0.8 : 0.0;
        }
        return 1.0 - (double) distanciaLevenshtein(a, b) / Math.max(a.length(), b.length());
    }

    private static int distanciaLevenshtein(String a, String b) {
        int[] previa = new int[b.length() + 1];
        int[] actual = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) previa[j] = j;

        for (int i = 1; i <= a.length(); i++) {
            actual[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int costo = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                actual[j] = Math.min(Math.min(previa[j] + 1, actual[j - 1] + 1), previa[j - 1] + costo);
            }
            int[] tmp = previa;
            previa = actual;
            actual = tmp;
        }
        return previa[b.length()];
    }
}
