package com.gelox.backend.voz.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.gelox.backend.entities.MetodoClasificacion;
import com.gelox.backend.entities.TipoIntencionVoz;
import com.gelox.backend.voz.ClasificadorIntencion;
import com.gelox.backend.voz.ClasificadorIntencion.ResultadoClasificacion;
import com.gelox.backend.voz.ProcesadorLenguaje;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * FP-15 §2.11 — el LLM como implementación primaria de {@link ProcesadorLenguaje}.
 * Valida el JSON de §2.11.2 y, si algo falla, responde con {@link ClasificadorIntencion}
 * (modelo de PLN y reglas): el asistente nunca queda peor que en la Iteración 1.
 * <p>
 * Respaldo ante: LLM sin configurar, error de red, tiempo excedido, JSON inválido,
 * intención fuera del enumerado o null, y confianza fuera de [0, 1] o menor a
 * {@link ClasificadorIntencion#UMBRAL_INTENCION}.
 */
@Slf4j
@Primary
@Component
@RequiredArgsConstructor
public class ProcesadorLenguajeLlm implements ProcesadorLenguaje {

    private final LlmCliente cliente;
    private final ClasificadorIntencion respaldo;

    @Override
    public ResultadoClasificacion procesar(String texto) {
        if (!cliente.configurado()) return respaldo.clasificar(texto);

        JsonNode json;
        try {
            json = cliente.clasificar(texto);
        } catch (RuntimeException e) {
            log.warn("LLM no disponible, se usa el clasificador anterior: {}", e.getMessage());
            return respaldo.clasificar(texto);
        }
        ResultadoClasificacion resultado = aResultado(json);
        return resultado != null ? resultado : respaldo.clasificar(texto);
    }

    /** null si el JSON no cumple §2.11.2 o no alcanza el umbral. */
    static ResultadoClasificacion aResultado(JsonNode json) {
        if (json == null || !json.isObject()) return null;
        JsonNode intencionNodo = json.get("intencion");
        JsonNode confianzaNodo = json.get("confianza");
        if (intencionNodo == null || !intencionNodo.isTextual()
                || confianzaNodo == null || !confianzaNodo.isNumber()) return null;

        TipoIntencionVoz intencion;
        try {
            intencion = TipoIntencionVoz.valueOf(intencionNodo.asText());
        } catch (IllegalArgumentException e) {
            return null;
        }
        double confianza = confianzaNodo.asDouble();
        if (confianza < ClasificadorIntencion.UMBRAL_INTENCION || confianza > 1) return null;

        // Mismos nombres de slots que ClasificadorIntencion; los números pasan a texto.
        Map<String, String> slots = new LinkedHashMap<>();
        json.path("slots").fields().forEachRemaining(e -> {
            if (!e.getValue().isNull() && e.getValue().isValueNode()) slots.put(e.getKey(), e.getValue().asText());
        });
        return new ResultadoClasificacion(intencion, slots, confianza, MetodoClasificacion.LLM);
    }
}
