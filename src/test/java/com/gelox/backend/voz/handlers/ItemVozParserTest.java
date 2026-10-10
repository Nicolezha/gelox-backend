package com.gelox.backend.voz.handlers;

import com.gelox.backend.voz.NormalizadorVoz;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class ItemVozParserTest {

    private static final Pattern PATRON = ItemVozParser.patron("\\s*,|\\s+y\\s+|\\s+para\\b|\\s+envio\\b|\\s+canal\\b");

    private static List<ItemVozParser.ItemExtraido> extraer(String frase) {
        return ItemVozParser.extraer(PATRON, NormalizadorVoz.normalizar(frase));
    }

    @Test
    @DisplayName("\"una caja y seis unidades de Festival\" no pierde las unidades")
    void cajaYUnidadesDelMismoProducto() {
        assertThat(extraer("Vende una caja y seis unidades de Festival para doña Marta, envío 5.000"))
                .containsExactly(new ItemVozParser.ItemExtraido(1, 6, "festival", null));
    }

    @Test
    @DisplayName("sin \"de\" y con \"cajitas\"")
    void sinDeYConCajitas() {
        assertThat(extraer("Quiero vender tres cajitas tosh pasion, canal ventanilla"))
                .containsExactly(new ItemVozParser.ItemExtraido(3, 0, "tosh pasion", null));
    }

    @Test
    @DisplayName("\"de 90 gramos\" es el tamaño, no el precio")
    void gramosNoEsPrecio() {
        assertThat(extraer("Vende dos cajas de polet chocoavellana de 90 gramos, canal ventanilla"))
                .containsExactly(new ItemVozParser.ItemExtraido(2, 0, "polet chocoavellana de 90 gramos", null));
    }

    @Test
    @DisplayName("la forma de pago no se pega al nombre del producto")
    void formaDePagoNoEsProducto() {
        Pattern ventanilla = ItemVozParser.patron("\\s*,|\\s+y\\s+|\\s+canal\\b|\\s+por\\b|\\s+con\\b|\\s+en\\b|\\s+para\\b"
                + "|\\s+pag\\w*|\\s+efectivo\\b|\\s+transferencia\\b");
        assertThat(ItemVozParser.extraer(ventanilla,
                NormalizadorVoz.normalizar("Vende dos cajas de festival pago por transferencia.")))
                .containsExactly(new ItemVozParser.ItemExtraido(2, 0, "festival", null));
    }

    @Test
    @DisplayName("dos productos distintos siguen siendo dos ítems")
    void dosProductos() {
        assertThat(extraer("Registra dos cajas de Festival y seis unidades de Solo Lack"))
                .containsExactly(new ItemVozParser.ItemExtraido(2, 0, "festival", null),
                        new ItemVozParser.ItemExtraido(0, 6, "solo lack", null));
    }
}
