package com.gelox.backend.voz;

import com.gelox.backend.TestHelper;
import com.gelox.backend.entities.ComandoVoz;
import com.gelox.backend.entities.EstadoComandoVoz;
import com.gelox.backend.entities.RolUsuario;
import com.gelox.backend.entities.TipoIntencionVoz;
import com.gelox.backend.entities.Usuario;
import com.gelox.backend.repositories.ComandoVozRepository;
import com.gelox.backend.voz.dto.VozConfirmarRequest;
import com.gelox.backend.voz.dto.VozConfirmarResponse;
import com.gelox.backend.voz.dto.VozInterpretarRequest;
import com.gelox.backend.voz.dto.VozInterpretarResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Handler de prueba de punta a punta: clasifica -> interpreta -> confirma -> se guarda en comando_voz.
 */
@ExtendWith(MockitoExtension.class)
class VozServiceTest {

    @Mock
    ComandoVozRepository comandoVozRepository;

    VozService vozService;
    VozPendienteStore pendienteStore;

    HandlerDePrueba handlerVenta;
    HandlerDePrueba handlerInventario;
    Usuario usuario;

    @BeforeEach
    void setUp() {
        pendienteStore = new VozPendienteStore();
        handlerVenta = new HandlerDePrueba(TipoIntencionVoz.REGISTRAR_VENTA, true);
        handlerInventario = new HandlerDePrueba(TipoIntencionVoz.CONSULTAR_INVENTARIO, false);

        vozService = new VozService(
                List.of(handlerVenta, handlerInventario),
                new ClasificadorIntencion(),
                comandoVozRepository,
                pendienteStore);
        vozService.indexarHandlers();

        usuario = TestHelper.buildUsuario("uid-1", "voz@gelox-test.com", RolUsuario.ENCARGADO_VENTAS, true);
    }

    private void stubGuardarComando() {
        when(comandoVozRepository.save(any())).thenAnswer(inv -> {
            ComandoVoz comando = inv.getArgument(0);
            comando.setId(UUID.randomUUID());
            return comando;
        });
    }

    @Test
    @DisplayName("de punta a punta: interpretar pide confirmación y confirmar guarda PROCESADO")
    void interpretarYConfirmar_deExtremoAExtremo() {
        stubGuardarComando();

        VozInterpretarResponse interpretado = vozService.interpretar(
                new VozInterpretarRequest("vende dos festival", 0.95), usuario);

        assertThat(interpretado.requiereConfirmacion()).isTrue();
        assertThat(interpretado.expiraEnSegundos()).isEqualTo(VozPendienteStore.TTL_SEGUNDOS);
        assertThat(interpretado.comandoId()).isNotNull();
        verifyNoInteractions(comandoVozRepository);

        VozConfirmarResponse confirmado = vozService.confirmar(
                new VozConfirmarRequest(interpretado.comandoId(), true), usuario);

        assertThat(confirmado.estado()).isEqualTo("PROCESADO");
        assertThat(confirmado.textoRespuesta()).isEqualTo("Venta registrada");
        assertThat(handlerVenta.payloadRecibidoEnEjecutar).isEqualTo("payload-interno-venta");

        ArgumentCaptor<ComandoVoz> captor = ArgumentCaptor.forClass(ComandoVoz.class);
        verify(comandoVozRepository).save(captor.capture());
        ComandoVoz guardado = captor.getValue();
        assertThat(guardado.getUsuario()).isEqualTo(usuario);
        assertThat(guardado.getTextoTranscrito()).isEqualTo("vende dos festival");
        assertThat(guardado.getEstado()).isEqualTo(EstadoComandoVoz.PROCESADO);
        assertThat(guardado.getIntencion()).isEqualTo(TipoIntencionVoz.REGISTRAR_VENTA);
    }

