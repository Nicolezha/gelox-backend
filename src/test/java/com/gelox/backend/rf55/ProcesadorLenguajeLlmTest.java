package com.gelox.backend.rf55;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gelox.backend.entities.MetodoClasificacion;
import com.gelox.backend.entities.TipoIntencionVoz;
import com.gelox.backend.voz.ClasificadorIntencion;
import com.gelox.backend.voz.ClasificadorIntencion.ResultadoClasificacion;
import com.gelox.backend.voz.llm.LlmCliente;
import com.gelox.backend.voz.llm.ProcesadorLenguajeLlm;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.ResourceAccessException;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * T115-PR1 — ProcesadorLenguajeLlm: el JSON válido del LLM se usa tal cual; cualquier falla
 * (red, tiempo, JSON inválido, intención fuera del enum o null, confianza &lt; 0,60) cae al
 * clasificador de la Iteración 1 (FP-15 §2.11.1).
 */
@ExtendWith(MockitoExtension.class)
class ProcesadorLenguajeLlmTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String TEXTO = "cambia el mango biche a tres cajas";
    private static final ResultadoClasificacion RESPALDO =
            new ResultadoClasificacion(TipoIntencionVoz.REGISTRAR_VENTA, Map.of(), 0.8, MetodoClasificacion.MODELO);

    @Mock
    LlmCliente cliente;

    @Mock
    ClasificadorIntencion clasificador;

    @InjectMocks
    ProcesadorLenguajeLlm procesador;

    @BeforeEach
    void setUp() {
        when(cliente.configurado()).thenReturn(true);
    }

    private void llmResponde(String json) throws Exception {
        when(cliente.clasificar(TEXTO)).thenReturn(JSON.readTree(json));
    }

    private void esperaRespaldo() {
        when(clasificador.clasificar(TEXTO)).thenReturn(RESPALDO);
        assertThat(procesador.procesar(TEXTO)).isSameAs(RESPALDO);
        verify(clasificador).clasificar(TEXTO);
    }

    @Test
    @DisplayName("JSON válido → intención del LLM, slots como texto y método LLM")
    void jsonValido_usaElLlm() throws Exception {
        llmResponde("""
                {"intencion":"DECIDIR_RECOMENDACION","confianza":0.92,
                 "slots":{"accion":"MODIFICAR","producto":"mango biche","cantidad":3,"unidad":"CAJA","destinatario":null}}""");

        ResultadoClasificacion r = procesador.procesar(TEXTO);

        assertThat(r.intencion()).isEqualTo(TipoIntencionVoz.DECIDIR_RECOMENDACION);
        assertThat(r.metodo()).isEqualTo(MetodoClasificacion.LLM);
        assertThat(r.probabilidadIntencion()).isEqualTo(0.92);
        assertThat(r.slots()).containsExactlyInAnyOrderEntriesOf(Map.of(
                "accion", "MODIFICAR", "producto", "mango biche", "cantidad", "3", "unidad", "CAJA"));
        verifyNoInteractions(clasificador);
    }

    @Test
    @DisplayName("presupuesto en número → texto sin decimales ni notación científica")
    void presupuesto_seConvierteATexto() throws Exception {
        llmResponde("""
                {"intencion":"CONSULTAR_RECOMENDACION","confianza":0.97,"slots":{"presupuesto":2000000}}""");

        assertThat(procesador.procesar(TEXTO).slots()).containsEntry("presupuesto", "2000000");
    }

    @Test
    @DisplayName("confianza exactamente 0,60 todavía es del LLM")
    void umbralExacto_usaElLlm() throws Exception {
        llmResponde("""
                {"intencion":"NAVEGACION","confianza":0.60,"slots":{"destino":"reportes"}}""");

        assertThat(procesador.procesar(TEXTO).metodo()).isEqualTo(MetodoClasificacion.LLM);
    }

    @ParameterizedTest(name = "{0}")
    @DisplayName("respuesta que no cumple §2.11.2 → respaldo con MODELO o REGLAS")
    @ValueSource(strings = {
            "{\"intencion\":\"DECIDIR_RECOMENDACION\",\"confianza\":0.55,\"slots\":{}}",
            "{\"intencion\":null,\"confianza\":0.9,\"slots\":{}}",
            "{\"intencion\":\"PEDIR_PIZZA\",\"confianza\":0.95,\"slots\":{}}",
            "{\"intencion\":\"NAVEGACION\",\"confianza\":1.4,\"slots\":{}}",
            "{\"intencion\":\"NAVEGACION\",\"confianza\":\"alta\",\"slots\":{}}",
            "{\"intencion\":\"NAVEGACION\",\"slots\":{}}",
            "[\"NAVEGACION\"]"
    })
    void respuestaInvalida_usaElRespaldo(String json) throws Exception {
        llmResponde(json);
        esperaRespaldo();
    }

    @Test
    @DisplayName("JSON mal formado → respaldo")
    void jsonMalFormado_usaElRespaldo() {
        when(cliente.clasificar(TEXTO)).thenThrow(new IllegalStateException("El LLM no respondió un JSON válido"));
        esperaRespaldo();
    }

    @Test
    @DisplayName("error de red o tiempo excedido → respaldo")
    void timeout_usaElRespaldo() {
        when(cliente.clasificar(TEXTO)).thenThrow(new ResourceAccessException("Read timed out"));
        esperaRespaldo();
    }

    @Test
    @DisplayName("sin LLM_API_URL / LLM_API_KEY / LLM_MODELO → respaldo sin llamar al LLM")
    void sinConfigurar_usaElRespaldo() {
        when(cliente.configurado()).thenReturn(false);
        esperaRespaldo();
        verify(cliente).configurado();
        org.mockito.Mockito.verifyNoMoreInteractions(cliente);
    }
}
