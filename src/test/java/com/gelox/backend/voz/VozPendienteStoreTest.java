package com.gelox.backend.voz;

import com.gelox.backend.entities.TipoIntencionVoz;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VozPendienteStoreTest {

    private final VozPendienteStore store = new VozPendienteStore();

    @Test
    void elTtlEsDe45Segundos() {
        assertThat(VozPendienteStore.TTL_SEGUNDOS).isEqualTo(45);
    }

    @Test
    void seGuardaYSeRecuperaUnaSolaVez() {
        UUID comandoId = UUID.randomUUID();
        UUID usuarioId = UUID.randomUUID();

        store.put(comandoId, usuarioId, TipoIntencionVoz.REGISTRAR_VENTA, "payload");

        VozPendiente pendiente = store.take(comandoId, usuarioId);
        assertThat(pendiente.intencion()).isEqualTo(TipoIntencionVoz.REGISTRAR_VENTA);
        assertThat(pendiente.payload()).isEqualTo("payload");

        assertThatThrownBy(() -> store.take(comandoId, usuarioId))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode().value()).isEqualTo(404));
    }

    @Test
    void noExisteDevuelve404() {
        assertThatThrownBy(() -> store.take(UUID.randomUUID(), UUID.randomUUID()))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode().value()).isEqualTo(404));
    }

    @Test
    void deOtroUsuarioDevuelve403() {
        UUID comandoId = UUID.randomUUID();
        store.put(comandoId, UUID.randomUUID(), TipoIntencionVoz.REGISTRAR_VENTA, "payload");

        assertThatThrownBy(() -> store.take(comandoId, UUID.randomUUID()))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode().value()).isEqualTo(403));
    }

    @Test
    void venceAlPasarElTtlDevuelve410() throws Exception {
        UUID comandoId = UUID.randomUUID();
        UUID usuarioId = UUID.randomUUID();
        store.put(comandoId, usuarioId, TipoIntencionVoz.REGISTRAR_VENTA, "payload");

        forzarVencimiento(comandoId);

        assertThatThrownBy(() -> store.take(comandoId, usuarioId))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> {
                    assertThat(e.getStatusCode().value()).isEqualTo(410);
                    assertThat(e.getReason()).isEqualTo("La confirmación expiró. Repite el comando.");
                });
    }

    @SuppressWarnings("unchecked")
    private void forzarVencimiento(UUID comandoId) throws Exception {
        Field campo = VozPendienteStore.class.getDeclaredField("pendientes");
        campo.setAccessible(true);
        var mapa = (ConcurrentHashMap<UUID, VozPendiente>) campo.get(store);

        VozPendiente vencido = mapa.get(comandoId);
        mapa.put(comandoId, new VozPendiente(
                vencido.comandoId(), vencido.usuarioId(), vencido.intencion(), vencido.payload(),
                Instant.now().minusSeconds(1)));
    }
}