    @Test
    @DisplayName("confirmar=false guarda CANCELADO y no ejecuta el handler")
    void confirmarFalso_guardaCancelado() {
        stubGuardarComando();

        VozInterpretarResponse interpretado = vozService.interpretar(
                new VozInterpretarRequest("vende dos festival", 0.95), usuario);

        VozConfirmarResponse confirmado = vozService.confirmar(
                new VozConfirmarRequest(interpretado.comandoId(), false), usuario);

        assertThat(confirmado.estado()).isEqualTo("CANCELADO");
        assertThat(handlerVenta.seEjecuto).isFalse();

        ArgumentCaptor<ComandoVoz> captor = ArgumentCaptor.forClass(ComandoVoz.class);
        verify(comandoVozRepository).save(captor.capture());
        assertThat(captor.getValue().getEstado()).isEqualTo(EstadoComandoVoz.CANCELADO);
    }

    @Test
    @DisplayName("si ejecutar falla, se guarda ERROR y se relanza la excepción")
    void siEjecutarFalla_guardaErrorYRelanza() {
        stubGuardarComando();
        handlerVenta.fallarAlEjecutar = true;

        VozInterpretarResponse interpretado = vozService.interpretar(
                new VozInterpretarRequest("vende dos festival", 0.95), usuario);

        assertThatThrownBy(() ->
                vozService.confirmar(new VozConfirmarRequest(interpretado.comandoId(), true), usuario))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("boom");

        ArgumentCaptor<ComandoVoz> captor = ArgumentCaptor.forClass(ComandoVoz.class);
        verify(comandoVozRepository).save(captor.capture());
        assertThat(captor.getValue().getEstado()).isEqualTo(EstadoComandoVoz.ERROR);
        assertThat(captor.getValue().getRespuesta()).isEqualTo("boom");
    }

    @Test
    @DisplayName("confianza baja obliga a confirmar aunque el handler no lo exija")
    void confianzaBajaFuerzaConfirmacion() {
        VozInterpretarResponse interpretado = vozService.interpretar(
                new VozInterpretarRequest("cuantas paletas de festival quedan", 0.5), usuario);

        assertThat(interpretado.requiereConfirmacion()).isTrue();
    }

    @Test
    @DisplayName("confianza alta y sin exigir confirmación: se guarda PROCESADO directo")
    void confianzaAltaSinConfirmacion_guardaDirecto() {
        stubGuardarComando();

        VozInterpretarResponse interpretado = vozService.interpretar(
                new VozInterpretarRequest("cuantas paletas de festival quedan", 0.95), usuario);

        assertThat(interpretado.requiereConfirmacion()).isFalse();
        assertThat(interpretado.expiraEnSegundos()).isNull();

        ArgumentCaptor<ComandoVoz> captor = ArgumentCaptor.forClass(ComandoVoz.class);
        verify(comandoVozRepository).save(captor.capture());
        assertThat(captor.getValue().getEstado()).isEqualTo(EstadoComandoVoz.PROCESADO);
    }

    @Test
    @DisplayName("sin intención reconocida se guarda ERROR con un ejemplo de uso")
    void sinIntencion_guardaError() {
        stubGuardarComando();

        VozInterpretarResponse interpretado = vozService.interpretar(
                new VozInterpretarRequest("qué clima hace hoy", 0.9), usuario);

        assertThat(interpretado.intencion()).isNull();
        assertThat(interpretado.requiereConfirmacion()).isFalse();

        ArgumentCaptor<ComandoVoz> captor = ArgumentCaptor.forClass(ComandoVoz.class);
        verify(comandoVozRepository).save(captor.capture());
        assertThat(captor.getValue().getEstado()).isEqualTo(EstadoComandoVoz.ERROR);
        assertThat(captor.getValue().getIntencion()).isNull();
    }

    @Test
    @DisplayName("\"agrega ...\" sin venta en curso explica que primero hay que decir la venta")
    void agregadoSinVentaEnCurso_mensajeEspecifico() {
        stubGuardarComando();

        VozInterpretarResponse interpretado = vozService.interpretar(
                new VozInterpretarRequest("agrega dos unidades de aloha mango biche", 0.9), usuario);

        assertThat(interpretado.ok()).isFalse();
        assertThat(interpretado.intencion()).isNull();
        assertThat(interpretado.textoRespuesta()).startsWith("No hay una venta en curso a la que agregar.");
        assertThat(handlerVenta.seEjecuto).isFalse();
    }

