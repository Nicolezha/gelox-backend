package com.gelox.backend.voz.handlers;

import com.gelox.backend.dto.CrearPedidoRequest;
import com.gelox.backend.dto.ItemPedidoRequest;
import com.gelox.backend.entities.TipoIntencionVoz;
import com.gelox.backend.entities.Usuario;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * RF52/RF53 — "genera un pedido con 20 cajas de Solo Lack y 15 de Festival".
 * crearPedido no valida rol y /api/voz/** solo exige estar autenticado, así
 * que el filtro de rol vive aquí.
 */
@Component
@RequiredArgsConstructor
public class GenerarPedidoHandler implements IntencionHandler {

    private static final Pattern ITEM_PATTERN =
            Pattern.compile("(\\d+)\\s*(cajas?|unidad(?:es)?)?\\s*de\\s+(.+?)(?:\\s+y\\s+|,|$)");

    private static final String UNIDAD_DEFECTO = "cajas";

    private final ResolvedorProducto resolvedorProducto;
    private final PedidoProveedorService pedidoProveedorService;

    private record ItemExtraido(int cantidad, String unidad, String fragmentoProducto) {}

    @Override
    public TipoIntencionVoz tipo() {
        return TipoIntencionVoz.GENERAR_PEDIDO;
    }

    @Override
    public boolean requiereConfirmacion() {
        return true;
    }

    @Override
    @RequiereRol({"ADMINISTRADOR", "ENCARGADO_INVENTARIO"})
    public VozResultado interpretar(VozContexto ctx) {
        List<ItemExtraido> extraidos = extraerItems(NormalizadorVoz.normalizar(ctx.texto()));

        if (extraidos.isEmpty()) {
            return new VozResultado(false,
                    "No entendí las cantidades. Di algo como 'genera un pedido con 20 cajas de Aloha Mango Biche y 15 de Aloha Paleta Limón'.",
                    Map.of(), null);
        }

        List<ItemPedidoRequest> items = new ArrayList<>();
        List<Map<String, Object>> datosItems = new ArrayList<>();
        List<String> descripciones = new ArrayList<>();

        for (ItemExtraido extraido : extraidos) {
            List<ResolvedorProducto.ProductoCandidato> candidatos =
                    resolvedorProducto.resolver(extraido.fragmentoProducto());

            if (candidatos.isEmpty()) {
                return new VozResultado(false,
                        "No encontré el producto \"" + extraido.fragmentoProducto() + "\". ¿Puedes repetirlo?",
                        Map.of(), null);
            }
            if (esAmbiguo(candidatos)) {
                return new VozResultado(false,
                        "¿" + candidatos.get(0).nombre() + " o " + candidatos.get(1).nombre() + "?",
                        Map.of(), null);
            }

            ResolvedorProducto.ProductoCandidato producto = candidatos.get(0);
            boolean esCaja = extraido.unidad().equals(UNIDAD_DEFECTO);
            int cantidadCajas = esCaja ? extraido.cantidad() : 0;
            int cantidadUnidades = esCaja ? 0 : extraido.cantidad();

            items.add(new ItemPedidoRequest(producto.id(), cantidadCajas, cantidadUnidades));
            datosItems.add(Map.of(
                    "productoId", producto.id(),
                    "nombre", producto.nombre(),
                    "cantidadCajas", cantidadCajas,
                    "cantidadUnidades", cantidadUnidades));
            descripciones.add(extraido.cantidad() + " " + extraido.unidad() + " de " + producto.nombre());
        }

        boolean hayCantidadPositiva = items.stream()
                .anyMatch(i -> i.cantidadCajas() > 0 || i.cantidadUnidades() > 0);
        if (!hayCantidadPositiva) {
            return new VozResultado(false, "No entendí ninguna cantidad válida. Intenta de nuevo.", Map.of(), null);
        }

        CrearPedidoRequest payload = new CrearPedidoRequest(items, "Generado por voz");
        String textoRespuesta = "Pedido: " + String.join(" y ", descripciones) + ". ¿Confirmas?";

        return new VozResultado(true, textoRespuesta, Map.of("items", datosItems), payload);
    }

    @Override
    @RequiereRol({"ADMINISTRADOR", "ENCARGADO_INVENTARIO"})
    public VozResultado ejecutar(VozPendiente pendiente, Usuario usuario) {
        CrearPedidoRequest req = (CrearPedidoRequest) pendiente.payload();

        Map<String, Object> resultado = pedidoProveedorService.crearPedido(req, usuario);
        UUID pedidoId = (UUID) resultado.get("pedidoId");

        Map<String, Object> datos = Map.of(
                "pedidoId", pedidoId,
                "exportUrl", "/api/inventario/pedidos/" + pedidoId + "/exportar");

        return new VozResultado(true, "Pedido registrado. Ya puedes descargar el archivo.", datos, null);
    }

    /** Ambiguo si el segundo candidato queda a menos de 0.10 del primero. */
    private boolean esAmbiguo(List<ResolvedorProducto.ProductoCandidato> candidatos) {
        return candidatos.size() >= 2 && (candidatos.get(0).score() - candidatos.get(1).score()) < 0.10;
    }

    private List<ItemExtraido> extraerItems(String textoNormalizado) {
        List<ItemExtraido> items = new ArrayList<>();
        Matcher matcher = ITEM_PATTERN.matcher(textoNormalizado);
        String unidadAnterior = UNIDAD_DEFECTO;

        while (matcher.find()) {
            int cantidad = Integer.parseInt(matcher.group(1));
            String unidadDicha = matcher.group(2);

            String unidad = unidadDicha == null
                    ? unidadAnterior
                    : (unidadDicha.startsWith("caja") ? "cajas" : "unidades");
            unidadAnterior = unidad;

            items.add(new ItemExtraido(cantidad, unidad, matcher.group(3).trim()));
        }
        return items;
    }
}
