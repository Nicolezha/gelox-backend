package com.gelox.backend.voz.handlers;

import com.gelox.backend.entities.Comerciante;
import com.gelox.backend.entities.EstadoPedido;
import com.gelox.backend.entities.EstadoRecomendacion;
import com.gelox.backend.entities.PedidoProveedor;
import com.gelox.backend.entities.TipoIntencionVoz;
import com.gelox.backend.entities.Usuario;
import com.gelox.backend.recomendacion.RecomendacionCompraService;
import com.gelox.backend.recomendacion.dto.RecomendacionDTO;
import com.gelox.backend.repositories.ComercianteRepository;
import com.gelox.backend.repositories.PedidoProveedorRepository;
import com.gelox.backend.repositories.ProductoRepository;
import com.gelox.backend.repositories.UsuarioRepository;
import com.gelox.backend.security.RequiereRol;
import com.gelox.backend.tenant.TenantContext;
import com.gelox.backend.ventas.rural.ClienteRuralRepository;
import com.gelox.backend.voz.FechaVoz;
import com.gelox.backend.voz.IntencionHandler;
import com.gelox.backend.voz.NormalizadorVoz;
import com.gelox.backend.voz.PreguntaVoz;
import com.gelox.backend.voz.VozContexto;
import com.gelox.backend.voz.VozPendiente;
import com.gelox.backend.voz.VozResultado;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * CONSULTAR_GENERAL por voz, sin confirmación: preguntas sobre los datos de la app.
 * "¿cuántas recomendaciones se hicieron el 15 de noviembre?", "¿cuántos clientes tengo?",
 * "¿cuántos productos hay?", "¿cuántos pedidos pendientes hay?", "¿cuántos usuarios hay?".
 * Todo se consulta solo dentro de la distribuidora del usuario (filtro de tenant).
 */
@Component
@RequiredArgsConstructor
public class ConsultaGeneralHandler implements IntencionHandler {

    private static final ZoneId ZONA_BOGOTA = ZoneId.of("America/Bogota");
    private static final int MAX_DETALLE = 5;

    public enum Tema { RECOMENDACIONES, CLIENTES, COMERCIANTES, PRODUCTOS, PEDIDOS, USUARIOS }

    static final String PREGUNTA_TEMA = "¿Qué quieres saber? Puedo decirte cuántas recomendaciones, clientes, "
            + "comerciantes, productos, pedidos o usuarios hay.";

    private static final Pattern RECOMENDACIONES = Pattern.compile("\\brecomendacion\\w*|\\brecomend\\w*");
    private static final Pattern COMERCIANTES = Pattern.compile("\\b(comerciantes?|vendedor\\w*|ambulantes?)\\b");
    private static final Pattern CLIENTES = Pattern.compile("\\bclientes?\\b");
    private static final Pattern PRODUCTOS = Pattern.compile("\\b(productos?|referencias?|helados?|catalogo)\\b");
    private static final Pattern PEDIDOS = Pattern.compile("\\bpedidos?\\b");
    private static final Pattern USUARIOS = Pattern.compile("\\b(usuarios?|empleados?|encargados?|trabajadores?)\\b");
    private static final Pattern SEMANA = Pattern.compile("\\b(esta semana|la semana|semana)\\b");
    private static final Pattern MES = Pattern.compile("\\b(este mes|el mes|mes)\\b");
    private static final Pattern PENDIENTES = Pattern.compile("\\bpendientes?\\b");

    private final RecomendacionCompraService recomendacionService;
    private final ClienteRuralRepository clienteRepo;
    private final ComercianteRepository comercianteRepo;
    private final ProductoRepository productoRepo;
    private final PedidoProveedorRepository pedidoRepo;
    private final UsuarioRepository usuarioRepo;

    @Override
    public TipoIntencionVoz tipo() {
        return TipoIntencionVoz.CONSULTAR_GENERAL;
    }

    @Override
    public boolean requiereConfirmacion() {
        return false;
    }

