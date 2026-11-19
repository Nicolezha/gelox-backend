package com.gelox.backend.voz;

import com.gelox.backend.entities.ComandoVoz;
import com.gelox.backend.entities.EstadoComandoVoz;
import com.gelox.backend.entities.MetodoClasificacion;
import com.gelox.backend.entities.TipoIntencionVoz;
import com.gelox.backend.entities.Usuario;
import com.gelox.backend.recomendacion.RecomendacionCompraService;
import com.gelox.backend.repositories.ComandoVozRepository;
import com.gelox.backend.security.RequiereRol;
import com.gelox.backend.voz.dto.VozConfirmarRequest;
import com.gelox.backend.voz.handlers.NavegacionHandler;
import com.gelox.backend.voz.handlers.PendienteAclaracion;
import com.gelox.backend.voz.handlers.RecomendacionVozHandler;
import com.gelox.backend.voz.dto.VozConfirmarResponse;
import com.gelox.backend.voz.dto.VozInterpretarRequest;
import com.gelox.backend.voz.dto.VozInterpretarResponse;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Orquesta el flujo de voz: clasifica el texto, delega en el
 * {@link IntencionHandler} correspondiente y resuelve la confirmación.
 * <p>
 * Sin {@code @Transactional}: si {@code ejecutar} falla, igual se debe poder
 * guardar el comando con estado ERROR en lugar de perderlo por un rollback.
 */
@Service
@RequiredArgsConstructor
public class VozService {

    /** RNF-5 — por debajo de este umbral se pide confirmación aunque el handler no la exija. */
    private static final double UMBRAL_CONFIANZA = 0.70;

    private static final ZoneId ZONA_BOGOTA = ZoneId.of("America/Bogota");

    private static final String MENSAJE_NO_ENTENDIDO =
            "No entendí el comando. Intenta algo como: 'vende 2 cajas de Aloha Mango Biche', "
                    + "'cuántas Aloha Paleta Limón quedan' o 'cuánto ganamos hoy'.";

    /** "agrega ..." sin una venta esperando confirmación: no hay a qué sumarle. */
    private static final String MENSAJE_AGREGADO_SIN_VENTA =
            "No hay una venta en curso a la que agregar. Primero di la venta completa, por ejemplo "
                    + "'registra 2 cajas de Aloha Mango Biche', y luego 'agrega ...' antes de confirmar.";

    private static final String MENSAJE_SIN_HANDLER = "Esa función de voz todavía no está disponible.";

    private static final String MENSAJE_CANCELADO = "Comando cancelado.";

    /** Sobre texto normalizado: "añade" → "anade", "también" → "tambien". */
    private static final Pattern ES_AGREGADO = Pattern.compile("^(agrega|anade|tambien)\\b");

    /** Respuesta a una pregunta del asistente que la descarta. */
    private static final Pattern ES_CANCELAR = Pattern.compile(
            "(cancela\\w*|olvida\\w*|deja\\w*( asi)?|nada|no|ninguna|ninguno|no gracias|mejor no)");

    /** "1", "el 2", "al numero 3": respuesta que es solo un número. */
    private static final Pattern NUMERO_SUELTO = Pattern.compile("^(?:el |al |la |a la )?(?:numero )?(\\d{1,2})$");
    private static final Pattern PRIMERA_OPCION = Pattern.compile("^(?:el |la )?(primer|primero|primera|1)\\b");
    private static final Pattern SEGUNDA_OPCION = Pattern.compile("^(?:el |la )?(segundo|segunda|2|otro|otra|ultimo|ultima)\\b");
    private static final Pattern RELLENO_RESPUESTA = Pattern.compile("\\b(el|la|los|las|de|del|es|esa|ese|por favor)\\b");

