package com.gelox.backend.voz.handlers;

import com.gelox.backend.entities.AjustePresupuesto;
import com.gelox.backend.entities.DecisionItem;
import com.gelox.backend.entities.EstadoRecomendacion;
import com.gelox.backend.entities.TipoIntencionVoz;
import com.gelox.backend.entities.Usuario;
import com.gelox.backend.recomendacion.DecisionRecomendacionService;
import com.gelox.backend.recomendacion.RecomendacionCompraService;
import com.gelox.backend.recomendacion.dto.DecidirItemRequest;
import com.gelox.backend.recomendacion.dto.ItemDTO;
import com.gelox.backend.recomendacion.dto.PedidoGeneradoDTO;
import com.gelox.backend.recomendacion.dto.RecomendacionDTO;
import com.gelox.backend.repositories.RecomendacionCompraRepository;
import com.gelox.backend.security.RequiereRol;
import com.gelox.backend.tenant.TenantContext;
import com.gelox.backend.voz.FechaVoz;
import com.gelox.backend.voz.IntencionHandler;
import com.gelox.backend.voz.NormalizadorVoz;
import com.gelox.backend.voz.PreguntaVoz;
import com.gelox.backend.voz.ResolvedorProducto;
import com.gelox.backend.voz.VozContexto;
import com.gelox.backend.voz.VozPendiente;
import com.gelox.backend.voz.VozResultado;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * RF55/RF56 — DECIDIR_RECOMENDACION por voz (FP-15 §2.11.5): aprobar, rechazar o cambiar las
 * cajas de un producto, aprobar todo y generar el pedido. Toda decisión pide confirmación
 * (15 s, ver VozPendienteStore). El producto se busca solo entre los ítems de la recomendación
 * de hoy, nunca en todo el catálogo.
 */
@Component
@RequiredArgsConstructor
public class RecomendacionVozHandler implements IntencionHandler {

    private static final ZoneId ZONA_BOGOTA = ZoneId.of("America/Bogota");

    static final String SIN_RECOMENDACION = "No hay una recomendación de hoy. Dime con cuánto presupuesto la genero.";
    static final String YA_CONVERTIDA = "La recomendación ya se convirtió en pedido; no se puede modificar.";
    static final String NO_ESTA = "Ese producto no está en la recomendación de hoy.";
    static final String SIN_APROBADOS = "Aprueba al menos un producto antes de generar el pedido.";
    static final String PEDIDO_REGISTRADO = "Pedido registrado. Ya puedes descargar el archivo.";

    public enum Accion { APROBAR, RECHAZAR, MODIFICAR, APROBAR_TODO, GENERAR_PEDIDO }

    private static final List<EstadoRecomendacion> ABIERTAS =
            List.of(EstadoRecomendacion.GENERADA, EstadoRecomendacion.EN_REVISION);

    /** "aprueba la recomendación", "aprueba toda la recomendación de compra del lunes". */
    private static final Pattern APROBAR_RECOMENDACION =
            Pattern.compile("\\baprueb\\w*\\s+(?:me\\s+)?(?:toda\\s+)?(?:la|esa|esta)\\s+(?:\\w+\\s+)?recomendacion");
    /** "apruébala", "sí, aprueba esa", "apruébala toda": la recomendación de la que se viene hablando. */
    public static final Pattern APROBAR_REFERIDA = Pattern.compile(
            "\\b(apruebal[ao]s?|aprobarla|aprueba(?:me)? (?:esa|eso|esta|la misma|todo eso|esa recomendacion|esta recomendacion))\\b");
    /** Slot con la recomendación de la que se venía hablando por voz (lo pone VozService). */
    public static final String SLOT_REFERIDA = "recomendacionReferida";

