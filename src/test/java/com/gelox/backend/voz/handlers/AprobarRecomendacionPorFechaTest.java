package com.gelox.backend.voz.handlers;

import com.gelox.backend.TestHelper;
import com.gelox.backend.entities.AjustePresupuesto;
import com.gelox.backend.entities.DecisionItem;
import com.gelox.backend.entities.EstadoRecomendacion;
import com.gelox.backend.entities.OrigenRecomendacion;
import com.gelox.backend.entities.RolUsuario;
import com.gelox.backend.entities.UrgenciaRecomendacion;
import com.gelox.backend.entities.Usuario;
import com.gelox.backend.recomendacion.DecisionRecomendacionService;
import com.gelox.backend.recomendacion.RecomendacionCompraService;
import com.gelox.backend.recomendacion.dto.ItemDTO;
import com.gelox.backend.recomendacion.dto.RecomendacionDTO;
import com.gelox.backend.repositories.RecomendacionCompraRepository;
import com.gelox.backend.tenant.TenantContext;
import com.gelox.backend.voz.FechaVoz;
import com.gelox.backend.voz.PreguntaVoz;
import com.gelox.backend.voz.ResolvedorProducto;
import com.gelox.backend.voz.VozContexto;
import com.gelox.backend.voz.VozResultado;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * "Aprueba la recomendación de compra del 15 de noviembre": usa la recomendación de ese día
 * (no la última que se hizo) y, si ese día hubo varias, pregunta cuál; la respuesta puede ser
 * la hora, el código, el precio o el orden.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AprobarRecomendacionPorFechaTest {

    private static final UUID TENANT = UUID.randomUUID();
    private static final LocalDate HOY = LocalDate.of(2026, 11, 18);
    private static final LocalDate DIA_15 = LocalDate.of(2026, 11, 15);

    @Mock RecomendacionCompraService recomendacionService;
    @Mock DecisionRecomendacionService decisionService;
    @Mock RecomendacionCompraRepository recomendacionRepo;
    @Mock ResolvedorProducto resolvedor;

    RecomendacionVozHandler handler;
    Usuario usuario;

    final RecomendacionDTO manana = rec("aaaa1111", LocalDateTime.of(2026, 11, 15, 9, 5), "118720");
    final RecomendacionDTO tarde = rec("bbbb2222", LocalDateTime.of(2026, 11, 15, 15, 40), "250000");
    final RecomendacionDTO noche = rec("cccc3333", LocalDateTime.of(2026, 11, 15, 19, 10), "250000");
    final RecomendacionDTO deHoy = rec("dddd4444", LocalDateTime.of(2026, 11, 18, 8, 0), "90000");

    @BeforeEach
    void setUp() {
        TenantContext.set(TENANT);
        handler = new RecomendacionVozHandler(recomendacionService, decisionService, recomendacionRepo, resolvedor);
        usuario = TestHelper.buildUsuario("uid-1", "admin@gelox-test.com", RolUsuario.ADMINISTRADOR, true);
        when(recomendacionService.recomendacionesDelDia(eq(HOY), any())).thenReturn(List.of(deHoy));
        when(recomendacionRepo.buscarUltima(any(), any(), any(), any())).thenReturn(java.util.Optional.empty());
    }

    @AfterEach
    void limpiar() {
        TenantContext.clear();
    }

    private static RecomendacionDTO rec(String prefijo, LocalDateTime creada, String costo) {
        UUID id = UUID.fromString(prefijo + "-0000-0000-0000-000000000000");
        ItemDTO item = new ItemDTO(UUID.randomUUID(), UUID.randomUUID(), "Aloha Mango Biche", 1, UrgenciaRecomendacion.ALERTA,
                null, null, null, null, null, 0, null, 24, 1, AjustePresupuesto.COMPLETO, new BigDecimal(costo), null, null,
                DecisionItem.PENDIENTE, null, null, null);
        return new RecomendacionDTO(id, EstadoRecomendacion.GENERADA, OrigenRecomendacion.PANTALLA,
                new BigDecimal("2000000"), new BigDecimal(costo), BigDecimal.ZERO, false, 90, 30, null,
                creada, creada, List.of(item));
    }

    private VozResultado decir(String texto, Map<String, String> slots) {
        return handler.interpretar(new VozContexto(texto, slots, 0.95, usuario, HOY));
    }

    /** Simula lo que hace VozService al responder: mismos slots de la pregunta + la respuesta. */
    private VozResultado responder(String textoOriginal, VozResultado pregunta, String respuesta) {
        assertThat(pregunta.payload()).isInstanceOf(PreguntaVoz.class);
        Map<String, String> slots = new HashMap<>(((PreguntaVoz) pregunta.payload()).slots());
        slots.put(PreguntaVoz.SLOT_RESPUESTA, respuesta);
        return decir(textoOriginal, slots);
    }

    private static UUID recomendacionDe(VozResultado r) {
        return ((RecomendacionVozHandler.Payload) r.payload()).recomendacionId();
    }

    private static final String FRASE = "aprueba la recomendación de compra del 15 de noviembre";

    @Test
    @DisplayName("Con una sola recomendación ese día, aprueba esa y no la de hoy")
    void unaSola_usaLaDelDia() {
        when(recomendacionService.recomendacionesDelDia(eq(DIA_15), any())).thenReturn(List.of(manana));

        VozResultado r = decir(FRASE, Map.of("accion", "APROBAR"));

        assertThat(r.ok()).isTrue();
        assertThat(recomendacionDe(r)).isEqualTo(manana.id());
        assertThat(((RecomendacionVozHandler.Payload) r.payload()).accion()).isEqualTo(RecomendacionVozHandler.Accion.APROBAR_TODO);
        assertThat(r.textoRespuesta()).contains("del 15 de noviembre");
    }

    @Test
    @DisplayName("Con varias ese día pregunta cuál, con hora, código y precio de cada una")
    void varias_preguntaCual() {
        when(recomendacionService.recomendacionesDelDia(eq(DIA_15), any())).thenReturn(List.of(manana, tarde, noche));

        VozResultado r = decir(FRASE, Map.of("accion", "APROBAR_TODO"));

        assertThat(r.ok()).isFalse();
        assertThat(r.payload()).isInstanceOf(PreguntaVoz.class);
        assertThat(r.textoRespuesta())
                .startsWith("Hay 3 recomendaciones pendientes del 15 de noviembre")
                .contains("la primera, de las 9:05 a. m., por $118.720")
                .contains("y la tercera, de las 7:10 p. m.")
                .endsWith("¿Cuál quieres aprobar?")
                .doesNotContain("código");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> opciones = (List<Map<String, Object>>) r.datos().get("opciones");
        assertThat(opciones).hasSize(3);
        assertThat(opciones.get(0)).containsEntry("codigo", "AAAA1111").containsEntry("hora", "9:05 a. m.")
                .containsEntry("ordinal", "Primera").containsEntry("respuesta", "la primera");
        assertThat(r.datos()).containsEntry("titulo", "Recomendaciones pendientes del 15 de noviembre")
                .containsEntry("pregunta", "¿Cuál quieres aprobar?");
        assertThat(r.datos()).containsKey("opciones");
    }

    @Test
    @DisplayName("Responde por orden: 'la segunda', 'la última', 'la primera'")
    void respondePorOrden() {
        when(recomendacionService.recomendacionesDelDia(eq(DIA_15), any())).thenReturn(List.of(manana, tarde, noche));
        VozResultado pregunta = decir(FRASE, Map.of("accion", "APROBAR_TODO"));

        assertThat(recomendacionDe(responder(FRASE, pregunta, "la segunda"))).isEqualTo(tarde.id());
        assertThat(recomendacionDe(responder(FRASE, pregunta, "la última"))).isEqualTo(noche.id());
        assertThat(recomendacionDe(responder(FRASE, pregunta, "la primera"))).isEqualTo(manana.id());
        assertThat(recomendacionDe(responder(FRASE, pregunta, "la 2"))).isEqualTo(tarde.id());
    }

    @Test
    @DisplayName("Responde por hora, código o precio")
    void respondePorHoraCodigoOPrecio() {
        when(recomendacionService.recomendacionesDelDia(eq(DIA_15), any())).thenReturn(List.of(manana, tarde, noche));
        VozResultado pregunta = decir(FRASE, Map.of("accion", "APROBAR_TODO"));

        assertThat(recomendacionDe(responder(FRASE, pregunta, "la de las tres y cuarenta"))).isEqualTo(tarde.id());
        assertThat(recomendacionDe(responder(FRASE, pregunta, "la de las 9 de la mañana"))).isEqualTo(manana.id());
        assertThat(recomendacionDe(responder(FRASE, pregunta, "código CCCC3333"))).isEqualTo(noche.id());
        assertThat(recomendacionDe(responder(FRASE, pregunta, "la de ciento dieciocho mil setecientos veinte"))).isEqualTo(manana.id());
    }

    @Test
    @DisplayName("Mismo precio en dos: vuelve a preguntar solo entre esas y luego acepta hora u orden")
    void mismoPrecio_preguntaDeNuevoEntreLasQueCoinciden() {
        when(recomendacionService.recomendacionesDelDia(eq(DIA_15), any())).thenReturn(List.of(manana, tarde, noche));
        VozResultado pregunta = decir(FRASE, Map.of("accion", "APROBAR_TODO"));

        VozResultado otra = responder(FRASE, pregunta, "la de 250 mil");
        assertThat(otra.ok()).isFalse();
        assertThat(otra.textoRespuesta()).startsWith("Coinciden 2 recomendaciones")
                .contains("3:40 p. m.").contains("7:10 p. m.").doesNotContain("9:05");

        assertThat(recomendacionDe(responder(FRASE, otra, "la última"))).isEqualTo(noche.id());
        assertThat(recomendacionDe(responder(FRASE, otra, "la primera"))).isEqualTo(tarde.id());
        assertThat(recomendacionDe(responder(FRASE, otra, "la de las 3"))).isEqualTo(tarde.id());
    }

    @Test
    @DisplayName("Respuesta que no identifica ninguna: lo dice y vuelve a preguntar")
    void respuestaSinCoincidencia_vuelveAPreguntar() {
        when(recomendacionService.recomendacionesDelDia(eq(DIA_15), any())).thenReturn(List.of(manana, tarde, noche));
        VozResultado pregunta = decir(FRASE, Map.of("accion", "APROBAR_TODO"));

        VozResultado r = responder(FRASE, pregunta, "la de las 11");

        assertThat(r.ok()).isFalse();
        assertThat(r.textoRespuesta()).startsWith("No identifiqué cuál.");
        assertThat(r.payload()).isInstanceOf(PreguntaVoz.class);
    }

    @Test
    @DisplayName("Sin recomendaciones ese día: lo dice con la fecha")
    void sinRecomendacionesEseDia() {
        when(recomendacionService.recomendacionesDelDia(eq(DIA_15), any())).thenReturn(List.of());

        VozResultado r = decir(FRASE, Map.of("accion", "APROBAR_TODO"));

        assertThat(r.ok()).isFalse();
        assertThat(r.textoRespuesta()).isEqualTo("No hay recomendaciones pendientes del 15 de noviembre.");
    }

    @Test
    @DisplayName("'Aprueba el producto' sin decir cuál: pregunta y la respuesta 'todos' aprueba la completa")
    void preguntaProducto_respuestaTodos() {
        VozResultado pregunta = decir("aprueba un producto", Map.of("accion", "APROBAR"));
        assertThat(pregunta.ok()).isFalse();
        assertThat(pregunta.textoRespuesta()).startsWith("¿Qué producto");

        VozResultado r = responder("aprueba un producto", pregunta, "todos");
        assertThat(r.ok()).isTrue();
        assertThat(((RecomendacionVozHandler.Payload) r.payload()).accion()).isEqualTo(RecomendacionVozHandler.Accion.APROBAR_TODO);
        assertThat(recomendacionDe(r)).isEqualTo(deHoy.id());
    }

    @Test
    @DisplayName("'Aprueba la última recomendación' usa la más reciente que se hizo, no lista las de hoy")
    void laUltima_esLaMasReciente() {
        when(recomendacionService.recomendacionesDelDia(eq(HOY), any())).thenReturn(List.of(deHoy, manana));
        when(recomendacionService.ultimaRecomendacion(any())).thenReturn(java.util.Optional.of(noche));

        VozResultado r = decir("aprueba la última recomendación de compra", Map.of("accion", "APROBAR"));

        assertThat(r.ok()).isTrue();
        assertThat(recomendacionDe(r)).isEqualTo(noche.id());
        assertThat(r.textoRespuesta()).contains("del 15 de noviembre de las 7:10 p. m.");
    }

    @Test
    @DisplayName("El comando ya dice cuál ('la primera del 15 de noviembre'): no pregunta")
    void criterioEnElComando() {
        when(recomendacionService.recomendacionesDelDia(eq(DIA_15), any())).thenReturn(List.of(manana, tarde, noche));

        VozResultado r = decir("aprueba la primera recomendación del 15 de noviembre", Map.of("accion", "APROBAR_TODO"));

        assertThat(r.ok()).isTrue();
        assertThat(recomendacionDe(r)).isEqualTo(manana.id());
    }

    @Test
    @DisplayName("'Sí, apruébala' aprueba la recomendación de la que se venía hablando, aunque sea de otro día")
    void apruebalaUsaLaReferida() {
        when(decisionService.obtener(tarde.id())).thenReturn(tarde);

        VozResultado r = decir("si apruebala", Map.of(RecomendacionVozHandler.SLOT_REFERIDA, tarde.id().toString()));

        assertThat(r.ok()).isTrue();
        assertThat(recomendacionDe(r)).isEqualTo(tarde.id());
        assertThat(((RecomendacionVozHandler.Payload) r.payload()).accion()).isEqualTo(RecomendacionVozHandler.Accion.APROBAR_TODO);
        assertThat(r.textoRespuesta()).contains("del 15 de noviembre");
    }

    @Test
    @DisplayName("'Déjame modificar' abre la recomendación; 'cambia el mango biche a 3 cajas' sigue siendo decisión")
    void editarRecomendacion() {
        assertThat(NavegacionHandler.esEditarRecomendacion("dejame modificar")).isTrue();
        assertThat(NavegacionHandler.esEditarRecomendacion("no mejor dejame modificarla")).isTrue();
        assertThat(NavegacionHandler.esEditarRecomendacion("quiero editar la recomendacion")).isTrue();
        assertThat(NavegacionHandler.esEditarRecomendacion("modificala")).isTrue();
        assertThat(NavegacionHandler.esEditarRecomendacion("cambia el mango biche a 3 cajas")).isFalse();
        assertThat(NavegacionHandler.esEditarRecomendacion("quiero cambiar el mango biche a 3 cajas")).isFalse();
    }

    @Test
    @DisplayName("FechaVoz — fechas dichas de varias formas, nunca en el futuro")
    void fechaVoz() {
        assertThat(FechaVoz.de("15 de noviembre", HOY)).contains(DIA_15);
        assertThat(FechaVoz.de("del 15/11", HOY)).contains(DIA_15);
        assertThat(FechaVoz.de("de ayer", HOY)).contains(HOY.minusDays(1));
        assertThat(FechaVoz.de("de antier", HOY)).contains(HOY.minusDays(2));
        assertThat(FechaVoz.de("del lunes", HOY)).contains(LocalDate.of(2026, 11, 16));
        assertThat(FechaVoz.de("del 20 de diciembre", HOY)).contains(LocalDate.of(2025, 12, 20));
        assertThat(FechaVoz.de("del primero de noviembre", HOY)).contains(LocalDate.of(2026, 11, 1));
        assertThat(FechaVoz.de("aprueba el mango biche", HOY)).isEmpty();
        assertThat(FechaVoz.describir(DIA_15, HOY)).isEqualTo("el 15 de noviembre");
    }
}
