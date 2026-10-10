package com.gelox.backend.voz;

import com.gelox.backend.TestHelper;
import com.gelox.backend.entities.ComandoVoz;
import com.gelox.backend.entities.EstadoComandoVoz;
import com.gelox.backend.entities.MetodoClasificacion;
import com.gelox.backend.entities.RolUsuario;
import com.gelox.backend.entities.TipoIntencionVoz;
import com.gelox.backend.entities.Usuario;
import com.gelox.backend.repositories.ComandoVozRepository;
import com.gelox.backend.voz.dto.VozConfirmarRequest;
import com.gelox.backend.voz.dto.VozConfirmarResponse;
import com.gelox.backend.voz.dto.VozInterpretarRequest;
import com.gelox.backend.voz.dto.VozInterpretarResponse;
import com.gelox.backend.voz.handlers.PendienteAclaracion;
import com.gelox.backend.voz.pln.ModeloIntencion;
import org.assertj.core.data.Offset;
import org.junit.jupiter.api.BeforeAll;
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

    /** Modelo de PLN real (src/main/resources/pln): los casos de regresión dependen de él. */
    static ModeloIntencion modelo;
    ClasificadorIntencion clasificador;

    @BeforeAll
    static void cargarModelo() {
        modelo = new ModeloIntencion();
        modelo.cargar();
        assertThat(modelo.disponible()).as("el modelo de intención debe cargar desde el classpath").isTrue();
    }

    HandlerDePrueba handlerVenta;
    HandlerDePrueba handlerInventario;
    Usuario usuario;

    @BeforeEach
    void setUp() {
        pendienteStore = new VozPendienteStore();
        handlerVenta = new HandlerDePrueba(TipoIntencionVoz.REGISTRAR_VENTA, true);
        handlerInventario = new HandlerDePrueba(TipoIntencionVoz.CONSULTAR_INVENTARIO, false);

        clasificador = new ClasificadorIntencion(modelo);
        vozService = new VozService(
                List.of(handlerVenta, handlerInventario),
                clasificador,
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
        // CP-04: la respuesta de la consulta no se entrega antes de confirmar.
        assertThat(interpretado.textoRespuesta()).isEqualTo("Entendí: \"cuantas paletas de festival quedan\". ¿Confirmas?");
        assertThat(interpretado.datos()).isEmpty();
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

    // ---- PLN (FP-15 §2.1, §2.5) y regresiones de la sección 6 ----

    /** Payload de una venta que preguntó "¿para quién?" y espera la respuesta. */
    private record AclaracionDePrueba(String pregunta) implements PendienteAclaracion {}

    @Test
    @DisplayName("PLN — al confirmar se guardan la probabilidad y el método de la interpretación original")
    void confirmar_guardaProbabilidadYMetodoOriginales() {
        stubGuardarComando();
        var clasificacion = clasificador.clasificar("vendame 2 de solo lac");

        VozInterpretarResponse interpretado = vozService.interpretar(
                new VozInterpretarRequest("vendame 2 de solo lac", 0.95), usuario);
        vozService.confirmar(new VozConfirmarRequest(interpretado.comandoId(), true), usuario);

        ArgumentCaptor<ComandoVoz> captor = ArgumentCaptor.forClass(ComandoVoz.class);
        verify(comandoVozRepository).save(captor.capture());
        ComandoVoz guardado = captor.getValue();
        assertThat(guardado.getMetodoClasificacion()).isEqualTo(MetodoClasificacion.MODELO);
        assertThat(guardado.getProbabilidadIntencion()).isNotNull();
        assertThat(guardado.getProbabilidadIntencion().doubleValue())
                .isCloseTo(clasificacion.probabilidadIntencion(), Offset.offset(0.001));
    }

    @Test
    @DisplayName("PLN — CANCELADO también guarda probabilidad y método")
    void cancelar_guardaProbabilidadYMetodo() {
        stubGuardarComando();
        VozInterpretarResponse interpretado = vozService.interpretar(
                new VozInterpretarRequest("vende dos festival", 0.95), usuario);
        vozService.confirmar(new VozConfirmarRequest(interpretado.comandoId(), false), usuario);

        ArgumentCaptor<ComandoVoz> captor = ArgumentCaptor.forClass(ComandoVoz.class);
        verify(comandoVozRepository).save(captor.capture());
        assertThat(captor.getValue().getEstado()).isEqualTo(EstadoComandoVoz.CANCELADO);
        assertThat(captor.getValue().getMetodoClasificacion()).isNotNull();
        assertThat(captor.getValue().getProbabilidadIntencion()).isNotNull();
    }

    @Test
    @DisplayName("PLN — frase fuera de dominio: ERROR con la probabilidad del modelo y método null")
    void fueraDeDominio_guardaErrorConProbabilidad() {
        stubGuardarComando();
        vozService.interpretar(new VozInterpretarRequest("hola buenos dias", 0.9), usuario);

        ArgumentCaptor<ComandoVoz> captor = ArgumentCaptor.forClass(ComandoVoz.class);
        verify(comandoVozRepository).save(captor.capture());
        assertThat(captor.getValue().getEstado()).isEqualTo(EstadoComandoVoz.ERROR);
        assertThat(captor.getValue().getIntencion()).isNull();
        assertThat(captor.getValue().getMetodoClasificacion()).isNull();
        assertThat(captor.getValue().getProbabilidadIntencion()).isNotNull();
    }

    @Test
    @DisplayName("Regresión §6 — 'agrega 2 cajas de festival' se suma a la venta en curso (mismo comandoId), no es MODIFICAR_PEDIDO")
    void agregadoSecuencial_continuaLaVentaEnCurso() {
        VozInterpretarResponse venta = vozService.interpretar(
                new VozInterpretarRequest("vende dos festival", 0.95), usuario);

        VozInterpretarResponse agregado = vozService.interpretar(
                new VozInterpretarRequest("agrega 2 cajas de festival", 0.95), usuario);

        assertThat(agregado.comandoId()).isEqualTo(venta.comandoId());
        assertThat(agregado.intencion()).isEqualTo(TipoIntencionVoz.REGISTRAR_VENTA.name());
        assertThat(handlerVenta.ultimoContexto.pendiente()).as("el handler recibe la venta en curso").isNotNull();
        verifyNoInteractions(comandoVozRepository);
    }

    @Test
    @DisplayName("Regresión §6 — 'agrega ...' sin venta en curso sigue explicando que primero hay que decir la venta")
    void agregadoSinVenta_noEsVentaNueva() {
        stubGuardarComando();
        VozInterpretarResponse r = vozService.interpretar(
                new VozInterpretarRequest("agrega 2 cajas de festival", 0.9), usuario);

        assertThat(r.ok()).isFalse();
        assertThat(r.intencion()).isNull();
        assertThat(r.textoRespuesta()).contains("No hay una venta en curso");
    }

    @Test
    @DisplayName("Regresión §6 — 'doña marta' y 'fresa' completan la venta que espera aclaración")
    void aclaracion_fraseSueltaCompletaLaVenta() {
        handlerVenta.payloadInterpretar = new AclaracionDePrueba("¿Para quién es la venta rural?");
        VozInterpretarResponse venta = vozService.interpretar(
                new VozInterpretarRequest("anota una venta rural de tres festival", 0.95), usuario);

        for (String respuesta : List.of("doña marta", "fresa")) {
            VozInterpretarResponse aclaracion = vozService.interpretar(
                    new VozInterpretarRequest(respuesta, 0.95), usuario);
            assertThat(aclaracion.comandoId()).as("'%s' continúa la misma venta", respuesta)
                    .isEqualTo(venta.comandoId());
            assertThat(aclaracion.intencion()).isEqualTo(TipoIntencionVoz.REGISTRAR_VENTA.name());
            assertThat(handlerVenta.ultimoContexto.texto()).isEqualTo(respuesta);
            assertThat(handlerVenta.ultimoContexto.pendiente()).isNotNull();
        }
    }

    @Test
    @DisplayName("Regresión §6 — el modelo no clasifica aclaraciones sueltas como otra intención con ≥ 0,60")
    void aclaracion_modeloNoLasReconoceComoIntencion() {
        for (String frase : List.of("dona marta", "doña marta", "fresa", "don pedro", "nicole hernandez")) {
            var r = clasificador.clasificar(frase);
            assertThat(r.intencion()).as("intención de '%s' (p=%s)", frase, r.probabilidadIntencion()).isNull();
        }
    }

    private static class HandlerDePrueba implements IntencionHandler {

        private final TipoIntencionVoz tipo;
        private final boolean requiereConfirmacion;
        boolean seEjecuto = false;
        boolean fallarAlEjecutar = false;
        boolean interpretarSinExito = false;
        Object payloadRecibidoEnEjecutar;
        /** Si no es null, interpretar() devuelve este payload (p. ej. una venta que espera aclaración). */
        Object payloadInterpretar;
        VozContexto ultimoContexto;

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
            ultimoContexto = ctx;
            String payload = tipo == TipoIntencionVoz.REGISTRAR_VENTA ? "payload-interno-venta" : null;
            return new VozResultado(true, "¿Confirmas? " + ctx.texto(), Map.of(),
                    payloadInterpretar != null ? payloadInterpretar : payload);
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