    /** "la última recomendación", "la más reciente", "la que acabo de hacer". */
    private static final Pattern ULTIMA_RECOMENDACION = Pattern.compile(
            "\\b(ultima|mas reciente|acabo de (hacer|generar|crear))\\b");
    /** Verbos del comando, para buscar en el resto "la primera", "la de las 3"… */
    private static final Pattern VERBOS_COMANDO = Pattern.compile(
            "\\b(aprueb\\w*|aprobar|rechaz\\w*|cambi\\w*|modific\\w*|genera\\w*|recomendacion\\w*|de compra|compra)\\b");
    private static final Pattern TODOS = Pattern.compile("\\b(todo|todos|todas|toda|completa|completo)\\b");
    private static final Pattern NUMERO = Pattern.compile("\\b(\\d+)\\b");
    private static final Pattern RELLENO_RESPUESTA = Pattern.compile(
            "\\b(el|la|los|las|de|del|producto|aprueba\\w*|rechaza\\w*|cambia\\w*|quiero|ese|esa|este|esta|por favor|cajas?|\\d+)\\b");

    /** Lo que se ejecuta al confirmar. itemId y cajas solo en las decisiones por producto. */
    public record Payload(UUID recomendacionId, Accion accion, UUID itemId, Integer cajas) {}

    private final RecomendacionCompraService recomendacionService;
    private final DecisionRecomendacionService decisionService;
    private final RecomendacionCompraRepository recomendacionRepo;
    private final ResolvedorProducto resolvedorProducto;

    @Override
    public TipoIntencionVoz tipo() {
        return TipoIntencionVoz.DECIDIR_RECOMENDACION;
    }

    @Override
    public boolean requiereConfirmacion() {
        return true;
    }

