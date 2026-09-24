package com.gelox.backend.voz.handlers;

import com.gelox.backend.dto.CalcularVentaRequest;
import com.gelox.backend.dto.CalcularVentaResponse;
import com.gelox.backend.dto.CatalogoVentaDTO;
import com.gelox.backend.dto.ItemCalculoRequest;
import com.gelox.backend.dto.ItemCalculoResultado;
import com.gelox.backend.entities.Usuario;
import com.gelox.backend.security.RequiereRol;
import com.gelox.backend.services.VentaService;
import com.gelox.backend.ventas.rural.ClienteRuralService;
import com.gelox.backend.ventas.rural.VentaRuralService;
import com.gelox.backend.ventas.rural.dto.ClienteRuralDTO;
import com.gelox.backend.ventas.rural.dto.ConfirmarPedidoRuralRequest;
import com.gelox.backend.ventas.rural.dto.ItemPedidoRuralRequest;
import com.gelox.backend.ventas.rural.dto.PedidoRuralResponse;
import com.gelox.backend.voz.NormalizadorVoz;
import com.gelox.backend.voz.ResolvedorProducto;
import com.gelox.backend.voz.VozContexto;
import com.gelox.backend.voz.VozPendiente;
import com.gelox.backend.voz.VozResultado;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * T41-BE4 — "vende dos cajas de Festival para doña Marta, envío 8.000".
 * Lo invoca {@link RegistrarVentaHandler} cuando el comando es de canal rural;
 * al confirmar reutiliza {@link VentaRuralService#confirmarPedidoRural}.
 */
@Component
@RequiredArgsConstructor
public class RegistrarVentaRuralFlujo {

    /** Sobre el texto original: conserva mayúsculas y tildes del nombre. */
    private static final Pattern DESTINATARIO_PATTERN = Pattern.compile(
            "(?iu)\\bpara\\s+(.+?)(?=\\s*,|\\s+con\\s+|\\s+en\\s+|\\s+env[ií]o\\b|$)");
    private static final Pattern HONORIFICO_PATTERN = Pattern.compile(
            "(?iu)^(do[nñ]a|don|se[nñ]ora|se[nñ]or|sra?\\.?|sr\\.?)\\s+");

    /** Sobre texto normalizado: "ocho mil" llega como "8 mil". */
    private static final Pattern ENVIO_PATTERN = Pattern.compile("envio\\s+(?:de\\s+)?\\$?(\\d+)(\\s+mil\\b)?");

    /** Grupos: 1 cantidad, 2 caja(s)/unidad(es), 3 producto, 4 precio dicho (opcional). */
    private static final Pattern ITEM_PATTERN = Pattern.compile(
            "(\\d+)\\s*(cajas?|unidades?)\\s+de\\s+(.+?)(?:\\s+a\\s+\\$?(\\d+))?"
                    + "(?=\\s*,|\\s+y\\s+|\\s+para\\b|\\s+envio\\b|\\s+con\\b|\\s+en\\b|\\s+por\\b|$)");

    private static final int MAX_CLIENTES_EN_TEXTO = 5;

    private final ResolvedorProducto resolvedorProducto;
    private final VentaService ventaService;
    private final ClienteRuralService clienteRuralService;
    private final VentaRuralService ventaRuralService;

    /** Público: {@link RegistrarVentaHandler} lo usa para enrutar {@code ejecutar}. */
    public record Payload(UUID clienteRuralId, String nombreDestinatario,
                          BigDecimal costoEnvio, List<ItemPedidoRuralRequest> items) {}

    private record ItemExtraido(int cajas, int unidades, String fragmentoProducto, BigDecimal precioDicho) {}

    @RequiereRol({"ADMINISTRADOR", "ENCARGADO_VENTAS"})
    public VozResultado interpretar(VozContexto ctx) {
        if (ctx.pendiente() != null) {
            return error("No puedo combinar un pedido rural con una venta en curso. "
                    + "Confirma o cancela la actual y repite el pedido rural completo.");
        }

        String original = ctx.texto();
        String texto = NormalizadorVoz.normalizar(original);

        String fragmentoDestinatario = extraerDestinatario(original);
        if (fragmentoDestinatario.isEmpty()) {
            return error("¿Para quién es el pedido rural?");
        }

        BigDecimal costoEnvio = extraerCostoEnvio(texto);

        List<ItemExtraido> extraidos = extraerItems(texto);
        if (extraidos.isEmpty()) {
            return error("No entendí las cantidades. Di algo como 'vende dos cajas de Festival para doña Marta, envío 8.000'.");
        }

        // productoId -> {cajas, unidades}; el mismo producto dicho dos veces se suma.
        Map<UUID, int[]> cantidades = new LinkedHashMap<>();
        Map<UUID, String> nombresDichos = new HashMap<>();
        Map<UUID, BigDecimal> preciosDichos = new HashMap<>();

        for (ItemExtraido extraido : extraidos) {
            List<ResolvedorProducto.ProductoCandidato> candidatos =
                    resolvedorProducto.resolver(extraido.fragmentoProducto());

            if (candidatos.isEmpty()) {
                return error("No encontré el producto \"" + extraido.fragmentoProducto() + "\". ¿Puedes repetirlo?");
            }
            if (esAmbiguo(candidatos)) {
                return error("¿" + candidatos.get(0).nombre() + " o " + candidatos.get(1).nombre() + "?");
            }

            ResolvedorProducto.ProductoCandidato producto = candidatos.get(0);
            int[] acumulado = cantidades.computeIfAbsent(producto.id(), id -> new int[2]);
            acumulado[0] += extraido.cajas();
            acumulado[1] += extraido.unidades();
            nombresDichos.put(producto.id(), producto.nombre());
            if (extraido.precioDicho() != null) {
                preciosDichos.put(producto.id(), extraido.precioDicho());
            }
        }

        boolean hayCantidadPositiva = cantidades.values().stream().anyMatch(c -> c[0] > 0 || c[1] > 0);
        if (!hayCantidadPositiva) {
            return error("No entendí ninguna cantidad válida. Intenta de nuevo.");
        }

        // Destinatario: uno → cliente existente; ninguno → nuevo; varios → pedir nombre completo.
        List<ClienteRuralDTO> encontrados = clienteRuralService.listarClientes(fragmentoDestinatario);
        if (encontrados.size() > 1) {
            return variosClientes(encontrados);
        }
        UUID clienteRuralId = encontrados.isEmpty() ? null : encontrados.get(0).id();
        String nombreDestinatario = encontrados.isEmpty() ? fragmentoDestinatario : encontrados.get(0).nombre();

        Map<UUID, CatalogoVentaDTO> catalogo = ventaService.getCatalogo().stream()
                .collect(Collectors.toMap(CatalogoVentaDTO::id, Function.identity()));

        List<ItemPedidoRuralRequest> items = new ArrayList<>();
        List<ItemCalculoRequest> itemsCalculo = new ArrayList<>();

        for (Map.Entry<UUID, int[]> entrada : cantidades.entrySet()) {
            UUID productoId = entrada.getKey();
            int cajas = entrada.getValue()[0];
            int unidades = entrada.getValue()[1];
            if (cajas == 0 && unidades == 0) continue;
            CatalogoVentaDTO producto = catalogo.get(productoId);

            if (producto == null) {
                return error("No encontré el producto \"" + nombresDichos.get(productoId) + "\". ¿Puedes repetirlo?");
            }

            int upC = producto.unidadesPorCaja() != null ? producto.unidadesPorCaja() : 0;
            int solicitadas = cajas * upC + unidades;
            if (producto.stock() < solicitadas) {
                return error("Stock insuficiente para " + producto.nombre()
                        + ". Disponible: " + producto.stock() + ", solicitado: " + solicitadas);
            }

            items.add(new ItemPedidoRuralRequest(productoId, cajas, unidades));
            itemsCalculo.add(new ItemCalculoRequest(productoId, cajas, unidades));
        }

        CalcularVentaResponse calculo = ventaService.calcularVenta(new CalcularVentaRequest(itemsCalculo));
        Map<UUID, BigDecimal> subtotales = calculo.items().stream()
                .collect(Collectors.toMap(ItemCalculoResultado::productoId, ItemCalculoResultado::subtotal));
        BigDecimal totalProductos = calculo.total();
        BigDecimal total = totalProductos.add(costoEnvio);

        List<Map<String, Object>> datosItems = new ArrayList<>();
        List<String> descripciones = new ArrayList<>();
        List<String> avisosPrecio = new ArrayList<>();

        for (ItemPedidoRuralRequest item : items) {
            CatalogoVentaDTO producto = catalogo.get(item.productoId());
            datosItems.add(Map.of(
                    "productoId", item.productoId(),
                    "nombre", producto.nombre(),
                    "cajas", item.cajas(),
                    "unidades", item.unidades(),
                    "subtotal", subtotales.get(item.productoId())));
            descripciones.add(describir(item.cajas(), item.unidades()) + " de " + producto.nombre());

            // confirmarPedidoRural siempre cobra el precio de catálogo: el dicho solo se avisa.
            BigDecimal precioDicho = preciosDichos.get(item.productoId());
            if (precioDicho != null && precioDicho.compareTo(producto.precioUnitario()) != 0) {
                avisosPrecio.add(" El precio de catálogo de " + producto.nombre() + " es "
                        + sinCeros(producto.precioUnitario()) + " y se usará ese.");
            }
        }

        String textoRespuesta = "Pedido rural para " + nombreDestinatario
                + (clienteRuralId == null ? " (destinatario nuevo)" : "") + ": "
                + String.join(" y ", descripciones) + ". Envío $" + sinCeros(costoEnvio)
                + ". Total $" + sinCeros(total) + "." + String.join("", avisosPrecio) + " ¿Confirmas?";

        Map<String, Object> datos = Map.of(
                "canal", "RURAL",
                "destinatario", nombreDestinatario,
                "items", datosItems,
                "costoEnvio", costoEnvio,
                "totalProductos", totalProductos,
                "total", total);

        return new VozResultado(true, textoRespuesta, datos,
                new Payload(clienteRuralId, nombreDestinatario, costoEnvio, items));
    }

    @RequiereRol({"ADMINISTRADOR", "ENCARGADO_VENTAS"})
    public VozResultado ejecutar(VozPendiente pendiente, Usuario usuario) {
        Payload p = (Payload) pendiente.payload();

        if (p.clienteRuralId() == null && (p.nombreDestinatario() == null || p.nombreDestinatario().isBlank())) {
            return error("¿Para quién es el pedido rural?");
        }

        // Si el stock cambió desde interpretar, StockInsuficienteException sube tal cual (422).
        PedidoRuralResponse r = ventaRuralService.confirmarPedidoRural(new ConfirmarPedidoRuralRequest(
                p.clienteRuralId(), p.nombreDestinatario(), null, null, null, p.costoEnvio(), p.items()), usuario);

        return new VozResultado(true, "Pedido rural registrado por $" + sinCeros(r.total()) + ".",
                Map.of("ventaId", r.ventaId(), "pedidoRuralId", r.pedidoRuralId(), "total", r.total()), null);
    }

    private VozResultado error(String mensaje) {
        return new VozResultado(false, mensaje, Map.of(), null);
    }

    private VozResultado variosClientes(List<ClienteRuralDTO> encontrados) {
        List<String> nombres = new ArrayList<>();
        List<Map<String, Object>> clientes = new ArrayList<>();

        for (ClienteRuralDTO c : encontrados) {
            if (nombres.size() < MAX_CLIENTES_EN_TEXTO) {
                boolean conCorregimiento = c.corregimiento() != null && !c.corregimiento().isBlank();
                nombres.add(c.nombre() + (conCorregimiento ? " (" + c.corregimiento() + ")" : ""));
            }
            // HashMap: teléfono y corregimiento pueden ser nulos.
            Map<String, Object> cliente = new HashMap<>();
            cliente.put("id", c.id());
            cliente.put("nombre", c.nombre());
            cliente.put("telefono", c.telefono());
            cliente.put("corregimiento", c.corregimiento());
            clientes.add(cliente);
        }

        int restantes = encontrados.size() - nombres.size();
        String texto = "Encontré varios clientes: " + String.join(", ", nombres)
                + (restantes > 0 ? " y " + restantes + " más" : "")
                + ". Repite el pedido con el nombre completo.";

        return new VozResultado(false, texto, Map.of("clientes", clientes), null);
    }

    /** "para doña Marta, envío 8.000" → "Marta"; vacío si no se dijo. */
    private String extraerDestinatario(String original) {
        Matcher matcher = DESTINATARIO_PATTERN.matcher(original);
        if (!matcher.find()) return "";
        String nombre = HONORIFICO_PATTERN.matcher(matcher.group(1).trim()).replaceFirst("");
        // El dictado suele cerrar con punto: "para doña Marta."
        return nombre.replaceAll("[.!?]+$", "").trim();
    }

    private BigDecimal extraerCostoEnvio(String texto) {
        Matcher matcher = ENVIO_PATTERN.matcher(texto);
        if (!matcher.find()) return BigDecimal.ZERO;
        BigDecimal costo = new BigDecimal(matcher.group(1));
        return matcher.group(2) != null ? costo.multiply(BigDecimal.valueOf(1000)) : costo;
    }

    /** Ambiguo si el segundo candidato queda a menos de 0.10 del primero. */
    private boolean esAmbiguo(List<ResolvedorProducto.ProductoCandidato> candidatos) {
        return candidatos.size() >= 2 && (candidatos.get(0).score() - candidatos.get(1).score()) < 0.10;
    }

    private List<ItemExtraido> extraerItems(String textoNormalizado) {
        List<ItemExtraido> items = new ArrayList<>();
        Matcher matcher = ITEM_PATTERN.matcher(textoNormalizado);

        while (matcher.find()) {
            int cantidad = Integer.parseInt(matcher.group(1));
            boolean esCaja = matcher.group(2).startsWith("caja");
            BigDecimal precioDicho = matcher.group(4) == null ? null : new BigDecimal(matcher.group(4));

            items.add(new ItemExtraido(esCaja ? cantidad : 0, esCaja ? 0 : cantidad,
                    matcher.group(3).trim(), precioDicho));
        }
        return items;
    }

    private String describir(int cajas, int unidades) {
        List<String> partes = new ArrayList<>();
        if (cajas > 0) partes.add(cajas + (cajas == 1 ? " caja" : " cajas"));
        if (unidades > 0) partes.add(unidades + (unidades == 1 ? " unidad" : " unidades"));
        return String.join(" y ", partes);
    }

    private String sinCeros(BigDecimal valor) {
        return valor.stripTrailingZeros().toPlainString();
    }
}
