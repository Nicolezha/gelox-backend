package com.gelox.backend.rf55;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gelox.backend.entities.MetodoClasificacion;
import com.gelox.backend.voz.ClasificadorIntencion;
import com.gelox.backend.voz.ClasificadorIntencion.ResultadoClasificacion;
import com.gelox.backend.voz.llm.LlmCliente;
import com.gelox.backend.voz.llm.LlmProperties;
import com.gelox.backend.voz.llm.ProcesadorLenguajeLlm;
import com.gelox.backend.voz.pln.ModeloIntencion;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.util.List;

/**
 * T115-PR1 — medición de precisión contra el LLM real (sirve para T141). No corre en el build:
 * solo con LLM_API_URL, LLM_API_KEY y LLM_MODELO definidos.
 * <pre>
 * LLM_API_URL=... LLM_API_KEY=... LLM_MODELO=... ./mvnw test -Dtest=LlmPrecisionManualTest
 * </pre>
 * Frases: rf46/FrasesCasosPruebaTest (CP-01 a CP-29) y los 10 ejemplos del prompt (FP-15 §2.11.3).
 */
@EnabledIfEnvironmentVariable(named = "LLM_API_KEY", matches = ".+")
class LlmPrecisionManualTest {

    private record Caso(String frase, String esperada) {}

    private static final List<Caso> CASOS = List.of(
            new Caso("Registra dos cajas de Festival en ventanilla.", "REGISTRAR_VENTA"),
            new Caso("¿Cuántas cajas de Festival quedan?", "CONSULTAR_INVENTARIO"),
            new Caso("Registra tres cajas de Festival a 3.000, canal ventanilla.", "REGISTRAR_VENTA"),
            new Caso("Registra dos cajas de Festival, canal ventanilla.", "REGISTRAR_VENTA"),
            new Caso("Registra cinco cajas de Solo Lack, canal ventanilla.", "REGISTRAR_VENTA"),
            new Caso("Registra veinte cajas de Festival, canal ventanilla.", "REGISTRAR_VENTA"),
            new Caso("Registra venta rural de dos cajas de Festival para María Elena Gómez, envío de diez mil pesos.", "REGISTRAR_VENTA"),
            new Caso("¿Cuántas cajas de Festival me quedan?", "CONSULTAR_INVENTARIO"),
            new Caso("GELOX, muéstrame los productos con stock bajo.", "CONSULTAR_INVENTARIO"),
            new Caso("¿Cuántas unidades de Paleta Arequipe Especial quedan?", "CONSULTAR_INVENTARIO"),
            new Caso("¿Cuánto vendimos hoy?", "CONSULTAR_FINANZAS"),
            new Caso("¿Cuánto ganamos hoy por canal?", "CONSULTAR_FINANZAS"),
            new Caso("¿Cuánto vendimos esta semana?", "CONSULTAR_FINANZAS"),
            new Caso("GELOX, cierra el día.", "CONSULTAR_FINANZAS"),
            new Caso("Genera un pedido con 20 cajas de Solo Lack y 15 de Festival.", "GENERAR_PEDIDO"),
            new Caso("Al pedido pendiente agrégale 10 de Festival.", "MODIFICAR_PEDIDO"),
            new Caso("Al pedido pendiente quítale las cajas de Festival.", "MODIFICAR_PEDIDO"),
            new Caso("En el pedido pendiente, cambia a 30 las cajas de Festival.", "MODIFICAR_PEDIDO"),
            // Ejemplos del prompt (§2.11.3)
            new Caso("registra tres cajas de minions canal ventanilla", "REGISTRAR_VENTA"),
            new Caso("venta rural de dos cajas de jet vainilla para maría elena gómez envío de diez mil", "REGISTRAR_VENTA"),
            new Caso("cuántas cajas de mango biche me quedan", "CONSULTAR_INVENTARIO"),
            new Caso("cuánto ganamos esta semana", "CONSULTAR_FINANZAS"),
            new Caso("genera un pedido con veinte cajas de solo lac", "GENERAR_PEDIDO"),
            new Caso("al pedido pendiente quítale el aloha limón", "MODIFICAR_PEDIDO"),
            new Caso("qué me recomiendas pedir con dos millones", "CONSULTAR_RECOMENDACION"),
            new Caso("cambia el mango biche a tres cajas", "DECIDIR_RECOMENDACION"),
            new Caso("listo genera el pedido de la recomendación", "DECIDIR_RECOMENDACION"),
            new Caso("cuéntame un chiste", null));

    @Test
    void medirPrecision() {
        LlmProperties propiedades = new LlmProperties(System.getenv("LLM_API_URL"), System.getenv("LLM_API_KEY"),
                System.getenv("LLM_MODELO"), 2500);
        ModeloIntencion modelo = new ModeloIntencion();
        modelo.cargar();
        ProcesadorLenguajeLlm procesador = new ProcesadorLenguajeLlm(
                new LlmCliente(propiedades, new ObjectMapper()), new ClasificadorIntencion(modelo));

        int aciertos = 0;
        int porLlm = 0;
        for (Caso caso : CASOS) {
            ResultadoClasificacion r = procesador.procesar(caso.frase());
            String obtenida = r.intencion() == null ? null : r.intencion().name();
            boolean acierto = java.util.Objects.equals(obtenida, caso.esperada());
            if (acierto) aciertos++;
            if (r.metodo() == MetodoClasificacion.LLM) porLlm++;
            System.out.printf("%s %-7s %-24s %s%n", acierto ? "OK " : "XX ", r.metodo(), obtenida, caso.frase());
        }
        System.out.printf("%nPrecisión de intención: %d/%d = %.1f %% (resueltas por el LLM: %d)%n",
                aciertos, CASOS.size(), 100.0 * aciertos / CASOS.size(), porLlm);
    }
}