    /** Arranque típico de una respuesta corta: "la de las 3", "el mango biche", "la segunda", "todos". */
    private static final Pattern PARECE_RESPUESTA = Pattern.compile(
            "^(?:si |no )?(?:la|el|los|las|lo|al|a la|de|del|con|por|esa|ese|esta|este|primer\\w*|segund\\w*|tercer\\w*"
                    + "|ultim\\w*|penultim\\w*|todos?|todas?|codigo|numero|opcion|\\d+)\\b");

    /** Arranque típico de un comando (texto normalizado), para no tomarlo como respuesta a una pregunta. */
    private static final Pattern INICIO_COMANDO = Pattern.compile(
            "^(?:oye |gelox |hola gelox )?(registra|vende|vendi|anota|aprueba|rechaza|cambia|modifica|genera|haz|hace"
                    + "|crea|cuant[oa]s?|que|cual(es)?|muestra(me)?|abre|ve a|ir a|llevame|dime|consulta|agrega|anade"
                    + "|quita|elimina|pide|recomienda|recomiendame|cierra|dirigete|vamos|anda|navega|llevame|pon|ponme|ensename)\\b");

    private final List<IntencionHandler> handlers;
    /** El LLM (ProcesadorLenguajeLlm, @Primary) con respaldo en el clasificador de la Iteración 1. */
    private final ProcesadorLenguaje procesador;
    private final ClasificadorIntencion clasificador;
    private final ComandoVozRepository comandoVozRepository;
    private final VozPendienteStore pendienteStore;
    private final RecomendacionCompraService recomendacionService;

    private Map<TipoIntencionVoz, IntencionHandler> handlersPorTipo;

    @PostConstruct
    void indexarHandlers() {
        handlersPorTipo = handlers.stream()
                .collect(Collectors.toMap(IntencionHandler::tipo, h -> h));
    }

    /**
     * Lo que hace falta recordar entre interpretar() y confirmar(): el payload es del handler, tal cual.
     * probabilidadIntencion y metodo son los de la interpretación original (FP-15 §2.1).
     */
    private record DatosPendientes(String texto, Double confianza, Object payloadHandler,
                                   Double probabilidadIntencion, MetodoClasificacion metodo) {}

    @RequiereRol({"ADMINISTRADOR", "ENCARGADO_VENTAS", "ENCARGADO_INVENTARIO"})
    public VozInterpretarResponse interpretar(VozInterpretarRequest request, Usuario usuario) {
        VozInterpretarResponse respuesta = interpretarComando(request, usuario);
        recordarRecomendacion(usuario, respuesta.datos());
        return respuesta;
    }

