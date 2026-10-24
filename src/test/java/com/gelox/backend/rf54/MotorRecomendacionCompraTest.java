package com.gelox.backend.rf54;

import com.gelox.backend.entities.AjustePresupuesto;
import com.gelox.backend.entities.Producto;
import com.gelox.backend.entities.UrgenciaRecomendacion;
import com.gelox.backend.recomendacion.DemandaRepository.DemandaDia;
import com.gelox.backend.recomendacion.MotorRecomendacionCompra;
import com.gelox.backend.recomendacion.MotorRecomendacionCompra.ItemCalculado;
import com.gelox.backend.recomendacion.MotorRecomendacionCompra.Resultado;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RF54 — MotorRecomendacionCompra con los ejemplos resueltos del FP-15 (secciones 2.10.6 y
 * 2.10.7). JUnit sin Spring: el motor no accede a la base.
 */
class MotorRecomendacionCompraTest {

    /** Fecha de los datos de Supabase del FP-15: la ventana va del 20/07 al 17/10/2026. */
    static final LocalDate HOY = LocalDate.of(2026, 10, 17);

    MotorRecomendacionCompra motor;

    /** Productos con datos reales de la sección 2.10.7. */
    Producto limon;
    Producto mango;
    Producto frutos;
    Producto bocatto;
    List<DemandaDia> demandaReal;

    @BeforeEach
    void setUp() {
        motor = new MotorRecomendacionCompra(2, 7);   // L = 2, R = 7
        limon = paletaLimon();
        mango = mangoBiche();
        frutos = frutosRojos();
        bocatto = bocattoConCobertura();
        demandaReal = new ArrayList<>(ventasMango(mango));
        demandaReal.addAll(ventasBocatto(bocatto));
    }

    // ── Datos de prueba ─────────────────────────────────────────────────────

    static Producto producto(String nombre, int stock, int minimo, Integer unidadesCaja,
                             String precioCosto, String precioVenta) {
        Producto p = new Producto();
        p.setId(UUID.randomUUID());
        p.setNombre(nombre);
        p.setStockActual(stock);
        p.setStockMinimo(minimo);
        p.setUnidadesPorCaja(unidadesCaja);
        p.setPrecioCosto(precioCosto == null ? null : new BigDecimal(precioCosto));
        p.setPrecioVenta(new BigDecimal(precioVenta));
        p.setActivo(true);
        return p;
    }

    /** Aloha Mango Biche (2.10.6): 3 unidades el 24/09 y 3 el 25/09. */
    static Producto mangoBiche() {
        return producto("Aloha Mango Biche", 17, 20, 24, "48840", "3500");
    }

    static List<DemandaDia> ventasMango(Producto mango) {
        return List.of(
                new DemandaDia(mango.getId(), LocalDate.of(2026, 9, 24), 3),
                new DemandaDia(mango.getId(), LocalDate.of(2026, 9, 25), 3));
    }

    /** Sin ventas en la ventana, stock 1 y mínimo 5 → CRÍTICO, 1 caja de $33.240 (44,6 %). */
    static Producto paletaLimon() {
        return producto("Aloha Paleta Limón", 1, 5, 24, "33240", "2500");
    }

    /** Sin ventas, stock 0 y mínimo 5 → CRÍTICO (100), 1 caja de $36.640 (38,9 %). */
    static Producto frutosRojos() {
        return producto("Aloha Paleta Frutos Rojos Mix", 0, 5, 24, "36640", "2500");
    }

    /** Escenario ilustrativo: demanda estable de 4 a 9 u/día (6,4 en promedio) y 20 en stock. */
    static Producto jetVainilla() {
        return producto("Jet Paleta Vainilla", 20, 0, 20, "47908", "3000");
    }

