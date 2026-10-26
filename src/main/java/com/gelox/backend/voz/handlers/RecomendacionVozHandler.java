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
import com.gelox.backend.voz.IntencionHandler;
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
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
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
        Optional<RecomendacionDTO> delDia = recomendacionService.recomendacionDelDia();
        if (delDia.isEmpty()) {
            return error(hoyYaHayPedido() ? YA_CONVERTIDA : SIN_RECOMENDACION);
        }
        RecomendacionDTO recomendacion = delDia.get();

        Accion accion = accion(ctx.slots().get("accion"));
        if (accion == null) {
            return error("No entendí qué hacer con la recomendación. Di por ejemplo 'aprueba el Mango Biche' o 'genera el pedido'.");
        }

        return switch (accion) {
            case APROBAR_TODO -> aprobarTodo(recomendacion);
            case GENERAR_PEDIDO -> generarPedido(recomendacion);
            default -> decidirProducto(recomendacion, accion, ctx.slots());
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
                yield new VozResultado(true, PEDIDO_REGISTRADO,
                        Map.of("pedidoId", pedido.pedidoId(), "exportUrl", pedido.exportUrl()), null);
            }
        };
    }

    // ══════════════════════════════════════════════════════════════════════
    // Interpretación por acción
    // ══════════════════════════════════════════════════════════════════════

    private VozResultado decidirProducto(RecomendacionDTO recomendacion, Accion accion, Map<String, String> slots) {
        String fragmento = slots.get("producto");
        if (fragmento == null || fragmento.isBlank()) {
            return error("¿Qué producto de la recomendación?");
        }
        Optional<ItemDTO> encontrado = buscarItem(recomendacion, fragmento);
        if (encontrado.isEmpty()) return error(NO_ESTA);
        ItemDTO item = encontrado.get();
        String nombre = item.productoNombre();

        return switch (accion) {
            case APROBAR -> {
                if (!tieneCajasSugeridas(item)) {
                    yield error(nombre + " no cupo en el presupuesto. Di a cuántas cajas lo cambio.");
                }
                yield confirmar("Aprobar " + nombre + ", " + cajas(item.cajasSugeridas()) + " por "
                                + pesos(item.costoEstimado()) + ". ¿Confirmas?",
                        new Payload(recomendacion.id(), accion, item.id(), null), item);
            }
            case RECHAZAR -> confirmar("Rechazar " + nombre + ". ¿Confirmas?",
                    new Payload(recomendacion.id(), accion, item.id(), null), item);
            default -> {
                Integer nuevas = entero(slots.get("cantidad"));
                if (nuevas == null || nuevas < 0) {
                    yield error("¿A cuántas cajas cambio " + nombre + "?");
                }
                int actuales = item.cajasAprobadas() != null ? item.cajasAprobadas()
                        : item.cajasSugeridas() != null ? item.cajasSugeridas() : 0;
                yield confirmar("Cambiar " + nombre + " de " + actuales + " a " + cajas(nuevas) + ". ¿Confirmas?",
                        new Payload(recomendacion.id(), accion, item.id(), nuevas), item);
            }
        };
    }

    private VozResultado aprobarTodo(RecomendacionDTO recomendacion) {
        List<ItemDTO> pendientes = recomendacion.items().stream()
                .filter(i -> i.decision() == DecisionItem.PENDIENTE && tieneCajasSugeridas(i))
                .toList();
        if (pendientes.isEmpty()) return error("No quedan productos pendientes por aprobar.");

        BigDecimal costo = pendientes.stream().map(ItemDTO::costoEstimado)
                .map(c -> c == null ? BigDecimal.ZERO : c)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new VozResultado(true,
                "Aprobar " + productos(pendientes.size(), "los ") + " por " + pesos(costo) + ". ¿Confirmas?",
                Map.of("recomendacionId", recomendacion.id()),
                new Payload(recomendacion.id(), Accion.APROBAR_TODO, null, null));
    }

    private VozResultado generarPedido(RecomendacionDTO recomendacion) {
        List<ItemDTO> aprobados = aprobados(recomendacion);
        if (aprobados.isEmpty()) return error(SIN_APROBADOS);
        return new VozResultado(true,
                "Generar el pedido con " + productos(aprobados.size(), "") + " aprobado" + (aprobados.size() == 1 ? "" : "s")
                        + " por " + pesos(recomendacion.costoAprobado()) + ". ¿Confirmas?",
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

    private boolean hoyYaHayPedido() {
        LocalDate hoy = LocalDate.now(ZONA_BOGOTA);
        return recomendacionRepo.buscarUltima(TenantContext.requerido(), List.of(EstadoRecomendacion.PEDIDO_GENERADO),
                hoy.atStartOfDay(), hoy.plusDays(1).atStartOfDay()).isPresent();
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