    private VozInterpretarResponse interpretarComando(VozInterpretarRequest request, Usuario usuario) {
        ClasificadorIntencion.ResultadoClasificacion resultado = procesador.procesar(request.texto());

        // El asistente hizo una pregunta ("¿cuál de las 3?", "¿qué producto?"): esta frase es la
        // respuesta, salvo que el usuario haya abierto el micrófono para un comando nuevo
        // (continuacion=false) o que la frase sea claramente otro comando.
        Optional<VozPendienteStore.PreguntaPendiente> pregunta = pendienteStore.takePregunta(usuario.getId());
        if (Boolean.FALSE.equals(request.continuacion())) pregunta = Optional.empty();
        if (pregunta.isPresent()) {
            String normalizado = NormalizadorVoz.normalizar(request.texto());
            if (ES_CANCELAR.matcher(normalizado).matches()) {
                ComandoVoz comando = guardarComando(usuario, request.texto(), request.confianza(),
                        pregunta.get().intencion(), EstadoComandoVoz.CANCELADO, MENSAJE_CANCELADO,
                        resultado.probabilidadIntencion(), resultado.metodo());
                return new VozInterpretarResponse(comando.getId(), true, pregunta.get().intencion().name(), false,
                        null, MENSAJE_CANCELADO, Map.of());
            }
            if (!esComandoNuevo(resultado, pregunta.get(), normalizado)) {
                return responderPregunta(pregunta.get(), request, usuario, resultado);
            }
        }

        Map<String, String> slots = resultado.slots();

        // "Muéstrame la recomendación completa (en pantalla)": abre la pantalla de esa recomendación.
        TipoIntencionVoz intencionMostrar = resultado.intencion();
        String normalizadoMostrar = NormalizadorVoz.normalizar(request.texto());
        if (intencionMostrar != TipoIntencionVoz.NAVEGACION && handlersPorTipo.containsKey(TipoIntencionVoz.NAVEGACION)
                && (NavegacionHandler.esMostrarRecomendacion(normalizadoMostrar)
                || NavegacionHandler.esEditarRecomendacion(normalizadoMostrar))) {
            resultado = new ClasificadorIntencion.ResultadoClasificacion(TipoIntencionVoz.NAVEGACION, slots,
                    resultado.probabilidadIntencion(), resultado.metodo());
        }

        // "Apruébala" / "aprueba esa": la recomendación de la que se venía hablando por voz.
        if (resultado.intencion() == TipoIntencionVoz.DECIDIR_RECOMENDACION
                && RecomendacionVozHandler.APROBAR_REFERIDA.matcher(NormalizadorVoz.normalizar(request.texto())).find()) {
            Optional<UUID> recordada = pendienteStore.ultimaRecomendacion(usuario.getId());
            if (recordada.isPresent()) {
                Map<String, String> conReferida = new LinkedHashMap<>(slots);
                conReferida.put(RecomendacionVozHandler.SLOT_REFERIDA, recordada.get().toString());
                slots = conReferida;
            }
        }

        // T41-BE5 — "agrega/añade/también ..." continúa la venta que espera confirmación.
        TipoIntencionVoz intencion = resultado.intencion();
        // PLN — un agregado sin la palabra "pedido" no lo reinterpreta el modelo (podría tomarlo como
        // MODIFICAR_PEDIDO o como una venta nueva): se decide con las reglas, como antes del modelo.
        String normalizadoAgregado = NormalizadorVoz.normalizar(request.texto());
        if ((resultado.metodo() == MetodoClasificacion.MODELO || resultado.metodo() == MetodoClasificacion.LLM)
                && ES_AGREGADO.matcher(normalizadoAgregado).find()
                && !normalizadoAgregado.contains("pedido")) {
            intencion = clasificador.intencionPorReglas(request.texto());
        }
        // T115-BE3 — "genera el pedido" sin productos: con el LLM lo resuelve la regla 4 del prompt;
        // con el clasificador de respaldo se redirige a la recomendación de hoy si ya tiene aprobados.
        if (intencion == TipoIntencionVoz.GENERAR_PEDIDO
                && resultado.metodo() != MetodoClasificacion.LLM
                && !slots.containsKey("cantidad")
                && handlersPorTipo.containsKey(TipoIntencionVoz.DECIDIR_RECOMENDACION)
                && hayAprobadosHoy()) {
            intencion = TipoIntencionVoz.DECIDIR_RECOMENDACION;
            slots = Map.of("accion", RecomendacionVozHandler.Accion.GENERAR_PEDIDO.name());
        }
        VozPendiente ventaEnCurso = null;
        boolean agregado = false;
        // Si la venta quedó esperando una aclaración (destinatario o producto), cualquier frase sin
        // intención propia ("doña Marta", "fresa") la completa.
        if (intencion == null || intencion == TipoIntencionVoz.REGISTRAR_VENTA) {
            agregado = ES_AGREGADO.matcher(NormalizadorVoz.normalizar(request.texto())).find();
            final boolean esAgregado = agregado;
            final TipoIntencionVoz intencionClasificada = intencion;
            ventaEnCurso = pendienteStore.peekPorUsuario(usuario.getId())
                    .filter(p -> p.intencion() == TipoIntencionVoz.REGISTRAR_VENTA)
                    .filter(p -> esperaDestinatario(p) ? intencionClasificada == null : esAgregado)
                    .orElse(null);
            if (ventaEnCurso != null) intencion = TipoIntencionVoz.REGISTRAR_VENTA;
        }

        if (intencion == null) {
            String mensaje = agregado ? MENSAJE_AGREGADO_SIN_VENTA : MENSAJE_NO_ENTENDIDO;
            ComandoVoz comando = guardarComando(usuario, request.texto(), request.confianza(),
                    null, EstadoComandoVoz.ERROR, mensaje,
                    resultado.probabilidadIntencion(), resultado.metodo());
            return new VozInterpretarResponse(comando.getId(), false, null, false, null, mensaje, Map.of());
        }

        IntencionHandler handler = handlersPorTipo.get(intencion);
        if (handler == null) {
            ComandoVoz comando = guardarComando(usuario, request.texto(), request.confianza(),
                    intencion, EstadoComandoVoz.ERROR, MENSAJE_SIN_HANDLER,
                    resultado.probabilidadIntencion(), resultado.metodo());
            return new VozInterpretarResponse(
                    comando.getId(), false, intencion.name(), false, null, MENSAJE_SIN_HANDLER, Map.of());
        }

        LocalDate hoy = LocalDate.now(ZONA_BOGOTA);
        DatosPendientes datosPrevios = ventaEnCurso == null ? null : (DatosPendientes) ventaEnCurso.payload();
        VozContexto ctx;
        if (ventaEnCurso != null) {
            VozPendiente paraHandler = new VozPendiente(
                    ventaEnCurso.comandoId(), ventaEnCurso.usuarioId(), intencion,
                    datosPrevios.payloadHandler(), ventaEnCurso.expiraEn());
            ctx = new VozContexto(request.texto(), slots, request.confianza(), usuario, hoy, paraHandler);
        } else {
            ctx = new VozContexto(request.texto(), slots, request.confianza(), usuario, hoy);
        }
        VozResultado interpretado = handler.interpretar(ctx);

        if (!interpretado.ok() && interpretado.payload() instanceof PreguntaVoz preguntaVoz) {
            return preguntar(usuario, request, intencion, request.texto(), preguntaVoz, interpretado, resultado);
        }
        if (!interpretado.ok()) {
            ComandoVoz comando = guardarComando(usuario, request.texto(), request.confianza(),
                    intencion, EstadoComandoVoz.ERROR, interpretado.textoRespuesta(),
                    resultado.probabilidadIntencion(), resultado.metodo());
            return new VozInterpretarResponse(comando.getId(), false, intencion.name(), false, null,
                    interpretado.textoRespuesta(), interpretado.datos());
        }

        boolean necesitaConfirmacion = handler.requiereConfirmacion() || request.confianza() < UMBRAL_CONFIANZA;

        if (necesitaConfirmacion) {
            UUID comandoId;
            DatosPendientes datos;
            if (ventaEnCurso != null) {
                // Mismo id: put sobrescribe la entrada y reinicia el plazo.
                comandoId = ventaEnCurso.comandoId();
                datos = new DatosPendientes(datosPrevios.texto() + "; " + request.texto(),
                        Math.min(datosPrevios.confianza(), request.confianza()), interpretado.payload(),
                        datosPrevios.probabilidadIntencion(), datosPrevios.metodo());
            } else {
                comandoId = UUID.randomUUID();
                datos = new DatosPendientes(request.texto(), request.confianza(), interpretado.payload(),
                        resultado.probabilidadIntencion(), resultado.metodo());
            }
            pendienteStore.put(comandoId, usuario.getId(), intencion, datos);

            if (!handler.requiereConfirmacion()) {
                // Consulta pendiente solo por baja confianza (RNF-5): se confirma lo entendido
                // y la respuesta llega con la confirmación, no antes.
                return new VozInterpretarResponse(comandoId, true, intencion.name(), true,
                        VozPendienteStore.ttlSegundos(intencion), "Entendí: \"" + request.texto() + "\". ¿Confirmas?", Map.of());
            }
            return new VozInterpretarResponse(comandoId, true, intencion.name(), true,
                    VozPendienteStore.ttlSegundos(intencion), interpretado.textoRespuesta(), interpretado.datos());
        }

        ComandoVoz comando = guardarComando(usuario, request.texto(), request.confianza(),
                intencion, EstadoComandoVoz.PROCESADO, interpretado.textoRespuesta(),
                    resultado.probabilidadIntencion(), resultado.metodo());
        return new VozInterpretarResponse(comando.getId(), true, intencion.name(), false, null,
                interpretado.textoRespuesta(), interpretado.datos());
    }

