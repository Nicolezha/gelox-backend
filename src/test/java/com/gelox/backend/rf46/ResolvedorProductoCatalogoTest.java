package com.gelox.backend.rf46;

import com.gelox.backend.entities.Producto;
import com.gelox.backend.repositories.ProductoRepository;
import com.gelox.backend.voz.ResolvedorProducto;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.UnaryOperator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tolerancia de pronunciación sobre TODO el catálogo de templates/catalogo.xlsx:
 * cada producto se "dice" como lo diría alguien (sin empaque ni gramaje) y con
 * los errores típicos del reconocedor de voz, y debe resolver a ese producto
 * (o a otra presentación con el mismo nombre hablado, que es ambigüedad legítima).
 */
class ResolvedorProductoCatalogoTest {

    private static final List<Producto> CATALOGO = new ArrayList<>();
    private static ResolvedorProducto resolvedor;

    @BeforeAll
    static void cargarCatalogo() throws Exception {
        try (InputStream in = ResolvedorProductoCatalogoTest.class.getResourceAsStream("/templates/catalogo.xlsx");
             XSSFWorkbook wb = new XSSFWorkbook(in)) {
            Sheet hoja = wb.getSheetAt(0);
            DataFormatter fmt = new DataFormatter();
            for (Row fila : hoja) {
                String sku = fmt.formatCellValue(fila.getCell(0)).trim();
                String descripcion = fmt.formatCellValue(fila.getCell(1)).trim();
                if (!sku.matches("\\d+") || descripcion.isEmpty()) continue;
                Producto p = new Producto();
                p.setId(UUID.randomUUID());
                p.setCodigoTecnico(sku);
                p.setNombre(descripcion);
                p.setUnidadesPorCaja(24);
                CATALOGO.add(p);
            }
        }
        ProductoRepository repo = mock(ProductoRepository.class);
        when(repo.findByActivoTrue()).thenReturn(CATALOGO);
        resolvedor = new ResolvedorProducto(repo);
    }

    /** Cómo se transcribiría al decirlo mal: cada operador simula un tipo de error. */
    private static final List<UnaryOperator<String>> ERRORES = List.of(
            s -> s,                                                     // dicho bien
            s -> s.replace("v", "b").replace("ll", "y"),               // b/v, ll/y
            s -> s.replaceAll("(?<!c)h", "").replace("qu", "k"),      // h muda, qu/k
            s -> s.replace("ch", "sh").replace("z", "s"),              // ch/sh, z/s
            s -> s.replaceAll("c(?=[ei])", "s").replace("j", "h"),     // seseo, j aspirada
            s -> s.replace("cookies", "cukis").replace("cream", "crim").replace("brownie", "brauni")
                    .replace("jumbo", "yumbo").replace("flow", "flou").replace("baileys", "beilis")
                    .replace("tosh", "toch").replace("jet", "yet").replace("kids", "kits"),
            s -> s.replace("chococono", "choco cono").replace("xtraloco", "extra loco")
                    .replace("chocolisto", "choco listo").replace("trululu", "tru lu lu"),
            ResolvedorProductoCatalogoTest::quitarUnaLetra             // se come una letra
    );

    @Test
    @DisplayName("los 212 productos del Excel se reconocen aunque se pronuncien mal")
    void todoElCatalogoConErroresDePronunciacion() {
        List<String> fallos = new ArrayList<>();
        int intentos = 0;

        for (Producto objetivo : CATALOGO) {
            String hablado = ResolvedorProducto.nombreHablado(objetivo.getNombre());
            Set<String> variantes = new LinkedHashSet<>();
            for (UnaryOperator<String> error : ERRORES) variantes.add(error.apply(hablado));

            for (String dicho : variantes) {
                intentos++;
                List<ResolvedorProducto.ProductoCandidato> candidatos = resolvedor.resolver(dicho);
                String problema = verificar(objetivo, candidatos);
                if (problema != null) fallos.add("\"" + dicho + "\" -> " + problema);
            }
        }

        double acierto = 1.0 - (double) fallos.size() / intentos;
        System.out.printf("Catálogo: %d productos, %d frases, %.1f%% reconocidas%n",
                CATALOGO.size(), intentos, acierto * 100);
        fallos.stream().limit(40).forEach(f -> System.out.println("  " + f));
        assertThat(acierto).as("frases reconocidas").isGreaterThanOrEqualTo(0.97);
    }

