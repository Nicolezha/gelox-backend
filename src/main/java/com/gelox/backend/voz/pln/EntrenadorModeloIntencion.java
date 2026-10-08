package com.gelox.backend.voz.pln;

import opennlp.tools.doccat.BagOfWordsFeatureGenerator;
import opennlp.tools.doccat.DoccatFactory;
import opennlp.tools.doccat.DoccatModel;
import opennlp.tools.doccat.DocumentCategorizerME;
import opennlp.tools.doccat.DocumentSample;
import opennlp.tools.doccat.DocumentSampleStream;
import opennlp.tools.doccat.FeatureGenerator;
import opennlp.tools.doccat.NGramFeatureGenerator;
import opennlp.tools.util.InputStreamFactory;
import opennlp.tools.util.MarkableFileInputStreamFactory;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.PlainTextByLineStream;
import opennlp.tools.util.TrainingParameters;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 * Entrena el modelo de intención (FP-15 §3.3.5). No es un bean: se ejecuta a mano.
 * <pre>
 * java -cp target/classes:&lt;classpath&gt; com.gelox.backend.voz.pln.EntrenadorModeloIntencion [corpus] [salida]
 * </pre>
 * Parámetros: MAXENT, 100 iteraciones, cutoff 1, bolsa de palabras + bigramas.
 */
public final class EntrenadorModeloIntencion {

    public static final String CORPUS_POR_DEFECTO = "src/main/resources/pln/intenciones-entrenamiento.txt";
    public static final String SALIDA_POR_DEFECTO = "src/main/resources/pln/modelo-intencion-es.bin";

    private EntrenadorModeloIntencion() {
    }

    public static void main(String[] args) throws Exception {
        String corpus = args.length > 0 ? args[0] : CORPUS_POR_DEFECTO;
        String salida = args.length > 1 ? args[1] : SALIDA_POR_DEFECTO;

        DoccatModel modelo = entrenar(new File(corpus));
        try (OutputStream out = new BufferedOutputStream(new FileOutputStream(salida))) {
            modelo.serialize(out);
        }
        System.out.println("Modelo de intención escrito en " + salida);
    }

    public static DoccatModel entrenar(File corpus) throws Exception {
        TrainingParameters parametros = new TrainingParameters();
        parametros.put(TrainingParameters.ALGORITHM_PARAM, "MAXENT");
        parametros.put(TrainingParameters.ITERATIONS_PARAM, 100);
        parametros.put(TrainingParameters.CUTOFF_PARAM, 1);

        DoccatFactory factory = new DoccatFactory(new FeatureGenerator[]{
                new BagOfWordsFeatureGenerator(),
                new NGramFeatureGenerator(2, 2)
        });

        InputStreamFactory entrada = new MarkableFileInputStreamFactory(corpus);
        try (ObjectStream<String> lineas = new PlainTextByLineStream(entrada, StandardCharsets.UTF_8);
             ObjectStream<DocumentSample> muestras = new DocumentSampleStream(lineas)) {
            return DocumentCategorizerME.train("es", muestras, parametros, factory);
        }
    }
}