    @Override
    @RequiereRol({"ADMINISTRADOR", "ENCARGADO_INVENTARIO"})
    public VozResultado interpretar(VozContexto ctx) {
        Map<String, String> slots = new LinkedHashMap<>(ctx.slots());
        // Lo que el usuario respondió a la última pregunta; lo consume la primera pieza que falte.
        String respuesta = slots.remove(PreguntaVoz.SLOT_RESPUESTA);
        LocalDate hoy = ctx.hoy() != null ? ctx.hoy() : LocalDate.now(ZONA_BOGOTA);
        String normalizado = NormalizadorVoz.normalizar(ctx.texto());

        // Primero lo dicho (determinista); el slot del LLM cubre formas como "hace tres días".
        Optional<LocalDate> fechaDicha = FechaVoz.de(normalizado, hoy);
        // "aprueba la última recomendación" sin decir el día: la más reciente que se hizo, de cualquier día.
        boolean laUltima = fechaDicha.isEmpty() && !slots.containsKey(SelectorRecomendacion.SLOT_ELEGIDA)
                && ULTIMA_RECOMENDACION.matcher(normalizado).find();
        LocalDate fecha = fechaDicha
                .or(() -> laUltima ? Optional.empty() : FechaVoz.deSlot(slots.get("fecha")))
                .orElse(hoy);

        Accion accion = accion(slots.get("accion"));
        // "aprueba la recomendación (de compra) del 15 de noviembre": toda la recomendación.
        boolean referida = APROBAR_REFERIDA.matcher(normalizado).find();
        if ((accion == Accion.APROBAR || accion == null) && esBlanco(slots.get("producto"))
                && (APROBAR_RECOMENDACION.matcher(normalizado).find() || referida)) {
            accion = Accion.APROBAR_TODO;
        }
        if (accion == null) {
            return error("No entendí qué hacer con la recomendación. Di por ejemplo 'aprueba el Mango Biche' o 'genera el pedido'.");
        }
        slots.put("accion", accion.name());

        List<RecomendacionDTO> abiertas;
        Optional<RecomendacionDTO> laReferida = fechaDicha.isEmpty() && referida
                ? buscarReferida(slots.get(SLOT_REFERIDA)) : Optional.empty();
        if (laReferida.isPresent()) {
            abiertas = List.of(laReferida.get());
            if (laReferida.get().createdAt() != null) fecha = laReferida.get().createdAt().toLocalDate();
        } else if (laUltima) {
            Optional<RecomendacionDTO> ultima = recomendacionService.ultimaRecomendacion(ABIERTAS);
            if (ultima.isEmpty()) return error("No hay recomendaciones pendientes por aprobar.");
            abiertas = List.of(ultima.get());
            if (ultima.get().createdAt() != null) fecha = ultima.get().createdAt().toLocalDate();
        } else {
            abiertas = abiertas(fecha, hoy);
        }
        slots.put("fecha", fecha.toString());
        if (abiertas.isEmpty()) {
            boolean convertida = yaHayPedido(fecha);
            if (fecha.equals(hoy)) return error(convertida ? YA_CONVERTIDA : SIN_RECOMENDACION);
            return error(convertida
                    ? "La recomendación " + SelectorRecomendacion.deFecha(fecha, hoy) + " ya se convirtió en pedido; no se puede modificar."
                    : "No hay recomendaciones pendientes " + SelectorRecomendacion.deFecha(fecha, hoy) + ".");
        }

        RecomendacionDTO recomendacion;
        Optional<RecomendacionDTO> elegida =
                SelectorRecomendacion.porId(abiertas, slots.get(SelectorRecomendacion.SLOT_ELEGIDA));
        if (elegida.isPresent()) {
            recomendacion = elegida.get();
        } else if (abiertas.size() == 1) {
            recomendacion = abiertas.get(0);
        } else {
            List<RecomendacionDTO> opciones =
                    SelectorRecomendacion.opciones(abiertas, slots.get(SelectorRecomendacion.SLOT_OPCIONES));
            boolean filtradas = opciones.size() != abiertas.size();
            String verbo = verbo(accion);
            // El comando mismo puede decir cuál: "aprueba la primera recomendación del 15", "la de las 3".
            String criterio = respuesta;
            if (criterio == null) {
                SelectorRecomendacion.Resultado enComando = SelectorRecomendacion.elegir(sinVerbos(normalizado), opciones);
                if (!enComando.conCriterio() || enComando.coincidencias().isEmpty()) {
                    return SelectorRecomendacion.preguntarCual(null, opciones, fecha, hoy, verbo, slots, filtradas);
                }
                criterio = sinVerbos(normalizado);
            }
            SelectorRecomendacion.Resultado seleccion = SelectorRecomendacion.elegir(criterio, opciones);
            respuesta = null; // ya se usó para elegir la recomendación
            if (seleccion.coincidencias().size() == 1) {
                recomendacion = seleccion.coincidencias().get(0);
            } else if (!seleccion.conCriterio() || seleccion.coincidencias().isEmpty()) {
                return SelectorRecomendacion.preguntarCual("No identifiqué cuál. ", opciones, fecha, hoy, verbo, slots, filtradas);
            } else {
                return SelectorRecomendacion.preguntarCual(null, seleccion.coincidencias(), fecha, hoy, verbo, slots, true);
            }
        }
        slots.put(SelectorRecomendacion.SLOT_ELEGIDA, recomendacion.id().toString());
        slots.remove(SelectorRecomendacion.SLOT_OPCIONES);

        // Se aclara de cuál se habla cuando no es la única de hoy.
        String referencia = fecha.equals(hoy) && abiertas.size() == 1 && !laUltima && laReferida.isEmpty() ? ""
                : " de la recomendación " + SelectorRecomendacion.deFecha(fecha, hoy)
                + (abiertas.size() > 1 || laUltima ? " de las " + SelectorRecomendacion.hora(recomendacion.createdAt()) : "");

        return switch (accion) {
            case APROBAR_TODO -> aprobarTodo(recomendacion, referencia);
            case GENERAR_PEDIDO -> generarPedido(recomendacion, referencia);
            default -> decidirProducto(recomendacion, accion, slots, respuesta, referencia);
        };
    }

