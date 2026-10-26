package com.gelox.backend.voz.llm;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * FP-15 §2.11.1 — envía el texto transcrito al LLM con una petición tipo chat completions
 * (system = prompt de §2.11.3, user = texto), temperatura 0 y salida JSON. Cambiar de
 * proveedor es solo cambiar las variables de entorno.
 * <p>
 * Solo viaja el texto: ni clientes, ni precios, ni inventario.
 */
@Component
@EnableConfigurationProperties(LlmProperties.class)
public class LlmCliente {

    private static final String PROMPT = "prompts/nlu-sistema.txt";

    private final LlmProperties propiedades;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;
    private final String promptSistema;

    public LlmCliente(LlmProperties propiedades, ObjectMapper objectMapper) {
        this.propiedades = propiedades;
        this.objectMapper = objectMapper;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(propiedades.timeoutMs()));
        factory.setReadTimeout(Duration.ofMillis(propiedades.timeoutMs()));
        this.restClient = RestClient.builder().requestFactory(factory).build();
        this.promptSistema = leerPrompt();
    }

    public boolean configurado() {
        return propiedades.configurado();
    }

    /**
     * Devuelve el JSON de §2.11.2 tal como lo respondió el modelo, sin validar.
     * Lanza excepción ante error de red, tiempo excedido o contenido que no es JSON.
     */
    public JsonNode clasificar(String texto) {
        Map<String, Object> peticion = Map.of(
                "model", propiedades.modelo(),
                "temperature", 0,
                "response_format", Map.of("type", "json_object"),
                "messages", List.of(
                        Map.of("role", "system", "content", promptSistema),
                        Map.of("role", "user", "content", texto)));

        JsonNode respuesta = restClient.post()
                .uri(propiedades.url())
                .header("Authorization", "Bearer " + propiedades.apiKey())
                .contentType(MediaType.APPLICATION_JSON)
                .body(peticion)
                .retrieve()
                .body(JsonNode.class);

        String contenido = respuesta == null ? ""
                : respuesta.path("choices").path(0).path("message").path("content").asText("");
        try {
            return objectMapper.readTree(contenido);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("El LLM no respondió un JSON válido", e);
        }
    }

    private static String leerPrompt() {
        try {
            return new ClassPathResource(PROMPT).getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo leer " + PROMPT, e);
        }
    }
}
