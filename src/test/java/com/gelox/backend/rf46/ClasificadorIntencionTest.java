package com.gelox.backend.rf46;

import com.gelox.backend.entities.MetodoClasificacion;
import com.gelox.backend.entities.TipoIntencionVoz;
import com.gelox.backend.voz.ClasificadorIntencion;
import com.gelox.backend.voz.pln.ModeloIntencion;
import com.gelox.backend.voz.pln.PrediccionIntencion;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * RF46 / FP-15 §2.4–2.5 — el clasificador resuelve la intención en dos niveles
 * (modelo de OpenNLP con umbral 0,60 y, si no alcanza, reglas de patrones) y
 * extrae los slots. Sin Spring ni servicios externos: el modelo se carga del
 * classpath. Incluye 5 frases por intención (25 en total) con los ejemplos del FP-15.
 * <p>
 * Los slots se comparan sobre el texto normalizado: "tres" llega como "3" y
 * "un/una" como "1".
 */
class ClasificadorIntencionTest {

    private static ModeloIntencion modelo;
    private static ClasificadorIntencion clasificador;

    @BeforeAll
    static void cargarModelo() {
        modelo = new ModeloIntencion();
        modelo.cargar();
        assertThat(modelo.disponible()).isTrue();
        clasificador = new ClasificadorIntencion(modelo);
    }

    // ---- Combinación modelo + reglas (FP-15 §2.5) ----

    @Test
    @DisplayName("PLN — frase que solo entiende el modelo: 'vendame 2 de solo lac' → MODELO")
    void modelo_fraseQueLasReglasNoCubren() {
        assertThat(new ClasificadorIntencion(modeloQueDevuelve(Optional.empty()))
                .clasificar("vendame 2 de solo lac").intencion())
                .as("las reglas solas no la reconocen").isNull();

        var r = clasificador.clasificar("vendame 2 de solo lac");

        assertThat(r.intencion()).isEqualTo(TipoIntencionVoz.REGISTRAR_VENTA);
        assertThat(r.metodo()).isEqualTo(MetodoClasificacion.MODELO);
        assertThat(r.probabilidadIntencion()).isGreaterThanOrEqualTo(ClasificadorIntencion.UMBRAL_INTENCION);
        assertThat(r.slots()).containsEntry("cantidad", "2").containsEntry("producto", "solo lac");
    }

    @Test
    @DisplayName("PLN — probabilidad < 0,60 → se usan las reglas y se conserva la probabilidad del modelo")
    void modelo_probabilidadBajaUsaReglas() {
        var c = new ClasificadorIntencion(modeloQueDevuelve(
                Optional.of(new PrediccionIntencion("GENERAR_PEDIDO", 0.45))));

        var r = c.clasificar("vende dos festival");

        assertThat(r.intencion()).isEqualTo(TipoIntencionVoz.REGISTRAR_VENTA);
        assertThat(r.metodo()).isEqualTo(MetodoClasificacion.REGLAS);
        assertThat(r.probabilidadIntencion()).isEqualTo(0.45);
    }

    @Test
    @DisplayName("PLN — FUERA_DE_DOMINIO ('hola buenos dias') → intención null")
    void modelo_fueraDeDominio() {
        var r = clasificador.clasificar("hola buenos dias");

        assertThat(r.intencion()).isNull();
        assertThat(r.metodo()).isNull();
        assertThat(r.probabilidadIntencion()).isNotNull();
    }

    @Test
    @DisplayName("PLN — FUERA_DE_DOMINIO con probabilidad alta igual pasa a las reglas")
    void modelo_fueraDeDominioPasaAReglas() {
        var c = new ClasificadorIntencion(modeloQueDevuelve(
                Optional.of(new PrediccionIntencion(PrediccionIntencion.FUERA_DE_DOMINIO, 0.95))));

        var r = c.clasificar("vende dos festival");

        assertThat(r.intencion()).isEqualTo(TipoIntencionVoz.REGISTRAR_VENTA);
        assertThat(r.metodo()).isEqualTo(MetodoClasificacion.REGLAS);
    }

    @Test
    @DisplayName("PLN — modelo no disponible (Optional.empty) → REGLAS, sin probabilidad")
    void modelo_noDisponibleUsaReglas() {
        var r = new ClasificadorIntencion(modeloQueDevuelve(Optional.empty())).clasificar("cuanto ganamos hoy");

        assertThat(r.intencion()).isEqualTo(TipoIntencionVoz.CONSULTAR_FINANZAS);
        assertThat(r.metodo()).isEqualTo(MetodoClasificacion.REGLAS);
        assertThat(r.probabilidadIntencion()).isNull();
    }

    @Test
    @DisplayName("PLN — modelo que no carga (no está en el classpath) no lanza excepción y devuelve vacío")
    void modelo_sinCargarDevuelveVacio() {
        ModeloIntencion sinCargar = new ModeloIntencion();   // sin llamar a cargar()
        assertThat(sinCargar.disponible()).isFalse();
        assertThat(sinCargar.predecir("vende 2 festival")).isEmpty();
    }