    @Override
    @RequiereRol({"ADMINISTRADOR", "ENCARGADO_INVENTARIO"})
    public VozResultado ejecutar(VozPendiente pendiente, Usuario usuario) {
        Payload p = (Payload) pendiente.payload();
        return switch (p.accion()) {
            case APROBAR -> {
                decisionService.decidir(p.recomendacionId(), p.itemId(), new DecidirItemRequest(DecisionItem.APROBADO, null, null));
                yield listo("Listo, aprobado.");
            }
            case RECHAZAR -> {
                decisionService.decidir(p.recomendacionId(), p.itemId(),
                        new DecidirItemRequest(DecisionItem.RECHAZADO, null, "Rechazado por voz"));
                yield listo("Listo, rechazado.");
            }
            case MODIFICAR -> {
                // 0 cajas se registra como rechazo (ItemRecomendacion.modificar).
                decisionService.decidir(p.recomendacionId(), p.itemId(),
                        new DecidirItemRequest(DecisionItem.MODIFICADO, p.cajas(), null));
                yield listo(p.cajas() == 0 ? "Listo, rechazado." : "Listo, modificado.");
            }
            case APROBAR_TODO -> {
                decisionService.aprobarTodo(p.recomendacionId());
                yield listo("Listo, aprobados.");
            }
            case GENERAR_PEDIDO -> {
                PedidoGeneradoDTO pedido = decisionService.generarPedido(p.recomendacionId(), usuario);
                // RF63 — un pedido por proveedor: se dice cuántos y las advertencias de monto mínimo
                List<PedidoGeneradoDTO.PedidoDeProveedorDTO> pedidos =
                        pedido.pedidos() == null ? List.of() : pedido.pedidos();
                StringBuilder texto = new StringBuilder(pedidos.size() > 1
                        ? "Se registraron " + pedidos.size() + " pedidos, uno por proveedor. Ya puedes descargar los archivos."
                        : PEDIDO_REGISTRADO);
                pedidos.stream()
                        .map(PedidoGeneradoDTO.PedidoDeProveedorDTO::advertenciaMontoMinimo)
                        .filter(java.util.Objects::nonNull)
                        .forEach(a -> texto.append(" Ojo: ").append(a));
                yield new VozResultado(true, texto.toString(),
                        Map.of("pedidoId", pedido.pedidoId(), "exportUrl", pedido.exportUrl(), "pedidos", pedidos), null);
            }
        };
    }

    // ══════════════════════════════════════════════════════════════════════
    // Interpretación por acción
    // ══════════════════════════════════════════════════════════════════════

    private VozResultado decidirProducto(RecomendacionDTO recomendacion, Accion accion, Map<String, String> slots,
                                         String respuesta, String referencia) {
        String fragmento = slots.get("producto");
        if (esBlanco(fragmento)) {
            if (respuesta == null) {
                String pregunta = accion == Accion.APROBAR
                        ? "¿Qué producto" + referencia + " apruebo? Di el nombre o 'todos' para aprobarla completa."
                        : "¿Qué producto" + referencia + " quieres " + (accion == Accion.RECHAZAR ? "rechazar" : "cambiar") + "?";
                return pregunta(pregunta, slots);
            }
            if (accion == Accion.APROBAR && TODOS.matcher(NormalizadorVoz.normalizar(respuesta)).find()) {
                return aprobarTodo(recomendacion, referencia);
            }
            fragmento = limpiarProducto(respuesta);
            respuesta = null; // ya se usó como el producto
            if (fragmento.isEmpty()) return pregunta("¿Qué producto?", slots);
        }
        Optional<ItemDTO> encontrado = buscarItem(recomendacion, fragmento);
        if (encontrado.isEmpty()) {
            Map<String, String> sinProducto = new LinkedHashMap<>(slots);
            sinProducto.remove("producto");
            return pregunta(NO_ESTA + " ¿Qué producto?", sinProducto);
        }
        ItemDTO item = encontrado.get();
        String nombre = item.productoNombre();
        Map<String, String> conProducto = new LinkedHashMap<>(slots);
        conProducto.put("producto", fragmento);

        return switch (accion) {
            case APROBAR -> {
                if (!tieneCajasSugeridas(item)) {
                    conProducto.put("accion", Accion.MODIFICAR.name());
                    yield pregunta(nombre + " no cupo en el presupuesto. ¿A cuántas cajas lo cambio?", conProducto);
                }
                yield confirmar("Aprobar " + nombre + referencia + ", " + cajas(item.cajasSugeridas()) + " por "
                                + pesos(item.costoEstimado()) + ". ¿Confirmas?",
                        new Payload(recomendacion.id(), accion, item.id(), null), item);
            }
            case RECHAZAR -> confirmar("Rechazar " + nombre + referencia + ". ¿Confirmas?",
                    new Payload(recomendacion.id(), accion, item.id(), null), item);
            default -> {
                Integer nuevas = entero(slots.get("cantidad"));
                if (nuevas == null && respuesta != null) nuevas = primerNumero(respuesta);
                if (nuevas == null || nuevas < 0) {
                    yield pregunta("¿A cuántas cajas cambio " + nombre + "?", conProducto);
                }
                int actuales = item.cajasAprobadas() != null ? item.cajasAprobadas()
                        : item.cajasSugeridas() != null ? item.cajasSugeridas() : 0;
                yield confirmar("Cambiar " + nombre + referencia + " de " + actuales + " a " + cajas(nuevas) + ". ¿Confirmas?",
                        new Payload(recomendacion.id(), accion, item.id(), nuevas), item);
            }
        };
    }

