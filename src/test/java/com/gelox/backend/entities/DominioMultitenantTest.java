package com.gelox.backend.entities;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * T110-BE1 — métodos de dominio de Distribuidora, RecomendacionCompra e ItemRecomendacion.
 */
class DominioMultitenantTest {

    private static ItemRecomendacion item(int cajasSugeridas, String costoEstimado) {
        return ItemRecomendacion.builder()
                .cajasSugeridas(cajasSugeridas)
                .costoEstimado(new BigDecimal(costoEstimado))
                .build();
    }

    // ---- Distribuidora ----

    @Test
    @DisplayName("permiteAcceso: solo PRUEBA y ACTIVO")
    void permiteAcceso() {
        assertThat(Distribuidora.builder().estado(EstadoDistribuidora.PRUEBA).build().permiteAcceso()).isTrue();
        assertThat(Distribuidora.builder().estado(EstadoDistribuidora.ACTIVO).build().permiteAcceso()).isTrue();
        assertThat(Distribuidora.builder().estado(EstadoDistribuidora.SUSPENDIDO).build().permiteAcceso()).isFalse();
        assertThat(Distribuidora.builder().estado(EstadoDistribuidora.INACTIVO).build().permiteAcceso()).isFalse();
    }

    @Test
    @DisplayName("puedeCambiarA: respeta la tabla de transiciones")
    void puedeCambiarA() {
        Distribuidora activa = Distribuidora.builder().estado(EstadoDistribuidora.ACTIVO).build();
        assertThat(activa.puedeCambiarA(EstadoDistribuidora.SUSPENDIDO)).isTrue();
        assertThat(activa.puedeCambiarA(EstadoDistribuidora.PRUEBA)).isFalse();
        assertThat(activa.puedeCambiarA(EstadoDistribuidora.ACTIVO)).isFalse();
        assertThat(activa.puedeCambiarA(null)).isFalse();

        Distribuidora suspendida = Distribuidora.builder().estado(EstadoDistribuidora.SUSPENDIDO).build();
        assertThat(suspendida.puedeCambiarA(EstadoDistribuidora.ACTIVO)).isTrue();
        assertThat(suspendida.puedeCambiarA(EstadoDistribuidora.INACTIVO)).isTrue();
        assertThat(suspendida.puedeCambiarA(EstadoDistribuidora.PRUEBA)).isFalse();

        Distribuidora prueba = Distribuidora.builder().estado(EstadoDistribuidora.PRUEBA).build();
        assertThat(prueba.puedeCambiarA(EstadoDistribuidora.ACTIVO)).isTrue();
        assertThat(prueba.puedeCambiarA(EstadoDistribuidora.SUSPENDIDO)).isTrue();
        assertThat(prueba.puedeCambiarA(EstadoDistribuidora.INACTIVO)).isTrue();
    }

    @Test
    @DisplayName("INACTIVO es estado final: no admite ninguna transición")
    void inactivoEsFinal() {
        Distribuidora inactiva = Distribuidora.builder().estado(EstadoDistribuidora.INACTIVO).build();
        for (EstadoDistribuidora destino : EstadoDistribuidora.values()) {
            assertThat(inactiva.puedeCambiarA(destino)).as(destino.name()).isFalse();
        }
    }

    // ---- ItemRecomendacion ----

    @Test
    @DisplayName("aprobar: aprueba las cajas sugeridas y registra la fecha")
    void aprobar() {
        ItemRecomendacion item = item(10, "1000.00");

        item.aprobar();

        assertThat(item.getDecision()).isEqualTo(DecisionItem.APROBADO);
        assertThat(item.getCajasAprobadas()).isEqualTo(10);
        assertThat(item.getFechaDecision()).isNotNull();
    }

    @Test
    @DisplayName("modificar: con cajas > 0 queda MODIFICADO")
    void modificar() {
        ItemRecomendacion item = item(10, "1000.00");

        item.modificar(4);

        assertThat(item.getDecision()).isEqualTo(DecisionItem.MODIFICADO);
        assertThat(item.getCajasAprobadas()).isEqualTo(4);
        assertThat(item.getFechaDecision()).isNotNull();
    }

    @Test
    @DisplayName("modificar a 0 cajas equivale a rechazar")
    void modificarACeroRechaza() {
        ItemRecomendacion item = item(10, "1000.00");

        item.modificar(0);

        assertThat(item.getDecision()).isEqualTo(DecisionItem.RECHAZADO);
        assertThat(item.getCajasAprobadas()).isZero();
        assertThat(item.getMotivoRechazo()).isNotBlank();
        assertThat(item.getFechaDecision()).isNotNull();
    }

    @Test
    @DisplayName("modificar con cajas negativas lanza IllegalArgumentException")
    void modificarNegativo() {
        assertThatThrownBy(() -> item(10, "1000.00").modificar(-1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("rechazar: guarda el motivo y registra la fecha")
    void rechazar() {
        ItemRecomendacion item = item(10, "1000.00");

        item.rechazar("Ya hay stock suficiente");

        assertThat(item.getDecision()).isEqualTo(DecisionItem.RECHAZADO);
        assertThat(item.getMotivoRechazo()).isEqualTo("Ya hay stock suficiente");
        assertThat(item.getFechaDecision()).isNotNull();
    }

    // ---- RecomendacionCompra ----

    @Test
    @DisplayName("recalcularCostoAprobado: suma aprobados completos y modificados proporcionales")
    void recalcularCostoAprobado() {
        ItemRecomendacion aprobado = item(10, "1000.00");
        aprobado.aprobar();
        ItemRecomendacion modificado = item(10, "500.00");
        modificado.modificar(4);                      // 500 * 4 / 10 = 200
        ItemRecomendacion rechazado = item(5, "300.00");
        rechazado.rechazar("No aplica");
        ItemRecomendacion pendiente = item(5, "999.00");

        RecomendacionCompra recomendacion = RecomendacionCompra.builder()
                .items(List.of(aprobado, modificado, rechazado, pendiente))
                .build();
        recomendacion.recalcularCostoAprobado();

        assertThat(recomendacion.getCostoAprobado()).isEqualByComparingTo("1200.00");
    }

    @Test
    @DisplayName("admiteDecision: falso solo cuando ya se generó el pedido")
    void admiteDecision() {
        assertThat(RecomendacionCompra.builder().estado(EstadoRecomendacion.GENERADA).build().admiteDecision()).isTrue();
        assertThat(RecomendacionCompra.builder().estado(EstadoRecomendacion.EN_REVISION).build().admiteDecision()).isTrue();
        assertThat(RecomendacionCompra.builder().estado(EstadoRecomendacion.DESCARTADA).build().admiteDecision()).isTrue();
        assertThat(RecomendacionCompra.builder().estado(EstadoRecomendacion.PEDIDO_GENERADO).build().admiteDecision()).isFalse();
    }
}