    private static ModeloIntencion modeloQueDevuelve(Optional<PrediccionIntencion> prediccion) {
        ModeloIntencion m = mock(ModeloIntencion.class);
        when(m.predecir(anyString())).thenReturn(prediccion);
        return m;
    }

    private void verifica(String frase, TipoIntencionVoz intencion, Map<String, String> slotsEsperados) {
        var resultado = clasificador.clasificar(frase);

        assertThat(resultado.intencion()).as("intención de \"%s\"", frase).isEqualTo(intencion);
        slotsEsperados.forEach((slot, valor) ->
                assertThat(resultado.slots()).as("slot %s de \"%s\"", slot, frase).containsEntry(slot, valor));
    }

    // ---- REGISTRAR_VENTA ----

    @Test
    @DisplayName("REGISTRAR_VENTA — FP-15: registra tres cajas de Festival a 2.500, canal ventanilla")
    void venta_comandoLiteralFp15() {
        verifica("registra tres cajas de Festival a 2.500, canal ventanilla",
                TipoIntencionVoz.REGISTRAR_VENTA, Map.of("cantidad", "3"));
    }

    @Test
    @DisplayName("REGISTRAR_VENTA — FP-15: vende 2 festival")
    void venta_vende2Festival() {
        verifica("vende 2 festival", TipoIntencionVoz.REGISTRAR_VENTA, Map.of("cantidad", "2"));
    }

    @Test
    @DisplayName("REGISTRAR_VENTA — vende dos bolsas de festival")
    void venta_bolsasDeFestival() {
        verifica("Vende dos bolsas de festival", TipoIntencionVoz.REGISTRAR_VENTA,
                Map.of("cantidad", "2", "producto", "festival"));
    }

    @Test
    @DisplayName("REGISTRAR_VENTA — anota una venta rural")
    void venta_anotaRural() {
        verifica("Anota una venta rural de tres festival", TipoIntencionVoz.REGISTRAR_VENTA,
                Map.of("cantidad", "1", "canal", "RURAL", "producto", "3 festival"));
    }

    @Test
    @DisplayName("REGISTRAR_VENTA — vendí en pasado")
    void venta_vendiEnPasado() {
        verifica("Vendí 5 de solo lack", TipoIntencionVoz.REGISTRAR_VENTA,
                Map.of("cantidad", "5", "producto", "solo lack"));
    }

    // ---- CONSULTAR_INVENTARIO ----

    @Test
    @DisplayName("CONSULTAR_INVENTARIO — FP-15: ¿Cuántas paletas de festival quedan?")
    void inventario_cuantasQuedan() {
        verifica("¿Cuántas paletas de festival quedan?", TipoIntencionVoz.CONSULTAR_INVENTARIO,
                Map.of("producto", "festival"));
    }

    @Test
    @DisplayName("CONSULTAR_INVENTARIO — cuántas cajas de Solo Lack hay")
    void inventario_cuantasHay() {
        verifica("Cuántas cajas de Solo Lack hay", TipoIntencionVoz.CONSULTAR_INVENTARIO,
                Map.of("producto", "solo lack"));
    }

    @Test
    @DisplayName("CONSULTAR_INVENTARIO — stock de un producto")
    void inventario_stockDeProducto() {
        verifica("Cómo está el stock de festival", TipoIntencionVoz.CONSULTAR_INVENTARIO,
                Map.of("producto", "festival"));
    }

    @Test
    @DisplayName("CONSULTAR_INVENTARIO — revisa el inventario (sin slots)")
    void inventario_revisaInventario() {
        verifica("Revisa el inventario", TipoIntencionVoz.CONSULTAR_INVENTARIO, Map.of());
        assertThat(clasificador.clasificar("Revisa el inventario").slots()).isEmpty();
    }

    @Test
    @DisplayName("CONSULTAR_INVENTARIO — se está acabando")
    void inventario_seEstaAcabando() {
        verifica("Se está acabando el festival", TipoIntencionVoz.CONSULTAR_INVENTARIO, Map.of());
    }

    // ---- CONSULTAR_FINANZAS ----

    @Test
    @DisplayName("CONSULTAR_FINANZAS — FP-15: ¿Cuánto ganamos hoy?")
    void finanzas_cuantoGanamosHoy() {
        verifica("¿Cuánto ganamos hoy?", TipoIntencionVoz.CONSULTAR_FINANZAS, Map.of("periodo", "HOY"));
    }

    @Test
    @DisplayName("CONSULTAR_FINANZAS — cuánto ganamos ayer")
    void finanzas_cuantoGanamosAyer() {
        verifica("Cuánto ganamos ayer", TipoIntencionVoz.CONSULTAR_FINANZAS, Map.of("periodo", "AYER"));
    }