    @Override
    @RequiereRol({"ADMINISTRADOR", "ENCARGADO_INVENTARIO", "ENCARGADO_VENTAS"})
    public VozResultado interpretar(VozContexto ctx) {
        Map<String, String> slots = new LinkedHashMap<>(ctx.slots());
        String respuesta = slots.remove(PreguntaVoz.SLOT_RESPUESTA);
        String normalizado = NormalizadorVoz.normalizar(ctx.texto()
                + (respuesta == null ? "" : " " + respuesta));
        LocalDate hoy = ctx.hoy() != null ? ctx.hoy() : LocalDate.now(ZONA_BOGOTA);

        Tema tema = tema(slots.get("tema")).or(() -> temaPorTexto(normalizado)).orElse(null);
        if (tema == null) {
            return new VozResultado(false, PREGUNTA_TEMA, Map.of(), new PreguntaVoz(slots));
        }

        Optional<LocalDate> fecha = FechaVoz.de(normalizado, hoy).or(() -> FechaVoz.deSlot(slots.get("fecha")));
        String periodo = slots.getOrDefault("periodo", "");
        LocalDate desde;
        LocalDate hasta;
        String cuando;
        if (fecha.isPresent()) {
            desde = hasta = fecha.get();
            cuando = FechaVoz.describir(fecha.get(), hoy);
        } else if ("SEMANA".equalsIgnoreCase(periodo) || SEMANA.matcher(normalizado).find()) {
            desde = hoy.minusDays(6);
            hasta = hoy;
            cuando = "en los últimos 7 días";
        } else if ("MES".equalsIgnoreCase(periodo) || MES.matcher(normalizado).find()) {
            desde = hoy.withDayOfMonth(1);
            hasta = hoy;
            cuando = "este mes";
        } else if ("AYER".equalsIgnoreCase(periodo)) {
            desde = hasta = hoy.minusDays(1);
            cuando = "ayer";
        } else {
            desde = hasta = null;
            cuando = null;
        }

        return switch (tema) {
            case RECOMENDACIONES -> recomendaciones(desde != null ? desde : hoy, hasta != null ? hasta : hoy,
                    cuando != null ? cuando : "hoy", hoy);
            case CLIENTES -> clientes();
            case COMERCIANTES -> comerciantes();
            case PRODUCTOS -> productos();
            case PEDIDOS -> pedidos(desde, hasta, cuando, PENDIENTES.matcher(normalizado).find());
            case USUARIOS -> usuarios();
        };
    }

    /** Sin confirmación de por medio solo llega aquí tras confirmar por baja confianza. */
    @Override
    @RequiereRol({"ADMINISTRADOR", "ENCARGADO_INVENTARIO", "ENCARGADO_VENTAS"})
    public VozResultado ejecutar(VozPendiente pendiente, Usuario usuario) {
        return (VozResultado) pendiente.payload();
    }

    // ══════════════════════════════════════════════════════════════════════
    // Respuestas por tema
    // ══════════════════════════════════════════════════════════════════════