    static List<DemandaDia> ventasJet(Producto jet) {
        int[] patron = {4, 5, 6, 7, 8, 9, 6, 7, 5, 7};
        List<DemandaDia> ventas = new ArrayList<>();
        for (int i = 0; i < 90; i++) {
            ventas.add(new DemandaDia(jet.getId(), HOY.minusDays(89 - i), patron[i % patron.length]));
        }
        return ventas;
    }

    /** Producto con ventas recientes y cobertura suficiente: no se sugiere. */
    static Producto bocattoConCobertura() {
        return producto("Bocatto Cono Fresa", 200, 10, 12, "40000", "6000");
    }

    static List<DemandaDia> ventasBocatto(Producto bocatto) {
        return List.of(new DemandaDia(bocatto.getId(), HOY.minusDays(3), 6));
    }

    static ItemCalculado item(Resultado r, Producto p) {
        return r.items().stream().filter(i -> i.producto() == p).findFirst().orElseThrow();
    }

    static BigDecimal bd(String valor) {
        return new BigDecimal(valor);
    }

    // ── 2.10.6 — Aloha Mango Biche ──────────────────────────────────

    @Test
    @DisplayName("SS 20, ROP 20,2, CRÍTICO, score 67, 1 caja, $48.840 y margen $35.160")
    void reproduceElEjemploResuelto() {
        Resultado r = motor.calcular(List.of(mango), ventasMango(mango), bd("1000000"), HOY);

        ItemCalculado i = item(r, mango);
        assertThat(i.demandaDiaria()).isEqualByComparingTo("0.067");
        assertThat(i.factorEstacionalidad()).isEqualByComparingTo("1.50");
        assertThat(i.stockSeguridad()).isEqualByComparingTo("20.00");
        assertThat(i.puntoReorden()).isEqualByComparingTo("20.20");
        assertThat(i.coberturaDias()).isEqualByComparingTo("170.00");
        assertThat(i.urgencia()).isEqualTo(UrgenciaRecomendacion.CRITICO);
        assertThat(i.score()).isEqualByComparingTo("67.00");
        assertThat(i.unidadesSugeridas()).isEqualTo(4);          // q = 3,9
        assertThat(i.cajasSugeridas()).isEqualTo(1);
        assertThat(i.costoEstimado()).isEqualByComparingTo("48840");
        assertThat(i.margenEsperado()).isEqualByComparingTo("35160");
        assertThat(i.ajustePresupuesto()).isEqualTo(AjustePresupuesto.COMPLETO);
        assertThat(r.costoTotal()).isEqualByComparingTo("48840");
    }

    @Test
    @DisplayName("Justificación con la plantilla de stock de seguridad y la frase de estacionalidad")
    void justificacion() {
        Resultado r = motor.calcular(List.of(mango), ventasMango(mango), bd("1000000"), HOY);

        assertThat(item(r, mango).justificacion()).isEqualTo(
                "Crítico: 17 unidades, por debajo del stock de seguridad (20). "
                        + "Promedio de 0,1 unidades/día en 90 días. "
                        + "Las ventas del último mes van 1,5× por encima del promedio.");
    }

    @Test
    @DisplayName("Las ventas fuera de la ventana de 90 días no cuentan")
    void ignoraVentasFueraDeLaVentana() {
        List<DemandaDia> ventas = new ArrayList<>(ventasMango(mango));
        ventas.add(new DemandaDia(mango.getId(), HOY.minusDays(90), 500));   // 18/07
        ventas.add(new DemandaDia(mango.getId(), HOY.plusDays(1), 500));

        Resultado r = motor.calcular(List.of(mango), ventas, bd("1000000"), HOY);

        assertThat(item(r, mango).demandaDiaria()).isEqualByComparingTo("0.067");
    }

    // ── 2.10.7 — Presupuesto limitado ───────────────────────────────

