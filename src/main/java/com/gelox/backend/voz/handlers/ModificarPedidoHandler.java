package com.gelox.backend.voz.handlers;

import com.gelox.backend.dto.AccionPedido;
import com.gelox.backend.dto.ModificarPedidoRequest;
import com.gelox.backend.entities.EstadoPedido;
import com.gelox.backend.entities.ItemPedidoProveedor;
import com.gelox.backend.entities.PedidoProveedor;
import com.gelox.backend.entities.TipoIntencionVoz;
import com.gelox.backend.entities.Usuario;
import com.gelox.backend.repositories.PedidoProveedorRepository;
import com.gelox.backend.security.RequiereRol;
import com.gelox.backend.services.PedidoProveedorService;
import com.gelox.backend.voz.IntencionHandler;
import com.gelox.backend.voz.NormalizadorVoz;
import com.gelox.backend.voz.ResolvedorProducto;
import com.gelox.backend.voz.VozContexto;
import com.gelox.backend.voz.VozPendiente;
import com.gelox.backend.voz.VozResultado;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * RF53 — "al pedido pendiente agrégale 10 de Festival". Opera siempre sobre
 * el pedido PENDIENTE más reciente.
 */
@Component
@RequiredArgsConstructor
public class ModificarPedidoHandler implements IntencionHandler {

    /** "de <producto>", tolerando un "del/al pedido..." pegado al final. */
    private static final Pattern PRODUCTO_PATTERN =
            Pattern.compile("\\bde\\s+(.+?)(?:\\s+(?:del|al)\\s+pedido\\b.*)?$");
    private static final Pattern AGREGAR = Pattern.compile("\\b(agreg|anad|sum|mete|metele|incluye|pon)\\w*");
    private static final Pattern ELIMINAR = Pattern.compile("\\b(quit|saca|elimin|borr)\\w*");
    /** "deja Festival en 30", "cambia a 30": reemplaza la cantidad en vez de sumarla. */
    private static final Pattern ACTUALIZAR = Pattern.compile("\\b(cambi|actualiz|modific|corrig|deja)\\w*");

    private static final Pattern CANTIDAD_PATTERN = Pattern.compile("\\b(\\d+)\\b");

    private static final DateTimeFormatter FECHA_CORTA = DateTimeFormatter.ofPattern("d 'de' MMMM", Locale.of("es", "CO"));

    /** Sin "de": "saca el Solo Lack del pedido", "deja Festival en 30 cajas" → lo que sobra es el producto. */
    private static final Pattern RELLENO = Pattern.compile(
            "\\b(agreg|anad|sum|mete|metele|incluye|pon|quit|saca|elimin|borr|cambi|actualiz|modific|corrig|deja)\\w*"
                    + "|\\b(al|el|la|los|las|del|en|a|y|con|lo|pedido|pendiente|proveedor|ultimo|cajas?|cajitas?"
                    + "|unidad(es)?|mas|por favor|gelox)\\b|\\d+");

    private final PedidoProveedorRepository pedidoRepository;
    private final ResolvedorProducto resolvedorProducto;
    private final PedidoProveedorService pedidoProveedorService;

    private record Payload(UUID pedidoId, ModificarPedidoRequest request) {}

    @Override
    public TipoIntencionVoz tipo() {
        return TipoIntencionVoz.MODIFICAR_PEDIDO;
    }

    @Override
    public boolean requiereConfirmacion() {
        return true;
    }