    /**
     * La frase contesta la pregunta pendiente: se vuelve a llamar al mismo handler con la frase
     * original, los slots que ya tenía y lo respondido en {@link PreguntaVoz#SLOT_RESPUESTA}.
     */
    private VozInterpretarResponse responderPregunta(VozPendienteStore.PreguntaPendiente pregunta,
                                                     VozInterpretarRequest request, Usuario usuario,
                                                     ClasificadorIntencion.ResultadoClasificacion resultado) {
        if (pregunta.slots().containsKey(PreguntaVoz.SLOT_REEMPLAZAR) || pregunta.slots().containsKey(PreguntaVoz.SLOT_ANEXAR)) {
            // La respuesta completa el comando original, que se interpreta de nuevo desde cero.
            return interpretar(new VozInterpretarRequest(textoReinterpretado(pregunta, request.texto()),
                    request.confianza()), usuario);
        }
        TipoIntencionVoz intencion = pregunta.intencion();
        IntencionHandler handler = handlersPorTipo.get(intencion);
        Map<String, String> slots = new LinkedHashMap<>(pregunta.slots());
        slots.put(PreguntaVoz.SLOT_RESPUESTA, request.texto());
        String textoComando = pregunta.textoOriginal() + " → " + request.texto();

        VozContexto ctx = new VozContexto(pregunta.textoOriginal(), slots, request.confianza(), usuario,
                LocalDate.now(ZONA_BOGOTA));
        VozResultado interpretado = handler.interpretar(ctx);

        if (!interpretado.ok() && interpretado.payload() instanceof PreguntaVoz otra) {
            return preguntar(usuario, request, intencion, pregunta.textoOriginal(), otra, interpretado, resultado);
        }
        if (!interpretado.ok()) {
            ComandoVoz comando = guardarComando(usuario, textoComando, request.confianza(),
                    intencion, EstadoComandoVoz.ERROR, interpretado.textoRespuesta(),
                    resultado.probabilidadIntencion(), resultado.metodo());
            return new VozInterpretarResponse(comando.getId(), false, intencion.name(), false, null,
                    interpretado.textoRespuesta(), interpretado.datos());
        }
        if (handler.requiereConfirmacion()) {
            UUID comandoId = UUID.randomUUID();
            pendienteStore.put(comandoId, usuario.getId(), intencion, new DatosPendientes(textoComando,
                    request.confianza(), interpretado.payload(), resultado.probabilidadIntencion(), resultado.metodo()));
            return new VozInterpretarResponse(comandoId, true, intencion.name(), true,
                    VozPendienteStore.ttlSegundos(intencion), interpretado.textoRespuesta(), interpretado.datos());
        }
        ComandoVoz comando = guardarComando(usuario, textoComando, request.confianza(),
                intencion, EstadoComandoVoz.PROCESADO, interpretado.textoRespuesta(),
                resultado.probabilidadIntencion(), resultado.metodo());
        return new VozInterpretarResponse(comando.getId(), true, intencion.name(), false, null,
                interpretado.textoRespuesta(), interpretado.datos());
    }