    @Test
    @DisplayName("si el handler responde ok=false: ok=false, no pide confirmación y se guarda ERROR")
    void handlerSinExito_noCreaPendienteYResponseOkFalso() {
        stubGuardarComando();
        handlerVenta.interpretarSinExito = true;

        VozInterpretarResponse interpretado = vozService.interpretar(
                new VozInterpretarRequest("vende dos festival", 0.95), usuario);

        assertThat(interpretado.ok()).isFalse();
        assertThat(interpretado.requiereConfirmacion()).isFalse();
        assertThat(interpretado.expiraEnSegundos()).isNull();
        assertThat(interpretado.textoRespuesta()).isEqualTo("No hay pedidos pendientes.");

        ArgumentCaptor<ComandoVoz> captor = ArgumentCaptor.forClass(ComandoVoz.class);
        verify(comandoVozRepository).save(captor.capture());
        assertThat(captor.getValue().getEstado()).isEqualTo(EstadoComandoVoz.ERROR);

        assertThatThrownBy(() ->
                vozService.confirmar(new VozConfirmarRequest(interpretado.comandoId(), true), usuario))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode().value()).isEqualTo(404));
    }

    @Test
    @DisplayName("una interpretación exitosa responde ok=true")
    void interpretacionExitosa_okTrue() {
        VozInterpretarResponse interpretado = vozService.interpretar(
                new VozInterpretarRequest("vende dos festival", 0.95), usuario);

        assertThat(interpretado.ok()).isTrue();
    }

    @Test
    @DisplayName("intención sin handler registrado se guarda ERROR")
    void sinHandlerRegistrado_guardaError() {
        stubGuardarComando();

        VozInterpretarResponse interpretado = vozService.interpretar(
                new VozInterpretarRequest("genera un pedido a nutresa", 0.9), usuario);

        assertThat(interpretado.intencion()).isEqualTo(TipoIntencionVoz.GENERAR_PEDIDO.name());

        ArgumentCaptor<ComandoVoz> captor = ArgumentCaptor.forClass(ComandoVoz.class);
        verify(comandoVozRepository).save(captor.capture());
        assertThat(captor.getValue().getEstado()).isEqualTo(EstadoComandoVoz.ERROR);
    }

    @Test
    @DisplayName("confirmar con un comandoId inexistente responde 404")
    void confirmarComandoInexistente_404() {
        assertThatThrownBy(() ->
                vozService.confirmar(new VozConfirmarRequest(UUID.randomUUID(), true), usuario))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode().value()).isEqualTo(404));
    }

    private static class HandlerDePrueba implements IntencionHandler {

        private final TipoIntencionVoz tipo;
        private final boolean requiereConfirmacion;
        boolean seEjecuto = false;
        boolean fallarAlEjecutar = false;
        boolean interpretarSinExito = false;
        Object payloadRecibidoEnEjecutar;

        HandlerDePrueba(TipoIntencionVoz tipo, boolean requiereConfirmacion) {
            this.tipo = tipo;
            this.requiereConfirmacion = requiereConfirmacion;
        }

        @Override
        public TipoIntencionVoz tipo() {
            return tipo;
        }

        @Override
        public boolean requiereConfirmacion() {
            return requiereConfirmacion;
        }

        @Override
        public VozResultado interpretar(VozContexto ctx) {
            if (interpretarSinExito) {
                return new VozResultado(false, "No hay pedidos pendientes.", Map.of(), null);
            }
            String payload = tipo == TipoIntencionVoz.REGISTRAR_VENTA ? "payload-interno-venta" : null;
            return new VozResultado(true, "¿Confirmas? " + ctx.texto(), Map.of(), payload);
        }

        @Override
        public VozResultado ejecutar(VozPendiente pendiente, Usuario usuario) {
            seEjecuto = true;
            payloadRecibidoEnEjecutar = pendiente.payload();
            if (fallarAlEjecutar) throw new IllegalStateException("boom");
            return new VozResultado(true, "Venta registrada", Map.of("ok", true), null);
        }
    }
}