    private VozResultado aprobarTodo(RecomendacionDTO recomendacion, String referencia) {
        List<ItemDTO> pendientes = recomendacion.items().stream()
                .filter(i -> i.decision() == DecisionItem.PENDIENTE && tieneCajasSugeridas(i))
                .toList();
        if (pendientes.isEmpty()) return error("No quedan productos pendientes por aprobar" + referencia + ".");

        BigDecimal costo = pendientes.stream().map(ItemDTO::costoEstimado)
                .map(c -> c == null ? BigDecimal.ZERO : c)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new VozResultado(true,
                "Aprobar " + productos(pendientes.size(), "los ") + referencia + " por " + pesos(costo) + ". ¿Confirmas?",
                Map.of("recomendacionId", recomendacion.id()),
                new Payload(recomendacion.id(), Accion.APROBAR_TODO, null, null));
    }

    private VozResultado generarPedido(RecomendacionDTO recomendacion, String referencia) {
        List<ItemDTO> aprobados = aprobados(recomendacion);
        if (aprobados.isEmpty()) return error(SIN_APROBADOS);
        return new VozResultado(true,
                "Generar el pedido" + referencia + " con " + productos(aprobados.size(), "") + " aprobado"
                        + (aprobados.size() == 1 ? "" : "s") + " por " + pesos(recomendacion.costoAprobado()) + ". ¿Confirmas?",
                Map.of("recomendacionId", recomendacion.id()),
                new Payload(recomendacion.id(), Accion.GENERAR_PEDIDO, null, null));
    }

    // ══════════════════════════════════════════════════════════════════════
    // Auxiliares
    // ══════════════════════════════════════════════════════════════════════

    /** Ítems aprobados o modificados con cajas: los que irían al pedido (también lo usa VozService). */
    public static List<ItemDTO> aprobados(RecomendacionDTO recomendacion) {
        return recomendacion.items().stream()
                .filter(i -> (i.decision() == DecisionItem.APROBADO || i.decision() == DecisionItem.MODIFICADO)
                        && i.cajasAprobadas() != null && i.cajasAprobadas() > 0)
                .toList();
    }

    /** El resolvedor busca en el catálogo; aquí solo cuentan los productos de la recomendación. */
    private Optional<ItemDTO> buscarItem(RecomendacionDTO recomendacion, String fragmento) {
        Map<UUID, ItemDTO> porProducto = recomendacion.items().stream()
                .collect(Collectors.toMap(ItemDTO::productoId, i -> i, (a, b) -> a));
        Set<UUID> ids = porProducto.keySet();
        return resolvedorProducto.resolver(fragmento).stream()
                .filter(c -> ids.contains(c.id()))
                .findFirst()
                .map(c -> porProducto.get(c.id()));
    }