    /**
     * El comando original completado con la respuesta: reemplaza el fragmento por el que se
     * preguntó (o por la opción elegida con "la primera" / "la segunda"), o se agrega al final.
     */
    static String textoReinterpretado(VozPendienteStore.PreguntaPendiente pregunta, String respuesta) {
        Map<String, String> s = pregunta.slots();
        String original = NormalizadorVoz.normalizar(pregunta.textoOriginal());
        String dicho = NormalizadorVoz.normalizar(respuesta);
        if (s.containsKey(PreguntaVoz.SLOT_ANEXAR)) {
            String prefijo = s.get(PreguntaVoz.SLOT_ANEXAR);
            Matcher numero = NUMERO_SUELTO.matcher(dicho);
            String anexo = numero.matches() && !prefijo.isEmpty() ? prefijo + numero.group(1) : dicho;
            return original + " " + anexo;
        }
        String elegido = opcionElegida(s.get(PreguntaVoz.SLOT_OPCIONES), dicho);
        String fragmento = s.getOrDefault(PreguntaVoz.SLOT_REEMPLAZAR, "");
        if (!fragmento.isBlank() && original.contains(fragmento)) {
            return original.replaceFirst(Pattern.quote(fragmento), Matcher.quoteReplacement(elegido));
        }
        return original + " " + elegido;
    }

