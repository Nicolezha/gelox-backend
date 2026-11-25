package com.gelox.backend.rf66;

import com.gelox.backend.credito.CalculoLiquidacion;
import com.gelox.backend.credito.CalculoLiquidacionResolver;
import com.gelox.backend.credito.ComisionCalculo;
import com.gelox.backend.credito.MargenCalculo;
import com.gelox.backend.credito.TarifaFijaCalculo;
import com.gelox.backend.entities.TipoEsquemaLiquidacion;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * RF66 — aritmética de cada esquema de liquidación (FP-15 §2.17.2). MARGEN
 * reproduce el cálculo histórico (precioVenta * unidadesVendidas) exacto.
 */
class CalculoLiquidacionTest {

    static final BigDecimal PRECIO_VENTA = BigDecimal.valueOf(1000);
    static final BigDecimal PRECIO_PUBLICO = BigDecimal.valueOf(1500);

    @Test
    @DisplayName("MARGEN: factura precioVenta * unidadesVendidas, igual que el cálculo histórico")
    void margen_facturaPrecioVentaPorUnidades() {
        CalculoLiquidacion.Linea linea = new MargenCalculo().calcular(PRECIO_VENTA, null, 10, BigDecimal.ZERO);

        assertThat(linea.montoCobrado()).isEqualByComparingTo("10000");
        assertThat(linea.gananciaComerciante()).isNull();
    }

    @Test
    @DisplayName("MARGEN con precio al público: la ganancia del comerciante es la diferencia de precios")
    void margen_conPrecioPublico_calculaGananciaComerciante() {
        CalculoLiquidacion.Linea linea = new MargenCalculo().calcular(PRECIO_VENTA, PRECIO_PUBLICO, 10, BigDecimal.ZERO);

        assertThat(linea.montoCobrado()).isEqualByComparingTo("10000");
        assertThat(linea.gananciaComerciante()).isEqualByComparingTo("5000");
    }

    @Test
    @DisplayName("COMISION: factura el porcentaje de la venta al público sobre precio al público")
    void comision_facturaPorcentajeDeLaVentaAlPublico() {
        CalculoLiquidacion.Linea linea = new ComisionCalculo().calcular(
                PRECIO_VENTA, PRECIO_PUBLICO, 10, BigDecimal.valueOf(20));

        // venta al público = 15000; comisión 20% = 3000; ganancia comerciante = 12000
        assertThat(linea.montoCobrado()).isEqualByComparingTo("3000");
        assertThat(linea.gananciaComerciante()).isEqualByComparingTo("12000");
    }

    @Test
    @DisplayName("COMISION sin precio al público: aplica el porcentaje sobre precioVenta y no calcula ganancia")
    void comision_sinPrecioPublico_usaPrecioVenta() {
        CalculoLiquidacion.Linea linea = new ComisionCalculo().calcular(
                PRECIO_VENTA, null, 10, BigDecimal.valueOf(20));

        assertThat(linea.montoCobrado()).isEqualByComparingTo("2000");
        assertThat(linea.gananciaComerciante()).isNull();
    }

    @Test
    @DisplayName("TARIFA_FIJA: factura un monto fijo por unidad vendida")
    void tarifaFija_facturaMontoFijoPorUnidad() {
        CalculoLiquidacion.Linea linea = new TarifaFijaCalculo().calcular(
                PRECIO_VENTA, PRECIO_PUBLICO, 10, BigDecimal.valueOf(200));

        // tarifa 200/u * 10 = 2000; venta al público = 15000; ganancia comerciante = 13000
        assertThat(linea.montoCobrado()).isEqualByComparingTo("2000");
        assertThat(linea.gananciaComerciante()).isEqualByComparingTo("13000");
    }

    @Test
    @DisplayName("el resolver devuelve la estrategia que corresponde a cada tipo de esquema")
    void resolver_devuelveLaEstrategiaPorTipo() {
        CalculoLiquidacionResolver resolver = new CalculoLiquidacionResolver(
                List.of(new MargenCalculo(), new ComisionCalculo(), new TarifaFijaCalculo()));

        assertThat(resolver.paraTipo(TipoEsquemaLiquidacion.MARGEN)).isInstanceOf(MargenCalculo.class);
        assertThat(resolver.paraTipo(TipoEsquemaLiquidacion.COMISION)).isInstanceOf(ComisionCalculo.class);
        assertThat(resolver.paraTipo(TipoEsquemaLiquidacion.TARIFA_FIJA)).isInstanceOf(TarifaFijaCalculo.class);
    }

    @Test
    @DisplayName("el resolver sin ninguna estrategia registrada lanza una excepción")
    void resolver_sinEstrategias_lanzaExcepcion() {
        CalculoLiquidacionResolver resolver = new CalculoLiquidacionResolver(List.of());

        assertThatThrownBy(() -> resolver.paraTipo(TipoEsquemaLiquidacion.MARGEN))
                .isInstanceOf(IllegalStateException.class);
    }
}