    @Test
    @DisplayName("CONSULTAR_FINANZAS — ganancia de la semana")
    void finanzas_gananciaDeLaSemana() {
        verifica("Dime la ganancia de esta semana", TipoIntencionVoz.CONSULTAR_FINANZAS,
                Map.of("periodo", "SEMANA"));
    }

    @Test
    @DisplayName("CONSULTAR_FINANZAS — ingresos del mes")
    void finanzas_ingresosDelMes() {
        verifica("Dame los ingresos del mes", TipoIntencionVoz.CONSULTAR_FINANZAS, Map.of("periodo", "MES"));
    }

    @Test
    @DisplayName("CONSULTAR_FINANZAS — cierra el día es tipo CIERRE")
    void finanzas_cierraElDia() {
        verifica("Cierra el día", TipoIntencionVoz.CONSULTAR_FINANZAS,
                Map.of("tipo", "CIERRE", "periodo", "HOY"));
    }

    // ---- GENERAR_PEDIDO ----

    @Test
    @DisplayName("GENERAR_PEDIDO — FP-15: genera un pedido con 20 cajas de Solo Lack y 15 de Festival")
    void generar_comandoLiteralFp15() {
        verifica("genera un pedido con 20 cajas de Solo Lack y 15 de Festival",
                TipoIntencionVoz.GENERAR_PEDIDO, Map.of());
    }

    @Test
    @DisplayName("GENERAR_PEDIDO — genera un pedido a un proveedor")
    void generar_pedidoAProveedor() {
        verifica("Genera un pedido a Nutresa", TipoIntencionVoz.GENERAR_PEDIDO, Map.of());
    }

    @Test
    @DisplayName("GENERAR_PEDIDO — crea el pedido con cantidad")
    void generar_creaPedido() {
        verifica("Crea el pedido de 10 cajas de festival", TipoIntencionVoz.GENERAR_PEDIDO,
                Map.of("cantidad", "10"));
    }

    @Test
    @DisplayName("GENERAR_PEDIDO — haz el pedido")
    void generar_hazPedido() {
        verifica("Haz el pedido de 5 cajas de solo lack", TipoIntencionVoz.GENERAR_PEDIDO,
                Map.of("cantidad", "5"));
    }

    @Test
    @DisplayName("GENERAR_PEDIDO — arma el pedido")
    void generar_armaPedido() {
        verifica("Arma el pedido de la semana", TipoIntencionVoz.GENERAR_PEDIDO, Map.of());
    }

    // ---- MODIFICAR_PEDIDO ----

    @Test
    @DisplayName("MODIFICAR_PEDIDO — FP-15: al pedido pendiente agrégale 10 de Festival")
    void modificar_agregaleAlPedidoPendiente() {
        verifica("Al pedido pendiente agrégale 10 de Festival", TipoIntencionVoz.MODIFICAR_PEDIDO,
                Map.of("cantidad", "10", "producto", "festival"));
    }

    @Test
    @DisplayName("MODIFICAR_PEDIDO — verbo antes de \"pedido\" (se evalúa antes que GENERAR_PEDIDO)")
    void modificar_verboAntesDePedido() {
        verifica("Agrégale tres cajas de solo lack al pedido pendiente", TipoIntencionVoz.MODIFICAR_PEDIDO,
                Map.of("cantidad", "3"));
    }

    @Test
    @DisplayName("MODIFICAR_PEDIDO — quita del pedido")
    void modificar_quitaDelPedido() {
        verifica("Quita el festival del pedido", TipoIntencionVoz.MODIFICAR_PEDIDO, Map.of());
    }

    @Test
    @DisplayName("MODIFICAR_PEDIDO — elimina del pedido pendiente")
    void modificar_eliminaDelPedido() {
        verifica("Elimina 5 cajas de solo lack del pedido pendiente", TipoIntencionVoz.MODIFICAR_PEDIDO,
                Map.of("cantidad", "5"));
    }

    @Test
    @DisplayName("MODIFICAR_PEDIDO — actualiza el pedido")
    void modificar_actualizaElPedido() {
        verifica("Actualiza el pedido pendiente con 8 de festival", TipoIntencionVoz.MODIFICAR_PEDIDO,
                Map.of("cantidad", "8", "producto", "festival"));
    }

    // ---- Otros ----

    @Test
    @DisplayName("frase sin coincidencia no devuelve intención")
    void sinCoincidencia() {
        var resultado = clasificador.clasificar("qué clima hace hoy");

        assertThat(resultado.intencion()).isNull();
        assertThat(resultado.slots()).isEmpty();
    }

    @Test
    @DisplayName("números escritos y montos con separador de miles se normalizan a dígitos")
    void normalizaNumerosYMontos() {
        assertThat(clasificador.clasificar("vende quince festival").slots()).containsEntry("cantidad", "15");
        assertThat(clasificador.clasificar("vendi festival por 2.500 pesos").slots()).containsEntry("cantidad", "2500");
    }
}