    @Override
    @RequiereRol({"ADMINISTRADOR", "ENCARGADO_INVENTARIO"})
    public VozResultado interpretar(VozContexto ctx) {
        String texto = NormalizadorVoz.normalizar(ctx.texto());

        AccionPedido accion = detectarAccion(texto);
        if (accion == null) {
            return sinCambios("No entendí qué quieres hacer con el pedido.");
        }

        List<PedidoProveedor> pendientes = pedidoRepository.findByEstadoOrderByFechaDesc(EstadoPedido.PENDIENTE)
                .stream()
                .sorted(Comparator.comparing(PedidoProveedor::getFecha)
                        .thenComparing(PedidoProveedor::getCreatedAt, Comparator.nullsFirst(Comparator.naturalOrder()))
                        .reversed())
                .toList();
        if (pendientes.isEmpty()) {
            return sinCambios("No hay pedidos pendientes.");
        }

        // Con varios pendientes hay que decir a cuál: "al pedido 2…", "al del 8 de octubre…", "al de ayer…".
        PedidoProveedor elegido = pendientes.get(0);
        if (pendientes.size() > 1) {
            SelectorPedido selector = SelectorPedido.de(texto, ctx.hoy());
            elegido = selector.elegir(pendientes);
            if (elegido == null) {
                return sinCambios(listarPendientes(pendientes));
            }
            texto = selector.textoSinSelector();
        }

        // Los ítems y productos son lazy y aquí no hay sesión: se cargan con JOIN FETCH.
        PedidoProveedor pedido = pedidoRepository.findByIdWithItems(elegido.getId()).orElse(null);
        if (pedido == null) {
            return sinCambios("No hay pedidos pendientes.");
        }

        String fragmentoProducto = extraerProducto(texto);
        if (fragmentoProducto.isEmpty()) {
            return sinCambios("No entendí de qué producto se trata.");
        }

        List<ResolvedorProducto.ProductoCandidato> candidatos = resolvedorProducto.resolver(fragmentoProducto);
        if (candidatos.isEmpty()) {
            return sinCambios("No encontré el producto \"" + fragmentoProducto + "\". ¿Puedes repetirlo?");
        }
        if (esAmbiguo(candidatos)) {
            return sinCambios("¿" + candidatos.get(0).nombre() + " o " + candidatos.get(1).nombre() + "?");
        }
        ResolvedorProducto.ProductoCandidato producto = candidatos.get(0);

        ItemPedidoProveedor itemActual = pedido.getItems().stream()
                .filter(i -> i.getProducto().getId().equals(producto.id()))
                .findFirst()
                .orElse(null);

        if (accion != AccionPedido.AGREGAR && itemActual == null) {
            return sinCambios("El producto " + producto.nombre() + " no está en el pedido pendiente.");
        }

        String pedidoCorto = pedido.getId().toString().substring(0, 8).toUpperCase();

        if (accion == AccionPedido.ELIMINAR) {
            int cantidadAnterior = itemActual.getCantidadCajas() > 0
                    ? itemActual.getCantidadCajas() : itemActual.getCantidadUnidades();

            ModificarPedidoRequest request = new ModificarPedidoRequest(AccionPedido.ELIMINAR, producto.id(), null, null);
            String textoRespuesta = "Al pedido #" + pedidoCorto + " se elimina " + producto.nombre() + ". ¿Confirmas?";

            return new VozResultado(true, textoRespuesta,
                    datosResumen(pedido.getId(), accion, producto.nombre(), cantidadAnterior, 0),
                    new Payload(pedido.getId(), request));
        }

        Matcher cantidadMatcher = CANTIDAD_PATTERN.matcher(texto);
        if (!cantidadMatcher.find()) {
            return sinCambios("No entendí la cantidad.");
        }
        int cantidad = Integer.parseInt(cantidadMatcher.group(1));
        boolean esCaja = !texto.contains("unidad");
        String unidadTexto = esCaja ? "cajas" : "unidades";
        int cajas = esCaja ? cantidad : 0;
        int unidades = esCaja ? 0 : cantidad;

        int cantidadAnterior = itemActual == null ? 0
                : (esCaja ? itemActual.getCantidadCajas() : itemActual.getCantidadUnidades());

        String textoRespuesta;
        int cantidadNueva;

        if (accion == AccionPedido.AGREGAR) {
            cantidadNueva = cantidadAnterior + cantidad;
            textoRespuesta = "Al pedido #" + pedidoCorto + " se agregan " + cantidad + " " + unidadTexto
                    + " de " + producto.nombre() + " (quedarían " + cantidadNueva + "). ¿Confirmas?";
        } else { // ACTUALIZAR
            cantidadNueva = cantidad;
            textoRespuesta = "Al pedido #" + pedidoCorto + " se actualiza " + producto.nombre()
                    + " a " + cantidad + " " + unidadTexto + ". ¿Confirmas?";
        }

        ModificarPedidoRequest request = new ModificarPedidoRequest(accion, producto.id(), cajas, unidades);

        return new VozResultado(true, textoRespuesta,
                datosResumen(pedido.getId(), accion, producto.nombre(), cantidadAnterior, cantidadNueva),
                new Payload(pedido.getId(), request));
    }