    @Test
    @DisplayName("Con datos reales sugiere Limón, Mango y Frutos Rojos, ordenados por margen %")
    void ordenPorUrgenciaYMargen() {
        Resultado r = motor.calcular(List.of(frutos, bocatto, mango, limon), demandaReal, bd("1000000"), HOY);

        assertThat(r.items()).extracting(ItemCalculado::producto).containsExactly(limon, mango, frutos);
        assertThat(r.items()).extracting(ItemCalculado::prioridad).containsExactly(1, 2, 3);
        assertThat(item(r, limon).score()).isEqualByComparingTo("93.40");
        assertThat(item(r, mango).score()).isEqualByComparingTo("67.00");
        assertThat(item(r, frutos).score()).isEqualByComparingTo("100.00");
        assertThat(r.items()).allMatch(i -> i.urgencia() == UrgenciaRecomendacion.CRITICO);
    }

    @Test
    @DisplayName("$80.000 → Mango no cabe (EXCLUIDO) y se sigue con Frutos Rojos: total $69.880")
    void presupuesto80Mil() {
        Resultado r = motor.calcular(List.of(limon, mango, frutos, bocatto), demandaReal, bd("80000"), HOY);

        assertThat(item(r, limon).ajustePresupuesto()).isEqualTo(AjustePresupuesto.COMPLETO);
        assertThat(item(r, limon).costoEstimado()).isEqualByComparingTo("33240");

        ItemCalculado mangoExcluido = item(r, mango);
        assertThat(mangoExcluido.ajustePresupuesto()).isEqualTo(AjustePresupuesto.EXCLUIDO);
        assertThat(mangoExcluido.cajasSugeridas()).isZero();
        assertThat(mangoExcluido.costoEstimado()).isEqualByComparingTo("0");
        assertThat(mangoExcluido.margenEsperado()).isEqualByComparingTo("0");
        assertThat(mangoExcluido.justificacion()).endsWith("Excede el presupuesto.");

        assertThat(item(r, frutos).ajustePresupuesto()).isEqualTo(AjustePresupuesto.COMPLETO);
        assertThat(item(r, frutos).costoEstimado()).isEqualByComparingTo("36640");
        assertThat(r.costoTotal()).isEqualByComparingTo("69880");
    }

    @Test
    @DisplayName("Jet Paleta Vainilla ilustrativo: ESTABLE (≈22) con 3 cajas por $143.724")
    void escenarioIlustrativoJet() {
        Producto jet = jetVainilla();
        Resultado r = motor.calcular(List.of(jet), ventasJet(jet), bd("1000000"), HOY);

        ItemCalculado i = item(r, jet);
        assertThat(i.demandaDiaria()).isEqualByComparingTo("6.400");
        assertThat(i.urgencia()).isEqualTo(UrgenciaRecomendacion.ESTABLE);
        assertThat(i.score().doubleValue()).isBetween(21.5, 22.5);
        assertThat(i.cajasSugeridas()).isEqualTo(3);
        assertThat(i.costoEstimado()).isEqualByComparingTo("143724");
        assertThat(i.justificacion()).startsWith("Estable: cubre 3,1 días; se sugiere completar hasta el próximo pedido (2+7 días).");
    }

    @Test
    @DisplayName("$170.000 con Jet → Jet queda REDUCIDO a 1 caja de $47.908: total $166.628")
    void presupuesto170Mil() {
        Producto jet = jetVainilla();
        List<DemandaDia> demanda = new ArrayList<>(demandaReal);
        demanda.addAll(ventasJet(jet));

        Resultado r = motor.calcular(List.of(jet, limon, mango, frutos, bocatto), demanda, bd("170000"), HOY);

        assertThat(r.items()).extracting(ItemCalculado::producto).containsExactly(limon, mango, frutos, jet);
        assertThat(r.items()).extracting(ItemCalculado::ajustePresupuesto).containsExactly(
                AjustePresupuesto.COMPLETO, AjustePresupuesto.COMPLETO,
                AjustePresupuesto.COMPLETO, AjustePresupuesto.REDUCIDO);

        ItemCalculado jetReducido = item(r, jet);
        assertThat(jetReducido.cajasSugeridas()).isEqualTo(1);
        assertThat(jetReducido.costoEstimado()).isEqualByComparingTo("47908");
        assertThat(jetReducido.margenEsperado()).isEqualByComparingTo("12092");   // 3000 × 20 − 47908
        assertThat(jetReducido.justificacion()).endsWith("Reducido a 1 caja por presupuesto.");
        assertThat(r.costoTotal()).isEqualByComparingTo("166628");
    }

