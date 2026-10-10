package com.gelox.backend.voz.handlers;

import com.gelox.backend.entities.PedidoProveedor;
import com.gelox.backend.voz.NormalizadorVoz;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SelectorPedidoTest {

    private static final LocalDate HOY = LocalDate.of(2026, 10, 10); // sábado

    /** Pendientes del más reciente al más antiguo: 10, 8 y 5 de octubre. */
    private static final List<PedidoProveedor> PENDIENTES = List.of(
            pedido(LocalDate.of(2026, 10, 10)), pedido(LocalDate.of(2026, 10, 8)), pedido(LocalDate.of(2026, 10, 5)));

    @ParameterizedTest(name = "\"{0}\" -> pedido del {1}")
    @DisplayName("se elige el pendiente que se nombra y el resto del texto queda limpio")
    @CsvSource(delimiter = '|', value = {
            "Al pedido 2 agrégale 10 cajas de Festival|2026-10-08|al agregale 10 cajas de festival",
            "Al pedido número tres quítale el Festival|2026-10-05|al quitale el festival",
            "Al segundo pedido agrégale 10 de Festival|2026-10-08|al pedido agregale 10 de festival",
            "Al último pedido súmale 5 cajas de Festival|2026-10-10|al pedido sumale 5 cajas de festival",
            "Al pedido más viejo agrégale 10 de Festival|2026-10-05|al pedido agregale 10 de festival",
            "Al pedido de hoy agrégale 10 de Festival|2026-10-10|al pedido agregale 10 de festival",
            "Al pedido del 8 de octubre agrégale 10 de Festival|2026-10-08|al pedido agregale 10 de festival",
            "Al pedido del lunes agrégale 10 de Festival|2026-10-05|al pedido agregale 10 de festival",
    })
    void eligePendiente(String frase, LocalDate fecha, String resto) {
        SelectorPedido s = SelectorPedido.de(NormalizadorVoz.normalizar(frase), HOY);
        assertThat(s.elegir(PENDIENTES).getFecha()).isEqualTo(fecha);
        assertThat(s.textoSinSelector()).isEqualTo(resto);
    }

    @ParameterizedTest(name = "\"{0}\" no elige ninguno")
    @DisplayName("sin decir a cuál, o uno que no existe, no se adivina")
    @CsvSource({
            "'Al pedido pendiente agrégale 10 cajas de Festival'",
            "'Al pedido 10 cajas de Festival agrégale'",
            "'Al pedido 7 agrégale 2 cajas de Festival'",
            "'Al pedido de ayer agrégale 2 cajas de Festival'",
    })
    void noAdivina(String frase) {
        assertThat(SelectorPedido.de(NormalizadorVoz.normalizar(frase), HOY).elegir(PENDIENTES)).isNull();
    }

    private static PedidoProveedor pedido(LocalDate fecha) {
        PedidoProveedor p = new PedidoProveedor();
        p.setFecha(fecha);
        return p;
    }
}
