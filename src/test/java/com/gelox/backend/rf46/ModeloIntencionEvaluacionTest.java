package com.gelox.backend.rf46;

import com.gelox.backend.voz.pln.ModeloIntencion;
import opennlp.tools.cmdline.doccat.DoccatFineGrainedReportListener;
import opennlp.tools.doccat.DoccatModel;
import opennlp.tools.doccat.DocumentCategorizerME;
import opennlp.tools.doccat.DocumentSample;
import opennlp.tools.doccat.DocumentCategorizerEvaluator;
import opennlp.tools.doccat.DocumentSampleStream;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.PlainTextByLineStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RF46 / FP-15 §3.3.5 — evalúa el modelo de intención entrenado contra el corpus
 * de prueba (frases distintas a las de entrenamiento) e imprime la matriz de
 * confusión. Criterio de aceptación: exactitud ≥ 0,90.
 */
class ModeloIntencionEvaluacionTest {

    private static final double EXACTITUD_MINIMA = 0.90;
    private static final String CORPUS_PRUEBA = "pln/intenciones-prueba.txt";

    @Test
    @DisplayName("FP-15 §3.3.5 — el modelo clasifica el corpus de prueba con exactitud ≥ 0,90")
    void exactitudSobreCorpusDePrueba() throws Exception {
        DoccatModel modelo;
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(ModeloIntencion.RUTA_MODELO)) {
            assertThat(in).as("el modelo %s debe estar en el classpath", ModeloIntencion.RUTA_MODELO).isNotNull();
            modelo = new DoccatModel(in);
        }

        ByteArrayOutputStream reporte = new ByteArrayOutputStream();
        DoccatFineGrainedReportListener listener = new DoccatFineGrainedReportListener(reporte);
        DocumentCategorizerEvaluator evaluador = new DocumentCategorizerEvaluator(new DocumentCategorizerME(modelo), listener);

        try (ObjectStream<String> lineas = new PlainTextByLineStream(
                () -> getClass().getClassLoader().getResourceAsStream(CORPUS_PRUEBA), StandardCharsets.UTF_8);
             ObjectStream<DocumentSample> muestras = new DocumentSampleStream(lineas)) {
            evaluador.evaluate(muestras);
        }
        listener.writeReport();

        System.out.println(reporte.toString(StandardCharsets.UTF_8));
        System.out.printf("Frases evaluadas: %d — exactitud: %.4f%n",
                evaluador.getDocumentCount(), evaluador.getAccuracy());

        assertThat(evaluador.getAccuracy()).isGreaterThanOrEqualTo(EXACTITUD_MINIMA);
    }
}
