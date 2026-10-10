package com.gelox.backend.rf46;

import com.gelox.backend.entities.TipoIntencionVoz;
import com.gelox.backend.voz.ClasificadorIntencion;
import com.gelox.backend.voz.NormalizadorVoz;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Frases de "GELOX — Casos de Prueba Funcionales RF46–RF53" tal como las
 * transcribe Chrome (mayúsculas, tildes, "¿?", comas y punto final).
 */
class FrasesCasosPruebaTest {

    private final ClasificadorIntencion clasificador = new ClasificadorIntencion();

    @ParameterizedTest(name = "{0}: \"{1}\"")
    @DisplayName("cada frase de los casos de prueba resuelve a su intención")
    @CsvSource(delimiter = '|', value = {
            "CP-01|Registra dos cajas de Festival en ventanilla.|REGISTRAR_VENTA",
            "CP-02|¿Cuántas cajas de Festival quedan?|CONSULTAR_INVENTARIO",
            "CP-06|Registra tres cajas de Festival a 3.000, canal ventanilla.|REGISTRAR_VENTA",
            "CP-07|Registra dos cajas de Festival, canal ventanilla.|REGISTRAR_VENTA",
            "CP-08|Registra cinco cajas de Solo Lack, canal ventanilla.|REGISTRAR_VENTA",
            "CP-09|Registra veinte cajas de Festival, canal ventanilla.|REGISTRAR_VENTA",
            "CP-10|Registra venta rural de dos cajas de Festival para María Elena Gómez, envío de diez mil pesos.|REGISTRAR_VENTA",
            "CP-14|¿Cuántas cajas de Festival me quedan?|CONSULTAR_INVENTARIO",
            "CP-15|GELOX, muéstrame los productos con stock bajo.|CONSULTAR_INVENTARIO",
            "CP-16|¿Cuántas unidades de Paleta Arequipe Especial quedan?|CONSULTAR_INVENTARIO",
            "CP-17|¿Cuánto vendimos hoy?|CONSULTAR_FINANZAS",
            "CP-18|¿Cuánto ganamos hoy por canal?|CONSULTAR_FINANZAS",
            "CP-19|¿Cuánto vendimos esta semana?|CONSULTAR_FINANZAS",
            "CP-21|GELOX, cierra el día.|CONSULTAR_FINANZAS",
            "CP-24|Genera un pedido con 20 cajas de Solo Lack y 15 de Festival.|GENERAR_PEDIDO",
            "CP-27|Al pedido pendiente agrégale 10 de Festival.|MODIFICAR_PEDIDO",
            "CP-28|Al pedido pendiente quítale las cajas de Festival.|MODIFICAR_PEDIDO",
            "CP-29|En el pedido pendiente, cambia a 30 las cajas de Festival.|MODIFICAR_PEDIDO",
            // Variantes habituales del reconocedor
            "var|cuántos helados de Festival hay|CONSULTAR_INVENTARIO",
            "var|Registré dos cajas de Festival|REGISTRAR_VENTA",
            "var|cuánto vendimos ayer|CONSULTAR_FINANZAS",
    })
    void intencion(String cp, String frase, TipoIntencionVoz esperada) {
        assertThat(clasificador.clasificar(frase).intencion()).as(cp).isEqualTo(esperada);
    }

    @ParameterizedTest(name = "{0}")
    @DisplayName("CP-05: frase fuera de las intenciones soportadas no se clasifica")
    @CsvSource({"'Oye GELOX, cuéntame un chiste.'"})
    void fueraDeDominio(String frase) {
        assertThat(clasificador.clasificar(frase).intencion()).isNull();
    }

    @ParameterizedTest(name = "\"{0}\" -> \"{1}\"")
    @DisplayName("el normalizador quita la puntuación del reconocedor y convierte números dichos")
    @CsvSource(delimiter = '|', value = {
            "Registra dos cajas de Festival, canal ventanilla.|registra 2 cajas de festival, canal ventanilla",
            "¿Cuántas cajas de Festival quedan?|cuantas cajas de festival quedan",
            "y 10 de Chocolisto.|y 10 de chocolisto",
            "envío de diez mil pesos.|envio de 10000 pesos",
            "envío de $10.000|envio de $10000",
            "a tres mil quinientos|a 3500",
            "veintidós cajas|22 cajas",
            "treinta y cinco|35",
            "ciento veinte|120",
            "mil doscientos|1200",
    })
    void normaliza(String entrada, String esperado) {
        assertThat(NormalizadorVoz.normalizar(entrada)).isEqualTo(esperado);
    }
}
