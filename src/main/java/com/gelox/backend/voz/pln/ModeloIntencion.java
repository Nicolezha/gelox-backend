package com.gelox.backend.voz.pln;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import opennlp.tools.doccat.DoccatModel;
import opennlp.tools.doccat.DocumentCategorizerME;
import opennlp.tools.tokenize.SimpleTokenizer;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.Optional;

/**
 * Clasificador de intención entrenado con Apache OpenNLP (FP-15 §2.4, §3.3.1).
 * <p>
 * El {@link DoccatModel} se carga una vez y se comparte; {@link DocumentCategorizerME}
 * no es thread-safe, así que se crea uno por petición. Si el modelo no carga, el
 * asistente sigue funcionando solo con reglas (FP-15 §2.6.3).
 */
@Slf4j
@Component
public class ModeloIntencion {

    public static final String RUTA_MODELO = "pln/modelo-intencion-es.bin";

    private volatile DoccatModel modelo;

    @PostConstruct
    public void cargar() {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(RUTA_MODELO)) {
            if (in == null) {
                log.error("No se encontró el modelo de intención '{}' en el classpath; se usarán solo reglas.", RUTA_MODELO);
                return;
            }
            modelo = new DoccatModel(in);
            log.info("Modelo de intención cargado: {}", RUTA_MODELO);
        } catch (Exception e) {
            log.error("No se pudo cargar el modelo de intención '{}'; se usarán solo reglas.", RUTA_MODELO, e);
            modelo = null;
        }
    }

    public boolean disponible() {
        return modelo != null;
    }

    /** @param textoNormalizado texto ya pasado por {@code NormalizadorVoz.normalizar} */
    public Optional<PrediccionIntencion> predecir(String textoNormalizado) {
        DoccatModel actual = modelo;
        if (actual == null || textoNormalizado == null || textoNormalizado.isBlank()) {
            return Optional.empty();
        }
        String[] tokens = SimpleTokenizer.INSTANCE.tokenize(textoNormalizado);
        DocumentCategorizerME categorizador = new DocumentCategorizerME(actual);
        double[] probabilidades = categorizador.categorize(tokens);
        String mejor = categorizador.getBestCategory(probabilidades);
        double probabilidad = probabilidades[categorizador.getIndex(mejor)];
        return Optional.of(new PrediccionIntencion(mejor, probabilidad));
    }
}