    private VozResultado recomendaciones(LocalDate desde, LocalDate hasta, String cuando, LocalDate hoy) {
        List<RecomendacionDTO> lista = recomendacionService.recomendacionesEntre(desde, hasta,
                List.of(EstadoRecomendacion.values()));
        String sujeto = capitalizar(cuando);
        if (lista.isEmpty()) {
            return listo(sujeto + " no se hizo ninguna recomendación de compra.", Map.of("total", 0));
        }
        long pendientes = lista.stream().filter(r -> r.estado() == EstadoRecomendacion.GENERADA
                || r.estado() == EstadoRecomendacion.EN_REVISION).count();
        long convertidas = lista.stream().filter(r -> r.estado() == EstadoRecomendacion.PEDIDO_GENERADO).count();
        long descartadas = lista.stream().filter(r -> r.estado() == EstadoRecomendacion.DESCARTADA).count();

        StringBuilder sb = new StringBuilder(sujeto).append(lista.size() == 1 ? " se hizo 1 recomendación"
                : " se hicieron " + lista.size() + " recomendaciones");
        List<String> partes = new ArrayList<>();
        if (pendientes > 0) partes.add(pendientes + (pendientes == 1 ? " pendiente" : " pendientes"));
        if (convertidas > 0) partes.add(convertidas + (convertidas == 1 ? " convertida en pedido" : " convertidas en pedido"));
        if (descartadas > 0) partes.add(descartadas + (descartadas == 1 ? " descartada" : " descartadas"));
        if (!partes.isEmpty()) sb.append(": ").append(unir(partes));
        sb.append('.');

        if (lista.size() <= MAX_DETALLE) {
            List<String> detalle = new ArrayList<>();
            boolean variosDias = !desde.equals(hasta);
            for (RecomendacionDTO r : lista) {
                String momento = (variosDias && r.createdAt() != null
                        ? FechaVoz.describir(r.createdAt().toLocalDate(), hoy) + " a las " : "a las ")
                        + SelectorRecomendacion.hora(r.createdAt());
                detalle.add(momento + ", código " + SelectorRecomendacion.codigo(r.id()) + ", por "
                        + RecomendacionVozHandler.pesos(r.costoTotal()) + " (" + estado(r.estado()) + ")");
            }
            sb.append(' ').append(capitalizar(String.join("; ", detalle))).append('.');
        }

        Map<String, Object> datos = new LinkedHashMap<>();
        datos.put("total", lista.size());
        datos.put("pendientes", pendientes);
        datos.put("convertidas", convertidas);
        datos.put("descartadas", descartadas);
        return listo(sb.toString(), datos);
    }

    private VozResultado clientes() {
        long rurales = clienteRepo.count();
        List<Comerciante> comerciantes = comercianteRepo.findAll();
        long activos = comerciantes.stream().filter(c -> Boolean.TRUE.equals(c.getActivo())).count();
        String texto = "Tienes " + cantidad(rurales, "cliente rural", "clientes rurales") + " registrados y "
                + cantidad(comerciantes.size(), "comerciante", "comerciantes")
                + (comerciantes.isEmpty() ? "" : " (" + activos + (activos == 1 ? " activo" : " activos") + ")") + ".";
        return listo(texto, Map.of("clientesRurales", rurales, "comerciantes", comerciantes.size(),
                "comerciantesActivos", activos));
    }

    private VozResultado comerciantes() {
        List<Comerciante> comerciantes = comercianteRepo.findAll();
        long activos = comerciantes.stream().filter(c -> Boolean.TRUE.equals(c.getActivo())).count();
        String texto = "Tienes " + cantidad(comerciantes.size(), "comerciante", "comerciantes") + ": "
                + activos + (activos == 1 ? " activo" : " activos") + " y "
                + (comerciantes.size() - activos) + ((comerciantes.size() - activos) == 1 ? " inactivo." : " inactivos.");
        return listo(texto, Map.of("comerciantes", comerciantes.size(), "comerciantesActivos", activos));
    }

    private VozResultado productos() {
        int activos = productoRepo.findByActivoTrue().size();
        int bajoStock = productoRepo.findProductosBajoStock().size();
        String texto = "Tienes " + cantidad(activos, "producto activo", "productos activos") + " en el catálogo"
                + (bajoStock == 0 ? " y ninguno está por debajo del mínimo."
                : "; " + bajoStock + (bajoStock == 1 ? " está" : " están") + " por debajo del mínimo.");
        return listo(texto, Map.of("productosActivos", activos, "bajoStock", bajoStock));
    }