    @Test
    @DisplayName("frases reales: solo el sabor y la marca, sin la línea ni el empaque")
    void frasesCortasReales() {
        assertThat(primero("chococono bakano")).containsAnyOf("Chococono Bakano", "Bkno");
        assertThat(primero("choco cono flou")).containsAnyOf("Chococono Flow", "Cono Flow");
        assertThat(primero("tosh pasion")).containsIgnoringCase("Tosh Paleta Pasion");
        assertThat(primero("touch pasion de 72 g")).contains("Tosh Paleta Pasion 72g");
        assertThat(primero("minions pago")).contains("Minions");
        assertThat(primero("polet baileys")).contains("Polet Paleta Baileys");
        assertThat(primero("polet beilis")).contains("Polet Paleta Baileys");
        assertThat(primero("aloja mango viche")).contains("Aloha Mango Biche");
        assertThat(primero("aloja mangoche")).contains("Aloha Mango Biche");
        assertThat(primero("aloha mango vichi")).contains("Aloha Mango Biche");
        assertThat(primero("mango biche")).contains("Aloha Mango Biche");
        assertThat(primero("dukan zanahoria")).contains("Dukan Paleta Zanahoria");
        assertThat(primero("minions")).contains("Minions");
        assertThat(primero("trululu")).containsIgnoringCase("Trululu");
        assertThat(primero("extra loco")).contains("Xtraloco");
        assertThat(primero("galleta napolitano")).containsIgnoringCase("Napolitan");
        assertThat(primero("tarro ron con pasas")).contains("Tarro Ron con Pasas");
        assertThat(primero("aloha tajin")).containsIgnoringCase("Tajin");
    }

    @Test
    @DisplayName("el tamaño dicho distingue presentaciones del mismo helado")
    void tamanoDesempata() {
        List<ResolvedorProducto.ProductoCandidato> c = resolvedor.resolver("polet chocoavellana de 90 gramos");
        assertThat(c.get(0).nombre()).contains("x90g");
        assertThat(c.get(0).score() - c.get(1).score()).isGreaterThanOrEqualTo(0.10);

        c = resolvedor.resolver("tosh pasion 72");
        assertThat(c.get(0).nombre()).contains("72g");
        assertThat(c.get(0).score() - c.get(1).score()).isGreaterThanOrEqualTo(0.10);
    }

    @Test
    @DisplayName("filas repetidas del Excel no generan la pregunta imposible \"¿X o X?\"")
    void nombresRepetidosNoSeOfrecenDosVeces() {
        List<ResolvedorProducto.ProductoCandidato> c = resolvedor.resolver("chococono bakano");
        assertThat(c.get(0).nombre()).isNotEqualTo(c.get(1).nombre());
    }

    @Test
    @DisplayName("lo que no está en el catálogo no se inventa")
    void noInventaProductos() {
        assertThat(resolvedor.resolver("panqueque")).isEmpty();
        assertThat(resolvedor.resolver("festival")).isEmpty();
        assertThat(resolvedor.resolver("coca cola")).isEmpty();
    }

    private static String primero(String dicho) {
        List<ResolvedorProducto.ProductoCandidato> c = resolvedor.resolver(dicho);
        assertThat(c).as("candidatos para \"%s\"", dicho).isNotEmpty();
        return c.get(0).nombre();
    }

    /**
     * null si está bien: el producto queda elegido, o es una de las dos opciones
     * que el asistente ofrece al preguntar "¿A o B?". Presentaciones del mismo
     * producto (mismas palabras habladas) cuentan como el mismo.
     */
    private static String verificar(Producto objetivo, List<ResolvedorProducto.ProductoCandidato> candidatos) {
        if (candidatos.isEmpty()) return "sin candidatos";
        Set<String> buscado = palabras(objetivo.getNombre());
        double mejor = candidatos.get(0).score();
        List<Set<String>> ofrecidos = new ArrayList<>();
        for (ResolvedorProducto.ProductoCandidato c : candidatos) {
            if (mejor - c.score() >= 0.10) break;
            Set<String> p = palabras(c.nombre());
            if (!ofrecidos.contains(p)) ofrecidos.add(p);
        }
        int posicion = ofrecidos.indexOf(buscado);
        if (posicion == 0 || posicion == 1) return null;
        return "no lo ofrece; ofrece \"" + String.join(" ", ofrecidos.get(0)) + "\"" + (ofrecidos.size() > 1
                ? " o \"" + String.join(" ", ofrecidos.get(1)) + "\"" : "");
    }

    private static Set<String> palabras(String nombre) {
        return new java.util.TreeSet<>(List.of(ResolvedorProducto.nombreHablado(nombre).split(" ")));
    }

    private static String quitarUnaLetra(String s) {
        // Quita una letra del medio de la palabra más larga (error típico de dicción rápida).
        String larga = "";
        for (String p : s.split(" ")) if (p.length() > larga.length()) larga = p;
        if (larga.length() < 6) return s;
        int i = larga.length() / 2;
        return s.replaceFirst(larga, larga.substring(0, i) + larga.substring(i + 1));
    }
}
