package com.gelox.backend.voz;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Payload de un {@link VozResultado} con {@code ok=false} cuando el handler le hizo una
 * pregunta al usuario ("¿cuál de las 3 recomendaciones?", "¿qué producto?"). VozService
 * la recuerda y la siguiente frase se toma como la respuesta, sin que el usuario tenga
 * que repetir todo el comando. Hay tres formas de usar la respuesta:
 * <ul>
 *   <li>por defecto, se vuelve a llamar al mismo handler con el texto original, estos
 *   {@code slots} y la respuesta en {@value #SLOT_RESPUESTA};</li>
 *   <li>{@link #reemplazando}: la respuesta reemplaza un fragmento del texto original
 *   ("¿Aloha Limón o Aloha Fresa?" → "la de limón") y el comando se interpreta de nuevo;</li>
 *   <li>{@link #anexando}: la respuesta se agrega al final del texto original ("¿a cuál pedido?"
 *   → "al primero") y el comando se interpreta de nuevo.</li>
 * </ul>
 */
public record PreguntaVoz(Map<String, String> slots) {

    /** Slot donde llega, tal cual, lo que el usuario respondió a la pregunta. */
    public static final String SLOT_RESPUESTA = "respuesta";

    /** Fragmento del texto original (normalizado) que la respuesta reemplaza. */
    static final String SLOT_REEMPLAZAR = "_reemplazar";
    /** Opciones ofrecidas, separadas por "|", para responder "la primera" / "la segunda". */
    static final String SLOT_OPCIONES = "_opciones";
    /** Presente: la respuesta se agrega al texto original; su valor antecede a un número suelto ("pedido "). */
    static final String SLOT_ANEXAR = "_anexar";

    /** Pregunta cuya respuesta reemplaza {@code fragmento} en el comando original. */
    public static VozResultado reemplazando(String pregunta, String fragmento, List<String> opciones) {
        Map<String, String> slots = new LinkedHashMap<>();
        slots.put(SLOT_REEMPLAZAR, fragmento == null ? "" : fragmento);
        Map<String, Object> datos = new LinkedHashMap<>();
        if (opciones != null && !opciones.isEmpty()) {
            slots.put(SLOT_OPCIONES, String.join("|", opciones));
            datos.put("opciones", opciones);
        }
        return new VozResultado(false, pregunta, datos, new PreguntaVoz(slots));
    }

    /** Pregunta cuya respuesta se agrega al comando original; un número suelto va precedido de {@code prefijoNumero}. */
    public static VozResultado anexando(String pregunta, String prefijoNumero) {
        return new VozResultado(false, pregunta, Map.of(),
                new PreguntaVoz(Map.of(SLOT_ANEXAR, prefijoNumero == null ? "" : prefijoNumero)));
    }

    boolean reinterpreta() {
        return slots.containsKey(SLOT_REEMPLAZAR) || slots.containsKey(SLOT_ANEXAR);
    }
}
