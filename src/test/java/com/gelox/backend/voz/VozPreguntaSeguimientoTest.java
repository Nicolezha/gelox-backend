package com.gelox.backend.voz;

import com.gelox.backend.TestHelper;
import com.gelox.backend.entities.ComandoVoz;
import com.gelox.backend.entities.RolUsuario;
import com.gelox.backend.entities.TipoIntencionVoz;
import com.gelox.backend.entities.Usuario;
import com.gelox.backend.recomendacion.RecomendacionCompraService;
import com.gelox.backend.repositories.ComandoVozRepository;
import com.gelox.backend.voz.dto.VozInterpretarRequest;
import com.gelox.backend.voz.dto.VozInterpretarResponse;
import com.gelox.backend.voz.pln.ModeloIntencion;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Cuando el asistente pregunta algo, la siguiente frase es la respuesta: no hay que volver a
 * decir el comando completo.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class VozPreguntaSeguimientoTest {

    @Mock ComandoVozRepository comandoVozRepository;
    @Mock RecomendacionCompraService recomendacionService;

    static ModeloIntencion modelo;
    VozPendienteStore store;
    Usuario usuario;

    @BeforeAll
    static void cargarModelo() {
        modelo = new ModeloIntencion();
        modelo.cargar();
    }

    @BeforeEach
    void setUp() {
        store = new VozPendienteStore();
        usuario = TestHelper.buildUsuario("uid-1", "voz@gelox-test.com", RolUsuario.ADMINISTRADOR, true);
        when(comandoVozRepository.save(any())).thenAnswer(inv -> {
            ComandoVoz comando = inv.getArgument(0);
            comando.setId(UUID.randomUUID());
            return comando;
        });
    }

    private VozService servicio(IntencionHandler... handlers) {
        ClasificadorIntencion clasificador = new ClasificadorIntencion(modelo);
        VozService s = new VozService(List.of(handlers), clasificador, clasificador, comandoVozRepository, store,
                recomendacionService);
        s.indexarHandlers();
        return s;
    }

    /** Pregunta "¿cuál?" mientras no llegue la respuesta; con ella pide confirmar. */
    static class HandlerQuePregunta implements IntencionHandler {
        final TipoIntencionVoz tipo;
        final List<VozContexto> contextos = new ArrayList<>();

        HandlerQuePregunta(TipoIntencionVoz tipo) {
            this.tipo = tipo;
        }

        @Override public TipoIntencionVoz tipo() { return tipo; }
        @Override public boolean requiereConfirmacion() { return true; }

        @Override
        public VozResultado interpretar(VozContexto ctx) {
            contextos.add(ctx);
            if (!ctx.slots().containsKey(PreguntaVoz.SLOT_RESPUESTA)) {
                return new VozResultado(false, "¿Cuál de las 2?", Map.of(), new PreguntaVoz(Map.of("fecha", "2026-11-15")));
            }
            return new VozResultado(true, "Aprobar la " + ctx.slots().get(PreguntaVoz.SLOT_RESPUESTA) + ". ¿Confirmas?",
                    Map.of(), "payload");
        }

        @Override public VozResultado ejecutar(VozPendiente pendiente, Usuario usuario) { return null; }
    }

    @Test
    @DisplayName("La pregunta del handler deja esperaRespuesta y la siguiente frase llega como respuesta")
    void respuestaAPregunta() {
        HandlerQuePregunta decidir = new HandlerQuePregunta(TipoIntencionVoz.DECIDIR_RECOMENDACION);
        VozService voz = servicio(decidir);

        VozInterpretarResponse pregunta = voz.interpretar(
                new VozInterpretarRequest("aprueba la recomendación de compra del 15 de noviembre", 0.95), usuario);
        assertThat(pregunta.ok()).isFalse();
        assertThat(pregunta.intencion()).isEqualTo("DECIDIR_RECOMENDACION");
        assertThat(pregunta.datos()).containsEntry("esperaRespuesta", true);

        VozInterpretarResponse r = voz.interpretar(new VozInterpretarRequest("la segunda", 0.9), usuario);

        assertThat(r.ok()).isTrue();
        assertThat(r.requiereConfirmacion()).isTrue();
        VozContexto ctx = decidir.contextos.get(1);
        assertThat(ctx.texto()).isEqualTo("aprueba la recomendación de compra del 15 de noviembre");
        assertThat(ctx.slots()).containsEntry(PreguntaVoz.SLOT_RESPUESTA, "la segunda").containsEntry("fecha", "2026-11-15");
    }

    @Test
    @DisplayName("'cancelar' descarta la pregunta")
    void cancelarPregunta() {
        HandlerQuePregunta decidir = new HandlerQuePregunta(TipoIntencionVoz.DECIDIR_RECOMENDACION);
        VozService voz = servicio(decidir);
        voz.interpretar(new VozInterpretarRequest("aprueba la recomendación de ayer", 0.95), usuario);

        VozInterpretarResponse r = voz.interpretar(new VozInterpretarRequest("cancelar", 0.9), usuario);

        assertThat(r.textoRespuesta()).isEqualTo("Comando cancelado.");
        assertThat(store.takePregunta(usuario.getId())).isEmpty();
        assertThat(decidir.contextos).hasSize(1);
    }

    @Test
    @DisplayName("Un comando nuevo de otra intención no se toma como respuesta")
    void comandoNuevoNoEsRespuesta() {
        HandlerQuePregunta decidir = new HandlerQuePregunta(TipoIntencionVoz.DECIDIR_RECOMENDACION);
        HandlerQuePregunta general = new HandlerQuePregunta(TipoIntencionVoz.CONSULTAR_GENERAL);
        VozService voz = servicio(decidir, general);
        voz.interpretar(new VozInterpretarRequest("aprueba la recomendación de ayer", 0.95), usuario);

        VozInterpretarResponse r = voz.interpretar(new VozInterpretarRequest("cuántos clientes tengo", 0.95), usuario);

        assertThat(r.intencion()).isEqualTo("CONSULTAR_GENERAL");
        assertThat(decidir.contextos).hasSize(1);
    }

    @Test
    @DisplayName("Pregunta que reemplaza un fragmento: '¿Festival o Festival Mini?' → 'la segunda'")
    void reemplazarFragmento() {
        store.putPregunta(usuario.getId(), TipoIntencionVoz.GENERAR_PEDIDO, "genera un pedido con 5 cajas de festival",
                ((PreguntaVoz) PreguntaVoz.reemplazando("¿Festival o Festival Mini?", "festival",
                        List.of("Festival", "Festival Mini")).payload()).slots());
        VozPendienteStore.PreguntaPendiente pregunta = store.takePregunta(usuario.getId()).orElseThrow();

        assertThat(VozService.textoReinterpretado(pregunta, "la segunda"))
                .isEqualTo("genera 1 pedido con 5 cajas de festival mini");
        assertThat(VozService.textoReinterpretado(pregunta, "festival mini"))
                .isEqualTo("genera 1 pedido con 5 cajas de festival mini");
    }

    @Test
    @DisplayName("Sin LLM, las reglas reconocen recomendaciones y consultas generales")
    void reglasDeRespaldo() {
        ClasificadorIntencion c = new ClasificadorIntencion(modelo);

        var aprobar = c.clasificar("aprueba la recomendación de compra del 15 de noviembre");
        assertThat(aprobar.intencion()).isEqualTo(TipoIntencionVoz.DECIDIR_RECOMENDACION);
        assertThat(aprobar.slots()).containsEntry("accion", "APROBAR_TODO");

        var producto = c.clasificar("aprueba el mango biche de la recomendación de ayer");
        assertThat(producto.slots()).containsEntry("accion", "APROBAR").containsEntry("producto", "mango biche");

        assertThat(c.clasificar("cuántas recomendaciones se hicieron el 15 de noviembre").intencion())
                .isEqualTo(TipoIntencionVoz.CONSULTAR_GENERAL);
        assertThat(c.clasificar("cuántos clientes tengo").intencion()).isEqualTo(TipoIntencionVoz.CONSULTAR_GENERAL);
        assertThat(c.clasificar("qué me recomiendas pedir con dos millones").slots()).containsEntry("presupuesto", "2000000");
    }
}