    /** "la primera" / "la segunda" / "la de limón" → la opción; si no, lo dicho sin artículos. */
    private static String opcionElegida(String opcionesSlot, String dicho) {
        String limpio = RELLENO_RESPUESTA.matcher(dicho).replaceAll(" ").replaceAll("\\s+", " ").trim();
        if (opcionesSlot == null || opcionesSlot.isBlank()) return limpio.isEmpty() ? dicho : limpio;
        List<String> opciones = java.util.Arrays.stream(opcionesSlot.split("\\|")).map(NormalizadorVoz::normalizar).toList();
        if (PRIMERA_OPCION.matcher(dicho).find()) return opciones.get(0);
        if (SEGUNDA_OPCION.matcher(dicho).find() && opciones.size() > 1) return opciones.get(1);
        if (!limpio.isEmpty()) {
            for (String opcion : opciones) {
                if (opcion.contains(limpio)) return opcion;
            }
        }
        return limpio.isEmpty() ? dicho : limpio;
    }

    /**
     * El handler preguntó algo: se recuerda la pregunta y el frontend vuelve a abrir el micrófono
     * ({@code datos.esperaRespuesta}) para que el usuario conteste ahí mismo.
     */
    private VozInterpretarResponse preguntar(Usuario usuario, VozInterpretarRequest request, TipoIntencionVoz intencion,
                                             String textoOriginal, PreguntaVoz pregunta, VozResultado interpretado,
                                             ClasificadorIntencion.ResultadoClasificacion resultado) {
        pendienteStore.putPregunta(usuario.getId(), intencion, textoOriginal, pregunta.slots());
        Map<String, Object> datos = new LinkedHashMap<>(interpretado.datos());
        datos.put("esperaRespuesta", true);
        ComandoVoz comando = guardarComando(usuario, request.texto(), request.confianza(), intencion,
                EstadoComandoVoz.PROCESADO, interpretado.textoRespuesta(),
                resultado.probabilidadIntencion(), resultado.metodo());
        return new VozInterpretarResponse(comando.getId(), false, intencion.name(), false, null,
                interpretado.textoRespuesta(), datos);
    }

    /**
     * Con una pregunta pendiente, la frase se toma como comando nuevo solo si arranca como un
     * comando ("cuántos clientes tengo"); "la de las 3", "el mango biche" o "la última" son respuestas.
     */
    private static boolean esComandoNuevo(ClasificadorIntencion.ResultadoClasificacion resultado,
                                          VozPendienteStore.PreguntaPendiente pregunta, String normalizado) {
        if (resultado.intencion() == null) return false;
        if (normalizado.split(" ").length < 3 || PARECE_RESPUESTA.matcher(normalizado).find()) return false;
        // Otra intención que no arranca como respuesta ("dirígete a la pantalla de catálogo"): comando nuevo.
        if (resultado.intencion() != pregunta.intencion()) return true;
        if (!INICIO_COMANDO.matcher(normalizado).find()) return false;
        // Repetir el comando completo de la misma intención ("vende 2 cajas de Aloha Limón") lo reemplaza;
        // en las preguntas que se responden por slot ("aprueba la de las 3") sigue siendo la respuesta.
        boolean reinterpreta = pregunta.slots().containsKey(PreguntaVoz.SLOT_REEMPLAZAR)
                || pregunta.slots().containsKey(PreguntaVoz.SLOT_ANEXAR);
        return reinterpreta;
    }

