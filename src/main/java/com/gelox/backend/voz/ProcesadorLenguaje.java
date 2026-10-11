package com.gelox.backend.voz;

/**
 * Puerto arquitectural para el Procesamiento del Lenguaje Natural (NLU).
 * <p>
 * En la <b>Iteración 1</b>, su implementación primaria {@link ClasificadorIntencion}
 * ejecuta reglas determinísticas de patrones, extracción de slots y normalización colombiana.
 * <p>
 * En la <b>Iteración 2</b> (según Planeación E1_2), permite conectar de forma transparente
 * adaptadores de Modelos de Lenguaje (LLMs como Claude, Gemini o modelos locales) mediante
 * inyección de dependencias de Spring Boot (@Primary o @Profile("llm")), garantizando
 * extensibilidad y bajo acoplamiento arquitectónico.
 */
public interface ProcesadorLenguaje {

    /**
     * Clasifica el texto transcrito de la voz, infiere la intención y extrae los slots semánticos.
     *
     * @param texto Comando transcrito por voz.
     * @return Resultado de la clasificación conteniendo la intención y los slots extraídos.
     */
    ClasificadorIntencion.ResultadoClasificacion procesar(String texto);
}