    /**
     * Recomendaciones abiertas (GENERADA o EN_REVISION) de la fecha, de la más antigua a la más nueva.
     * Para hoy, si la consulta por día no trae nada, se usa la del día (misma regla que la pantalla).
     */
    private List<RecomendacionDTO> abiertas(LocalDate fecha, LocalDate hoy) {
        List<RecomendacionDTO> delDia = recomendacionService.recomendacionesDelDia(fecha, ABIERTAS);
        if (delDia.isEmpty() && fecha.equals(hoy)) {
            return recomendacionService.recomendacionDelDia().map(List::of).orElse(List.of());
        }
        return delDia;
    }

    private boolean yaHayPedido(LocalDate fecha) {
        return recomendacionRepo.buscarUltima(TenantContext.requerido(), List.of(EstadoRecomendacion.PEDIDO_GENERADO),
                fecha.atStartOfDay(), fecha.plusDays(1).atStartOfDay()).isPresent();
    }

    private static String verbo(Accion accion) {
        return switch (accion) {
            case APROBAR, APROBAR_TODO -> "aprobar";
            case RECHAZAR -> "usar para rechazar el producto";
            case MODIFICAR -> "modificar";
            case GENERAR_PEDIDO -> "convertir en pedido";
        };
    }

    /** "el mango biche", "apruébame el de mango biche" → "mango biche". */
    private static String limpiarProducto(String respuesta) {
        return RELLENO_RESPUESTA.matcher(NormalizadorVoz.normalizar(respuesta)).replaceAll(" ")
                .replaceAll("[^a-z0-9 ]", " ").replaceAll("\\s+", " ").trim();
    }

    private static Integer primerNumero(String respuesta) {
        Matcher m = NUMERO.matcher(NormalizadorVoz.normalizar(respuesta));
        return m.find() ? Integer.parseInt(m.group(1)) : null;
    }

    /** La recomendación recordada, si sigue abierta (GENERADA o EN_REVISION). */
    private Optional<RecomendacionDTO> buscarReferida(String id) {
        if (esBlanco(id)) return Optional.empty();
        try {
            RecomendacionDTO r = decisionService.obtener(UUID.fromString(id));
            return r != null && ABIERTAS.contains(r.estado()) ? Optional.of(r) : Optional.empty();
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    private static String sinVerbos(String normalizado) {
        return VERBOS_COMANDO.matcher(normalizado).replaceAll(" ").replaceAll("\\s+", " ").trim();
    }

    private static boolean esBlanco(String s) {
        return s == null || s.isBlank();
    }

    private static VozResultado pregunta(String texto, Map<String, String> slots) {
        return new VozResultado(false, texto, Map.of(), new PreguntaVoz(slots));
    }

    private static Accion accion(String slot) {
        if (slot == null) return null;
        try {
            return Accion.valueOf(slot.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static Integer entero(String slot) {
        if (slot == null) return null;
        try {
            return (int) Double.parseDouble(slot.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static boolean tieneCajasSugeridas(ItemDTO item) {
        return item.ajustePresupuesto() != AjustePresupuesto.EXCLUIDO
                && item.cajasSugeridas() != null && item.cajasSugeridas() > 0;
    }

    private static VozResultado confirmar(String texto, Payload payload, ItemDTO item) {
        return new VozResultado(true, texto,
                Map.of("recomendacionId", payload.recomendacionId(), "itemId", item.id()), payload);
    }

    private static VozResultado error(String texto) {
        return new VozResultado(false, texto, Map.of(), null);
    }

    private static VozResultado listo(String texto) {
        return new VozResultado(true, texto, Map.of(), null);
    }

    static String cajas(int n) {
        return n + (n == 1 ? " caja" : " cajas");
    }

    private static String productos(int n, String articulo) {
        return n == 1 ? "1 producto" : articulo + n + " productos";
    }

    /** "$118.720", como se lee en voz alta. */
    static String pesos(BigDecimal valor) {
        return "$" + NumberFormat.getIntegerInstance(Locale.of("es", "CO"))
                .format(valor == null ? BigDecimal.ZERO : valor);
    }
}