    /** La última recomendación de la que se habló por voz, para "muéstramela completa". */
    private void recordarRecomendacion(Usuario usuario, Map<String, Object> datos) {
        if (datos != null && datos.get("recomendacionId") instanceof UUID id) {
            pendienteStore.recordarRecomendacion(usuario.getId(), id);
        }
    }

    private boolean hayAprobadosHoy() {
        return recomendacionService.recomendacionDelDia()
                .map(r -> !RecomendacionVozHandler.aprobados(r).isEmpty())
                .orElse(false);
    }

    private static boolean esperaDestinatario(VozPendiente pendiente) {
        return pendiente.payload() instanceof DatosPendientes datos
                && datos.payloadHandler() instanceof PendienteAclaracion;
    }

    @RequiereRol({"ADMINISTRADOR", "ENCARGADO_VENTAS", "ENCARGADO_INVENTARIO"})
    public VozConfirmarResponse confirmar(VozConfirmarRequest request, Usuario usuario) {
        VozPendiente pendiente = pendienteStore.take(request.comandoId(), usuario.getId());
        DatosPendientes datos = (DatosPendientes) pendiente.payload();

        if (!request.confirmar()) {
            guardarComando(usuario, datos.texto(), datos.confianza(), pendiente.intencion(),
                    EstadoComandoVoz.CANCELADO, MENSAJE_CANCELADO,
                    datos.probabilidadIntencion(), datos.metodo());
            return new VozConfirmarResponse(EstadoComandoVoz.CANCELADO.name(), MENSAJE_CANCELADO, Map.of());
        }

        IntencionHandler handler = handlersPorTipo.get(pendiente.intencion());
        VozPendiente paraHandler = new VozPendiente(
                pendiente.comandoId(), pendiente.usuarioId(), pendiente.intencion(),
                datos.payloadHandler(), pendiente.expiraEn());

        try {
            VozResultado resultado = handler.ejecutar(paraHandler, usuario);
            guardarComando(usuario, datos.texto(), datos.confianza(), pendiente.intencion(),
                    EstadoComandoVoz.PROCESADO, resultado.textoRespuesta(),
                    datos.probabilidadIntencion(), datos.metodo());
            recordarRecomendacion(usuario, resultado.datos());
            return new VozConfirmarResponse(EstadoComandoVoz.PROCESADO.name(), resultado.textoRespuesta(), resultado.datos());
        } catch (RuntimeException e) {
            guardarComando(usuario, datos.texto(), datos.confianza(), pendiente.intencion(),
                    EstadoComandoVoz.ERROR, e.getMessage(),
                    datos.probabilidadIntencion(), datos.metodo());
            throw e;
        }
    }

    private ComandoVoz guardarComando(Usuario usuario, String texto, double confianza, TipoIntencionVoz intencion,
                                       EstadoComandoVoz estado, String respuesta,
                                       Double probabilidadIntencion, MetodoClasificacion metodo) {
        ComandoVoz comando = ComandoVoz.builder()
                .usuario(usuario)
                .textoTranscrito(texto)
                .confianza(BigDecimal.valueOf(confianza).setScale(3, RoundingMode.HALF_UP))
                .intencion(intencion)
                .estado(estado)
                .respuesta(respuesta)
                .probabilidadIntencion(probabilidadIntencion == null ? null
                        : BigDecimal.valueOf(probabilidadIntencion).setScale(3, RoundingMode.HALF_UP))
                .metodoClasificacion(metodo)
                .build();
        return comandoVozRepository.save(comando);
    }
}
