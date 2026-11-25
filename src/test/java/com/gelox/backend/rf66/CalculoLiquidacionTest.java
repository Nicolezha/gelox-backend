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
 * RF66 — aritmética de cada esquema de liquidación sobre los totales de TODA
 * la planilla (FP-15 §2.17.2), espejo exacto de calcularLiquidacion() en
 * creditoUtils.js del frontend. 20 despachadas − 5 devueltas = 15 vendidas,
 * PVP 2000, precio comerciante 1600 (ejemplo de EsquemaLiquidacion.jsx).
 */
class CalculoLiquidacionTest {

    static final int UNIDADES = 15;
    static final BigDecimal TOTAL_PUBLICO = BigDecimal.valueOf(2000L * UNIDADES);        // 30000
    static final BigDecimal TOTAL_COMERCIANTE = BigDecimal.valueOf(1600L * UNIDADES);    // 24000

    @Test
    @DisplayName("MARGEN: la ganancia del comerciante es la diferencia entre total público y total comerciante")
    void margen_gananciaEsLaDiferencia() {
        CalculoLiquidacion.Resultado r = new MargenCalculo().calcular(TOTAL_PUBLICO, TOTAL_COMERCIANTE, UNIDADES, BigDecimal.ZERO);

        assertThat(r.gananciaComerciante()).isEqualByComparingTo("6000");
        assertThat(r.montoCobrado()).isEqualByComparingTo("24000");
    }

    @Test
    @DisplayName("MARGEN sin precio al público (totalPublico == totalComerciante): reproduce el histórico exacto")
    void margen_sinPrecioPublico_reproduceElHistorico() {
        CalculoLiquidacion.Resultado r = new MargenCalculo().calcular(TOTAL_COMERCIANTE, TOTAL_COMERCIANTE, UNIDADES, BigDecimal.ZERO);

        assertThat(r.montoCobrado()).isEqualByComparingTo("24000");
        assertThat(r.gananciaComerciante()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("MARGEN con precio al público menor que el del comerciante: igual se cobra el precio al comerciante")
    void margen_publicoMenorQueComerciante_cobraElPrecioAlComerciante() {
        CalculoLiquidacion.Resultado r = new MargenCalculo().calcular(BigDecimal.ZERO, TOTAL_COMERCIANTE, UNIDADES, null);

        assertThat(r.montoCobrado()).isEqualByComparingTo("24000");
        assertThat(r.gananciaComerciante()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("COMISION: el comerciante gana el porcentaje del total público; la distribuidora cobra el resto")
    void comision_gananciaEsElPorcentajeDelTotalPublico() {
        CalculoLiquidacion.Resultado r = new ComisionCalculo().calcular(TOTAL_PUBLICO, TOTAL_COMERCIANTE, UNIDADES, BigDecimal.valueOf(20));

        // 30000 * 20% = 6000 para el comerciante; 24000 factura la distribuidora
        assertThat(r.gananciaComerciante()).isEqualByComparingTo("6000");
        assertThat(r.montoCobrado()).isEqualByComparingTo("24000");
    }

    @Test
    @DisplayName("TARIFA_FIJA: el comerciante gana un valor fijo por planilla, sin importar lo vendido")
    void tarifaFija_gananciaEsElValorFijoPorPlanilla() {
        CalculoLiquidacion.Resultado r = new TarifaFijaCalculo().calcular(TOTAL_PUBLICO, TOTAL_COMERCIANTE, UNIDADES, BigDecimal.valueOf(5000));

        assertThat(r.gananciaComerciante()).isEqualByComparingTo("5000");
        assertThat(r.montoCobrado()).isEqualByComparingTo("25000");
    }

    @Test
    @DisplayName("TARIFA_FIJA sin unidades vendidas: no hay ganancia")
    void tarifaFija_sinUnidades_sinGanancia() {
        CalculoLiquidacion.Resultado r = new TarifaFijaCalculo().calcular(BigDecimal.ZERO, BigDecimal.ZERO, 0, BigDecimal.valueOf(5000));

        assertThat(r.gananciaComerciante()).isEqualByComparingTo("0");
        assertThat(r.montoCobrado()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("la ganancia nunca queda negativa ni supera el total público")
    void laGanancia_quedaAcotadaEntreCeroYElTotalPublico() {
        // comisión exagerada no hace que la distribuidora cobre un monto negativo
        CalculoLiquidacion.Resultado comision = new ComisionCalculo().calcular(TOTAL_PUBLICO, TOTAL_COMERCIANTE, UNIDADES, BigDecimal.valueOf(100));
        assertThat(comision.gananciaComerciante()).isEqualByComparingTo(TOTAL_PUBLICO);
        assertThat(comision.montoCobrado()).isEqualByComparingTo("0");

        // tarifa fija mayor al total público no deja montoCobrado negativo
        CalculoLiquidacion.Resultado tarifa = new TarifaFijaCalculo().calcular(TOTAL_PUBLICO, TOTAL_COMERCIANTE, UNIDADES, BigDecimal.valueOf(999999));
        assertThat(tarifa.gananciaComerciante()).isEqualByComparingTo(TOTAL_PUBLICO);
        assertThat(tarifa.montoCobrado()).isEqualByComparingTo("0");
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
