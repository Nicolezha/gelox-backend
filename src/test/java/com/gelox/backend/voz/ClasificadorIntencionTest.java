package com.gelox.backend.voz;

import com.gelox.backend.entities.TipoIntencionVoz;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * FP-15 §2.4 — el clasificador debe resolver la intención correcta por
 * coincidencia de patrones, sin depender de ningún servicio externo.
 */
class ClasificadorIntencionTest {

    private final ClasificadorIntencion clasificador = new ClasificadorIntencion();

    @Test
    @DisplayName("registra una venta")
    void registrarVenta() {
        var resultado = clasificador.clasificar("Vende dos bolsas de festival");

        assertThat(resultado.intencion()).isEqualTo(TipoIntencionVoz.REGISTRAR_VENTA);
        assertThat(resultado.slots()).containsEntry("cantidad", "2");
        assertThat(resultado.slots()).containsEntry("producto", "festival");
    }

    @Test
    @DisplayName("venta por canal rural")
    void registrarVentaRural() {
        var resultado = clasificador.clasificar("Anota una venta rural de tres festival");

        assertThat(resultado.intencion()).isEqualTo(TipoIntencionVoz.REGISTRAR_VENTA);
        assertThat(resultado.slots()).containsEntry("canal", "RURAL");
    }

    @Test
    @DisplayName("consulta de inventario")
    void consultarInventario() {
        var resultado = clasificador.clasificar("¿Cuántas paletas de festival quedan?");

        assertThat(resultado.intencion()).isEqualTo(TipoIntencionVoz.CONSULTAR_INVENTARIO);
    }

    @Test
    @DisplayName("consulta financiera general")
    void consultarFinanzas() {
        var resultado = clasificador.clasificar("¿Cuánto ganamos hoy?");

        assertThat(resultado.intencion()).isEqualTo(TipoIntencionVoz.CONSULTAR_FINANZAS);
        assertThat(resultado.slots()).containsEntry("periodo", "HOY");
    }

    @Test
    @DisplayName("cierre del día es consulta financiera con tipo cierre")
    void cierreDelDia() {
        var resultado = clasificador.clasificar("Cierra el día");

        assertThat(resultado.intencion()).isEqualTo(TipoIntencionVoz.CONSULTAR_FINANZAS);
        assertThat(resultado.slots()).containsEntry("tipo", "CIERRE");
    }

    @Test
    @DisplayName("generar pedido")
    void generarPedido() {
        var resultado = clasificador.clasificar("Genera un pedido a Nutresa");

        assertThat(resultado.intencion()).isEqualTo(TipoIntencionVoz.GENERAR_PEDIDO);
    }

    @Test
    @DisplayName("modificar pedido se evalúa antes que generar pedido")
    void modificarPedido() {
        var resultado = clasificador.clasificar("Agrégale tres cajas de solo lac al pedido pendiente");

        assertThat(resultado.intencion()).isEqualTo(TipoIntencionVoz.MODIFICAR_PEDIDO);
        assertThat(resultado.slots()).containsEntry("cantidad", "3");
    }

    @Test
    @DisplayName("números escritos y montos se normalizan a dígitos")
    void normalizaNumerosYMontos() {
        assertThat(clasificador.clasificar("vende quince festival").slots()).containsEntry("cantidad", "15");
        assertThat(clasificador.clasificar("vendi festival por 2.500 pesos").slots()).containsEntry("cantidad", "2500");
    }

    @Test
    @DisplayName("verbo después de \"pedido\": al pedido pendiente agrégale... es MODIFICAR_PEDIDO")
    void modificarPedidoConPedidoAntesDelVerbo() {
        var resultado = clasificador.clasificar("Al pedido pendiente agrégale 10 de Festival");

        assertThat(resultado.intencion()).isEqualTo(TipoIntencionVoz.MODIFICAR_PEDIDO);
        assertThat(resultado.slots()).containsEntry("cantidad", "10");
    }

    @Test
    @DisplayName("frase sin coincidencia no devuelve intención")
    void sinCoincidencia() {
        var resultado = clasificador.clasificar("qué clima hace hoy");

        assertThat(resultado.intencion()).isNull();
    }
}