    // ── Productos sin ventas ────────────────────────────────────────

    @Test
    @DisplayName("Stock sobre el mínimo → no se recomienda")
    void sobreElMinimo() {
        Producto p = producto("Sin ventas", 30, 10, 12, "20000", "2500");
        Resultado r = motor.calcular(List.of(p), List.of(), bd("1000000"), HOY);

        assertThat(r.items()).isEmpty();
        assertThat(r.productosExcluidos()).isEmpty();
        assertThat(r.costoTotal()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("Bajo el mínimo → CRÍTICO con q = mínimo − stock")
    void bajoElMinimo() {
        Producto p = producto("Sin ventas", 3, 10, 12, "20000", "2500");
        Resultado r = motor.calcular(List.of(p), List.of(), bd("1000000"), HOY);

        ItemCalculado i = item(r, p);
        assertThat(i.urgencia()).isEqualTo(UrgenciaRecomendacion.CRITICO);
        assertThat(i.unidadesSugeridas()).isEqualTo(7);
        assertThat(i.cajasSugeridas()).isEqualTo(1);
        assertThat(i.stockSeguridad()).isEqualByComparingTo("10.00");
        assertThat(i.coberturaDias()).isNull();
        assertThat(i.score()).isEqualByComparingTo("90.10");   // 67 + 33 × (1 − 3/10)
        assertThat(i.justificacion()).isEqualTo(
                "Crítico: 3 unidades, por debajo del mínimo configurado (10). Sin ventas en los últimos 90 días.");
    }

    // ── Urgencias, orden y márgenes ─────────────────────────────────

    @Test
    @DisplayName("CRÍTICO por cobertura menor al tiempo de entrega")
    void criticoPorCobertura() {
        Producto p = producto("Alta rotación", 15, 0, 12, "30000", "4000");
        List<DemandaDia> ventas = new ArrayList<>();
        for (int i = 0; i < 90; i++) ventas.add(new DemandaDia(p.getId(), HOY.minusDays(i), 10));

        ItemCalculado i = item(motor.calcular(List.of(p), ventas, bd("1000000"), HOY), p);

        assertThat(i.urgencia()).isEqualTo(UrgenciaRecomendacion.CRITICO);   // c = 1,5 < L = 2
        assertThat(i.coberturaDias()).isEqualByComparingTo("1.50");
        assertThat(i.justificacion()).isEqualTo(
                "Crítico: quedan 1,5 días de inventario y el proveedor tarda 2 días. "
                        + "Promedio de 10 unidades/día en 90 días.");
    }

    @Test
    @DisplayName("ALERTA cuando no es crítico y el stock está bajo el punto de reorden")
    void alerta() {
        Producto p = producto("En alerta", 25, 0, 12, "30000", "4000");
        List<DemandaDia> ventas = new ArrayList<>();
        for (int i = 0; i < 90; i++) ventas.add(new DemandaDia(p.getId(), HOY.minusDays(i), 10));

        ItemCalculado i = item(motor.calcular(List.of(p), ventas, bd("1000000"), HOY), p);

        // D = 10 y σ = 0: sin mínimo SS = 0 y ROP = 20 < 25 → ESTABLE
        assertThat(i.urgencia()).isEqualTo(UrgenciaRecomendacion.ESTABLE);

        p.setStockMinimo(10);   // SS = 10, ROP = 30 ≥ 25 y c = 2,5 ≥ L
        ItemCalculado alerta = item(motor.calcular(List.of(p), ventas, bd("1000000"), HOY), p);
        assertThat(alerta.urgencia()).isEqualTo(UrgenciaRecomendacion.ALERTA);
        assertThat(alerta.score()).isEqualByComparingTo("57.83");   // 34 + 33 × (1 − 2,5/9)
        assertThat(alerta.justificacion()).isEqualTo(
                "Alerta: 25 unidades, por debajo del punto de reorden (30). Alcanza para 2,5 días.");
    }

    @Test
    @DisplayName("precio_venta 0 → margen 0 y queda al final de su grupo de urgencia")
    void precioVentaCero() {
        Producto sinPrecio = producto("Sin precio de venta", 0, 10, 12, "20000", "0");  // score 100
        Producto conMargen = producto("Con margen", 8, 10, 12, "20000", "2500");      // score 73,6
        Producto alerta = producto("Alerta", 25, 10, 12, "30000", "4000");
        List<DemandaDia> ventas = new ArrayList<>();
        for (int i = 0; i < 90; i++) ventas.add(new DemandaDia(alerta.getId(), HOY.minusDays(i), 10));

        Resultado r = motor.calcular(List.of(sinPrecio, alerta, conMargen), ventas, bd("1000000"), HOY);

        assertThat(r.items()).extracting(ItemCalculado::producto).containsExactly(conMargen, sinPrecio, alerta);
        assertThat(item(r, sinPrecio).margenEsperado()).isEqualByComparingTo("0");
        assertThat(item(r, sinPrecio).urgencia()).isEqualTo(UrgenciaRecomendacion.CRITICO);
    }

    @Test
    @DisplayName("Mismo margen % → desempata el score")
    void desempatePorScore() {
        Producto a = producto("A", 8, 10, 12, "20000", "2500");   // score 73,6
        Producto b = producto("B", 0, 10, 12, "20000", "2500");   // score 100
        Resultado r = motor.calcular(List.of(a, b), List.of(), bd("1000000"), HOY);

        assertThat(r.items()).extracting(ItemCalculado::producto).containsExactly(b, a);
    }

    @Test
    @DisplayName("Ventas del último mes por debajo del promedio → frase «por debajo»")
    void estacionalidadPorDebajo() {
        Producto p = producto("Temporada baja", 5, 0, 12, "30000", "4000");
        List<DemandaDia> ventas = new ArrayList<>();
        for (int i = 28; i < 90; i++) ventas.add(new DemandaDia(p.getId(), HOY.minusDays(i), 4));

        ItemCalculado i = item(motor.calcular(List.of(p), ventas, bd("1000000"), HOY), p);

        assertThat(i.factorEstacionalidad()).isEqualByComparingTo("0.70");
        assertThat(i.justificacion()).contains("Las ventas del último mes van 0,7× por debajo del promedio.");
    }

    // ── Exclusiones por datos incompletos ───────────────────────────

    @Test
    @DisplayName("Sin precio de costo, sin unidades por caja o inactivo → productosExcluidos")
    void excluidosConMotivo() {
        Producto sinCosto = producto("Sin costo", 0, 10, 12, null, "2500");
        Producto costoCero = producto("Costo cero", 0, 10, 12, "0", "2500");
        Producto sinCaja = producto("Sin caja", 0, 10, null, "20000", "2500");
        Producto inactivo = producto("Inactivo", 0, 10, 12, "20000", "2500");
        inactivo.setActivo(false);

        Resultado r = motor.calcular(List.of(sinCosto, costoCero, sinCaja, inactivo), List.of(), bd("1000000"), HOY);

        assertThat(r.items()).isEmpty();
        assertThat(r.productosExcluidos())
                .extracting(MotorRecomendacionCompra.ProductoExcluido::motivo)
                .containsExactly("Sin precio de costo", "Sin precio de costo", "Sin unidades por caja",
                        "Producto inactivo");
    }
}
