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

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
    private static final Pattern CANTIDAD_PATTERN = Pattern.compile("\\b(\\d+)\\b");

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

        List<PedidoProveedor> pendientes = pedidoRepository.findByEstadoOrderByFechaDesc(EstadoPedido.PENDIENTE);
        if (pendientes.isEmpty()) {
            return sinCambios("No hay pedidos pendientes.");
        }
        // Los ítems y productos son lazy y aquí no hay sesión: se cargan con JOIN FETCH.
        PedidoProveedor pedido = pedidoRepository.findByIdWithItems(pendientes.get(0).getId()).orElse(null);
        if (pedido == null) {
            return sinCambios("No hay pedidos pendientes.");
        }

        Matcher productoMatcher = PRODUCTO_PATTERN.matcher(texto);
        if (!productoMatcher.find()) {
            return sinCambios("No entendí de qué producto se trata.");
        }
        String fragmentoProducto = productoMatcher.group(1).trim();

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

    private AccionPedido detectarAccion(String texto) {
        if (texto.contains("agregale") || texto.contains("agrega") || texto.contains("suma")) return AccionPedido.AGREGAR;
        if (texto.contains("quita") || texto.contains("elimina") || texto.contains("borra")) return AccionPedido.ELIMINAR;
        if (texto.contains("cambia") || texto.contains("actualiza") || texto.contains("dejalo en")) return AccionPedido.ACTUALIZAR;
        return null;
    }
}
