package com.gelox.backend.voz.handlers;

import com.gelox.backend.dto.CrearPedidoRequest;
import com.gelox.backend.dto.ItemPedidoRequest;
import com.gelox.backend.entities.TipoIntencionVoz;
import com.gelox.backend.entities.Usuario;
import com.gelox.backend.security.RequiereRol;
import com.gelox.backend.services.PedidoProveedorService;
import com.gelox.backend.voz.IntencionHandler;
import com.gelox.backend.voz.NormalizadorVoz;
import com.gelox.backend.voz.PreguntaVoz;
import com.gelox.backend.voz.ResolvedorProducto;
import com.gelox.backend.voz.VozContexto;
import com.gelox.backend.voz.VozPendiente;
import com.gelox.backend.voz.VozResultado;
import com.gelox.backend.recomendacion.dto.PedidoGeneradoDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * RF52/RF53 — "genera un pedido con 20 cajas de Solo Lack y 15 de Festival". RF63: se genera un
 * pedido por proveedor de los productos, y el resumen lo dice antes de confirmar.
 * crearPedido no valida rol y /api/voz/** solo exige estar autenticado, así
 * que el filtro de rol vive aquí.
 */
@Component
@RequiredArgsConstructor
public class GenerarPedidoHandler implements IntencionHandler {

    private static final Pattern ITEM_PATTERN =
            Pattern.compile("(\\d+)\\s*(cajas?|unidad(?:es)?)?\\s*de\\s+(.+?)"
                    // "pide 2 cajas de X a nutresa": el destinatario no es parte del producto
                    + "(?:\\s+y\\s+|,|\\s+(?:a|al|para)\\s+(?:nutresa|proveedor)\\b|$)");

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
                return PreguntaVoz.reemplazando(
                        "No encontré el producto \"" + extraido.fragmentoProducto() + "\". ¿Puedes repetirlo?",
                        extraido.fragmentoProducto(), null);
            }
            if (esAmbiguo(candidatos)) {
                return PreguntaVoz.reemplazando(
                        "¿" + candidatos.get(0).nombre() + " o " + candidatos.get(1).nombre() + "?",
                        extraido.fragmentoProducto(), List.of(candidatos.get(0).nombre(), candidatos.get(1).nombre()));
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

        // RF63 — se infiere el proveedor de cada producto: un pedido por proveedor
        Map<UUID, PedidoProveedorService.ProveedorPedido> proveedores;
        try {
            proveedores = pedidoProveedorService.proveedoresDe(items.stream().map(ItemPedidoRequest::productoId).toList());
        } catch (ResponseStatusException e) {
            return new VozResultado(false, e.getReason() + ". Asígnale uno en el catálogo.", Map.of(), null);
        }
        Map<String, List<String>> porProveedor = new LinkedHashMap<>();
        for (int i = 0; i < items.size(); i++) {
            PedidoProveedorService.ProveedorPedido proveedor = proveedores.get(items.get(i).productoId());
            porProveedor.computeIfAbsent(proveedor == null ? "" : proveedor.nombre(), k -> new ArrayList<>())
                    .add(descripciones.get(i));
        }

        CrearPedidoRequest payload = new CrearPedidoRequest(items, "Generado por voz");
        return new VozResultado(true, resumen(porProveedor), Map.of("items", datosItems), payload);
    }

    /**
     * Un proveedor: «Pedido a Nutresa: 20 cajas de X y 15 cajas de Y. ¿Confirmas?» (sin proveedor
     * conocido: «Pedido: …»). Varios: «Se generarán 2 pedidos: a Nutresa, 20 cajas de X; a Proveedor
     * QA Uno, 15 cajas de Y. ¿Confirmas?».
     */
    private static String resumen(Map<String, List<String>> porProveedor) {
        if (porProveedor.size() == 1) {
            Map.Entry<String, List<String>> unico = porProveedor.entrySet().iterator().next();
            String destino = unico.getKey().isEmpty() ? "" : " a " + unico.getKey();
            return "Pedido" + destino + ": " + String.join(" y ", unico.getValue()) + ". ¿Confirmas?";
        }
        List<String> partes = new ArrayList<>();
        porProveedor.forEach((proveedor, lineas) -> partes.add(
                "a " + (proveedor.isEmpty() ? "otro proveedor" : proveedor) + ", " + String.join(" y ", lineas)));
        return "Se generarán " + porProveedor.size() + " pedidos: " + String.join("; ", partes) + ". ¿Confirmas?";
    }

    @Override
    @RequiereRol({"ADMINISTRADOR", "ENCARGADO_INVENTARIO"})
    public VozResultado ejecutar(VozPendiente pendiente, Usuario usuario) {
        CrearPedidoRequest req = (CrearPedidoRequest) pendiente.payload();

        List<PedidoProveedorService.PedidoCreado> creados = pedidoProveedorService.crearPedido(req, usuario);
        PedidoGeneradoDTO generado = PedidoGeneradoDTO.de(creados);

        // Mismas claves que POST /api/recomendaciones/{id}/pedido; pedidoId y exportUrl son del primero
        Map<String, Object> datos = Map.of(
                "pedidoId", generado.pedidoId(),
                "exportUrl", generado.exportUrl(),
                "pedidos", generado.pedidos());

        StringBuilder texto = new StringBuilder(creados.size() == 1
                ? "Pedido registrado. Ya puedes descargar el archivo."
                : "Se registraron " + creados.size() + " pedidos, uno por proveedor. Ya puedes descargar los archivos.");
        creados.stream()
                .map(PedidoProveedorService.PedidoCreado::advertenciaMontoMinimo)
                .filter(Objects::nonNull)
                .forEach(a -> texto.append(" Ojo: ").append(a));
        return new VozResultado(true, texto.toString(), datos, null);
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
