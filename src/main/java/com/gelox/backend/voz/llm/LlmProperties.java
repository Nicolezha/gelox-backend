package com.gelox.backend.voz.llm;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.StringUtils;

/**
 * FP-15 §2.11.1 — conexión al LLM. Los valores salen de las variables de entorno de Render
 * LLM_API_URL, LLM_API_KEY y LLM_MODELO; nunca van en el repositorio.
 */
@ConfigurationProperties("llm")
public record LlmProperties(String url, String apiKey, String modelo, @DefaultValue("2500") int timeoutMs) {

    /** Sin url, clave o modelo el asistente trabaja solo con el clasificador anterior. */
    public boolean configurado() {
        return StringUtils.hasText(url) && StringUtils.hasText(apiKey) && StringUtils.hasText(modelo);
    }
}
