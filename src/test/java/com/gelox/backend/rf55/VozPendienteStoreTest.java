package com.gelox.backend.rf55;

import com.gelox.backend.entities.TipoIntencionVoz;
import com.gelox.backend.voz.VozPendiente;
import com.gelox.backend.voz.VozPendienteStore;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** T115-BE3 — cada pendiente guarda su propio vencimiento (FP-15 §2.11.5). */
class VozPendienteStoreTest {

    private final VozPendienteStore store = new VozPendienteStore();

    private Duration plazo(TipoIntencionVoz intencion) {
        UUID comandoId = UUID.randomUUID();
        UUID usuarioId = UUID.randomUUID();
        Instant antes = Instant.now();
        store.put(comandoId, usuarioId, intencion, "payload");
        VozPendiente pendiente = store.take(comandoId, usuarioId);
        return Duration.between(antes, pendiente.expiraEn());
    }

    @Test
    @DisplayName("una decisión de la recomendación vence a los 15 s")
    void decisionRecomendacion_15Segundos() {
        assertThat(VozPendienteStore.ttlSegundos(TipoIntencionVoz.DECIDIR_RECOMENDACION)).isEqualTo(15);
        assertThat(plazo(TipoIntencionVoz.DECIDIR_RECOMENDACION)).isBetween(Duration.ofSeconds(14), Duration.ofSeconds(16));
    }

    @Test
    @DisplayName("una venta por voz sigue venciendo a los 45 s")
    void venta_45Segundos() {
        assertThat(VozPendienteStore.ttlSegundos(TipoIntencionVoz.REGISTRAR_VENTA)).isEqualTo(45);
        assertThat(plazo(TipoIntencionVoz.REGISTRAR_VENTA)).isBetween(Duration.ofSeconds(44), Duration.ofSeconds(46));
    }

    @Test
    @DisplayName("las demás operaciones con confirmación también quedan en 45 s")
    void demasOperaciones_45Segundos() {
        for (TipoIntencionVoz intencion : TipoIntencionVoz.values()) {
            if (intencion == TipoIntencionVoz.DECIDIR_RECOMENDACION) continue;
            assertThat(VozPendienteStore.ttlSegundos(intencion)).as(intencion.name()).isEqualTo(VozPendienteStore.TTL_SEGUNDOS);
        }
    }
}