    @Override
    @RequiereRol({"ADMINISTRADOR", "ENCARGADO_INVENTARIO"})
    public VozResultado ejecutar(VozPendiente pendiente, Usuario usuario) {
        Payload payload = (Payload) pendiente.payload();

        pedidoProveedorService.modificarPedidoPendiente(payload.pedidoId(), payload.request(), usuario);

        Map<String, Object> datos = Map.of(
                "pedidoId", payload.pedidoId(),
                "exportUrl", "/api/inventario/pedidos/" + payload.pedidoId() + "/exportar");

        return new VozResultado(true, "Pedido modificado. Ya puedes descargar el archivo actualizado.", datos, null);
    }

    /** "Hay 2 pedidos pendientes: 1) del 10 de octubre: 20 cajas de Solo Lack…; 2) …. ¿A cuál?" */
    private String listarPendientes(List<PedidoProveedor> pendientes) {
        List<String> descripciones = new ArrayList<>();
        for (int i = 0; i < pendientes.size(); i++) {
            PedidoProveedor p = pedidoRepository.findByIdWithItems(pendientes.get(i).getId()).orElse(pendientes.get(i));
            String productos = p.getItems() == null ? "" : p.getItems().stream().limit(2)
                    .map(item -> item.getProducto().getNombre())
                    .collect(Collectors.joining(" y "));
            descripciones.add((i + 1) + ") del " + p.getFecha().format(FECHA_CORTA)
                    + (productos.isEmpty() ? "" : " con " + productos));
        }
        return "Hay " + pendientes.size() + " pedidos pendientes: " + String.join("; ", descripciones)
                + ". ¿A cuál? Di por ejemplo 'al pedido 1 agrégale 10 cajas de Minions'.";
    }

    private VozResultado sinCambios(String mensaje) {
        return new VozResultado(false, mensaje, Map.of(), null);
    }

    private Map<String, Object> datosResumen(UUID pedidoId, AccionPedido accion, String producto,
                                              int cantidadAnterior, int cantidadNueva) {
        return Map.of(
                "pedidoId", pedidoId,
                "accion", accion.name(),
                "producto", producto,
                "cantidadAnterior", cantidadAnterior,
                "cantidadNueva", cantidadNueva);
    }

    private boolean esAmbiguo(List<ResolvedorProducto.ProductoCandidato> candidatos) {
        return candidatos.size() >= 2 && (candidatos.get(0).score() - candidatos.get(1).score()) < 0.10;
    }

    private static String extraerProducto(String texto) {
        Matcher m = PRODUCTO_PATTERN.matcher(texto);
        String fragmento = m.find() ? m.group(1) : RELLENO.matcher(texto).replaceAll(" ");
        return fragmento.replaceAll("[^a-z0-9 ]", " ").replaceAll("\\s+", " ").trim();
    }

    private AccionPedido detectarAccion(String texto) {
        if (ELIMINAR.matcher(texto).find()) return AccionPedido.ELIMINAR;
        if (ACTUALIZAR.matcher(texto).find()) return AccionPedido.ACTUALIZAR;
        if (AGREGAR.matcher(texto).find()) return AccionPedido.AGREGAR;
        return null;
    }
}