    private VozResultado pedidos(LocalDate desde, LocalDate hasta, String cuando, boolean soloPendientes) {
        List<PedidoProveedor> pedidos = pedidoRepo.findAllByOrderByFechaDesc().stream()
                .filter(p -> desde == null || (p.getFecha() != null
                        && !p.getFecha().isBefore(desde) && !p.getFecha().isAfter(hasta)))
                .toList();
        long pendientes = pedidos.stream().filter(p -> p.getEstado() == EstadoPedido.PENDIENTE).count();
        long recibidos = pedidos.stream().filter(p -> p.getEstado() == EstadoPedido.RECIBIDO).count();
        long cancelados = pedidos.stream().filter(p -> p.getEstado() == EstadoPedido.CANCELADO).count();

        String texto;
        if (soloPendientes) {
            texto = (cuando == null ? "Hay " : capitalizar(cuando) + " hay ")
                    + cantidad(pendientes, "pedido pendiente", "pedidos pendientes") + " al proveedor.";
        } else {
            String base = cuando == null ? "En total hay " : capitalizar(cuando) + " se hicieron ";
            texto = base + cantidad(pedidos.size(), "pedido", "pedidos") + " al proveedor"
                    + (pedidos.isEmpty() ? "." : ": " + pendientes + (pendientes == 1 ? " pendiente, " : " pendientes, ")
                    + recibidos + (recibidos == 1 ? " recibido y " : " recibidos y ")
                    + cancelados + (cancelados == 1 ? " cancelado." : " cancelados."));
        }
        return listo(texto, Map.of("total", pedidos.size(), "pendientes", pendientes,
                "recibidos", recibidos, "cancelados", cancelados));
    }

    private VozResultado usuarios() {
        List<Usuario> usuarios = usuarioRepo.findByTenantId(TenantContext.requerido());
        long activos = usuarios.stream().filter(u -> Boolean.TRUE.equals(u.getActivo())).count();
        String texto = "Hay " + cantidad(usuarios.size(), "usuario", "usuarios") + " en tu distribuidora, "
                + activos + (activos == 1 ? " activo." : " activos.");
        return listo(texto, Map.of("usuarios", usuarios.size(), "usuariosActivos", activos));
    }

    // ══════════════════════════════════════════════════════════════════════
    // Auxiliares
    // ══════════════════════════════════════════════════════════════════════

    private static Optional<Tema> tema(String slot) {
        if (slot == null || slot.isBlank()) return Optional.empty();
        return Arrays.stream(Tema.values()).filter(t -> t.name().equalsIgnoreCase(slot.trim())).findFirst();
    }

    /** El orden importa: "pedidos de la recomendación" es de recomendaciones; "clientes comerciantes", de comerciantes. */
    static Optional<Tema> temaPorTexto(String normalizado) {
        if (RECOMENDACIONES.matcher(normalizado).find()) return Optional.of(Tema.RECOMENDACIONES);
        if (COMERCIANTES.matcher(normalizado).find()) return Optional.of(Tema.COMERCIANTES);
        if (CLIENTES.matcher(normalizado).find()) return Optional.of(Tema.CLIENTES);
        if (PEDIDOS.matcher(normalizado).find()) return Optional.of(Tema.PEDIDOS);
        if (USUARIOS.matcher(normalizado).find()) return Optional.of(Tema.USUARIOS);
        if (PRODUCTOS.matcher(normalizado).find()) return Optional.of(Tema.PRODUCTOS);
        return Optional.empty();
    }

    private static String estado(EstadoRecomendacion estado) {
        return switch (estado) {
            case GENERADA -> "pendiente";
            case EN_REVISION -> "en revisión";
            case PEDIDO_GENERADO -> "convertida en pedido";
            case DESCARTADA -> "descartada";
        };
    }

    private static String cantidad(long n, String singular, String plural) {
        return n == 1 ? "1 " + singular : n + " " + plural;
    }

    private static String unir(List<String> partes) {
        if (partes.size() == 1) return partes.get(0);
        return String.join(", ", partes.subList(0, partes.size() - 1)) + " y " + partes.get(partes.size() - 1);
    }

    private static String capitalizar(String s) {
        return s == null || s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    /** El payload es el mismo resultado: si se pidió confirmación por baja confianza, se devuelve al confirmar. */
    private static VozResultado listo(String texto, Map<String, Object> datos) {
        VozResultado resultado = new VozResultado(true, texto, datos, null);
        return new VozResultado(true, texto, datos, resultado);
    }
}
