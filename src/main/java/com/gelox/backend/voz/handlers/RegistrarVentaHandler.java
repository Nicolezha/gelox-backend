package com.gelox.backend.voz.handlers;

import com.gelox.backend.dto.CalcularVentaRequest;
import com.gelox.backend.dto.CalcularVentaResponse;
import com.gelox.backend.dto.CatalogoVentaDTO;
import com.gelox.backend.dto.ConfirmarVentaRequest;
import com.gelox.backend.dto.ConfirmarVentaResponse;
import com.gelox.backend.dto.ItemCalculoRequest;
import com.gelox.backend.dto.ItemCalculoResultado;
import com.gelox.backend.dto.ItemVentaRequest;
import com.gelox.backend.entities.CanalVenta;
import com.gelox.backend.entities.MetodoPago;
import com.gelox.backend.entities.TipoIntencionVoz;
import com.gelox.backend.entities.Usuario;
import com.gelox.backend.security.RequiereRol;
import com.gelox.backend.services.VentaService;
import com.gelox.backend.voz.IntencionHandler;
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
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * T41 — "registra tres cajas de Festival a 2.500, canal ventanilla".
 * Con {@code ctx.pendiente()} ("agrega 2 cajas de Solo Lack") fusiona lo
 * nuevo con la venta que sigue esperando confirmación.
 */
@Component
@RequiredArgsConstructor
public class RegistrarVentaHandler implements IntencionHandler {

    private static final Pattern ITEM_PATTERN = ItemVozParser.patron(
            "\\s*,|\\s+y\\s+|\\s+canal\\b|\\s+por\\b|\\s+con\\b|\\s+en\\b");

    private static final String MENSAJE_FALTA_PRODUCTO = "Falta aclarar cuál producto es.";

    private static final String EJEMPLO = "Di algo como 'registra tres cajas de Festival a 2.500, canal ventanilla'.";

    /** "envío" también marca canal rural: el costo de envío solo existe en pedidos rurales. */
    private static final Pattern ES_RURAL = Pattern.compile("\\b(rural|envio)\\b");

    private final ResolvedorProducto resolvedorProducto;
    private final VentaService ventaService;
    private final RegistrarVentaRuralFlujo registrarVentaRuralFlujo;

    private record Payload(CanalVenta canal, MetodoPago metodoPago, List<ItemVentaRequest> items) {}

    @Override
    public TipoIntencionVoz tipo() {
        return TipoIntencionVoz.REGISTRAR_VENTA;
    }

    @Override
    public boolean requiereConfirmacion() {
        return true;
    }

    @Override
    @RequiereRol({"ADMINISTRADOR", "ENCARGADO_VENTAS"})
    public VozResultado interpretar(VozContexto ctx) {
        Object previo = ctx.pendiente() == null ? null : ctx.pendiente().payload();
        if (previo != null && esPayloadRural(previo)) {
            return registrarVentaRuralFlujo.interpretar(ctx);
        }

        // Si se estaba aclarando un producto, la frase es la respuesta: se reinterpreta el texto completo.
        String textoOriginal = ctx.texto();
        Payload anterior = previo instanceof Payload p ? p : null;
        if (previo instanceof PendienteProducto aclaracion) {
            textoOriginal = aclaracion.completar(ctx.texto());
            if (textoOriginal == null) {
                return error("No pude aplicar tu respuesta. Repite el comando completo.");
            }
            anterior = (Payload) aclaracion.anterior();
        }

        String texto = NormalizadorVoz.normalizar(textoOriginal);
        if (ES_RURAL.matcher(texto).find()) {
            return registrarVentaRuralFlujo.interpretar(ctx);
        }

        CanalVenta canal;
        MetodoPago metodoPago;
        if (anterior != null) {
            canal = anterior.canal();
            metodoPago = anterior.metodoPago();
        } else {
            canal = CanalVenta.VENTANILLA;
            metodoPago = texto.contains("transferencia") ? MetodoPago.TRANSFERENCIA : MetodoPago.EFECTIVO;
        }

        List<ItemVozParser.ItemExtraido> extraidos = extraerItems(texto);
        if (extraidos.isEmpty()) {
            return error(ItemVozParser.explicarFalta(texto, EJEMPLO));
        }

        // productoId -> {cajas, unidades}; parte de lo ya pendiente y suma lo nuevo.
        Map<UUID, int[]> cantidades = new LinkedHashMap<>();
        if (anterior != null) {
            for (ItemVentaRequest item : anterior.items()) {
                cantidades.put(item.productoId(), new int[]{item.cajas(), item.unidades()});
            }
        }
        Map<UUID, String> nombresDichos = new HashMap<>();
        Map<UUID, BigDecimal> preciosDichos = new HashMap<>();

        for (ItemVozParser.ItemExtraido extraido : extraidos) {
            List<ResolvedorProducto.ProductoCandidato> candidatos =
                    resolvedorProducto.resolver(extraido.fragmentoProducto());

            if (candidatos.isEmpty()) {
                return error("No encontré el producto \"" + extraido.fragmentoProducto() + "\". ¿Puedes repetirlo?");
            }
            if (esAmbiguo(candidatos)) {
                // ok=true: queda el contexto guardado y el usuario responde solo cuál producto es.
                return new VozResultado(true,
                        "¿" + candidatos.get(0).nombre() + " o " + candidatos.get(1).nombre() + "?",
                        Map.of("requiereAclaracion", true,
                                "opciones", List.of(candidatos.get(0).nombre(), candidatos.get(1).nombre())),
                        new PendienteProducto(textoOriginal, extraido.fragmentoProducto(), false, anterior));
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

        Map<UUID, CatalogoVentaDTO> catalogo = ventaService.getCatalogo().stream()
                .collect(Collectors.toMap(CatalogoVentaDTO::id, Function.identity()));

        List<ItemVentaRequest> items = new ArrayList<>();
        List<ItemCalculoRequest> itemsCalculo = new ArrayList<>();

        for (Map.Entry<UUID, int[]> entrada : cantidades.entrySet()) {
            UUID productoId = entrada.getKey();
            int cajas = entrada.getValue()[0];
            int unidades = entrada.getValue()[1];
            if (cajas == 0 && unidades == 0) continue;
            CatalogoVentaDTO producto = catalogo.get(productoId);

            if (producto == null) {
                String nombre = nombresDichos.getOrDefault(productoId, productoId.toString());
                return error("No encontré el producto \"" + nombre + "\". ¿Puedes repetirlo?");
            }

            int upC = producto.unidadesPorCaja() != null ? producto.unidadesPorCaja() : 0;
            int solicitadas = cajas * upC + unidades;
            if (producto.stock() < solicitadas) {
                return error("Stock insuficiente para " + producto.nombre()
                        + ". Disponible: " + producto.stock() + ", solicitado: " + solicitadas);
            }

            items.add(new ItemVentaRequest(productoId, cajas, unidades));
            itemsCalculo.add(new ItemCalculoRequest(productoId, cajas, unidades));
        }

        CalcularVentaResponse calculo = ventaService.calcularVenta(new CalcularVentaRequest(itemsCalculo));
        Map<UUID, BigDecimal> subtotales = calculo.items().stream()
                .collect(Collectors.toMap(ItemCalculoResultado::productoId, ItemCalculoResultado::subtotal));

        List<Map<String, Object>> datosItems = new ArrayList<>();
        List<String> descripciones = new ArrayList<>();
        List<String> avisosPrecio = new ArrayList<>();

        for (ItemVentaRequest item : items) {
            CatalogoVentaDTO producto = catalogo.get(item.productoId());
            datosItems.add(Map.of(
                    "productoId", item.productoId(),
                    "nombre", producto.nombre(),
                    "cajas", item.cajas(),
                    "unidades", item.unidades(),
                    "subtotal", subtotales.get(item.productoId())));
            descripciones.add(describir(item.cajas(), item.unidades()) + " de " + producto.nombre());

            // confirmarVenta siempre usa el precio de catálogo: el dicho solo se avisa.
            BigDecimal precioDicho = preciosDichos.get(item.productoId());
            if (precioDicho != null && precioDicho.compareTo(producto.precioUnitario()) != 0) {
                avisosPrecio.add(" El precio de catálogo de " + producto.nombre() + " es "
                        + sinCeros(producto.precioUnitario()) + " y se usará ese.");
            }
        }

        String textoRespuesta = "Venta " + canal.name().toLowerCase() + ": " + String.join(" y ", descripciones)
                + ". Total $" + sinCeros(calculo.total()) + ". Pago: " + metodoPago.name().toLowerCase() + "."
                + String.join("", avisosPrecio) + " ¿Confirmas?";

        Map<String, Object> datos = Map.of(
                "canal", canal.name(),
                "items", datosItems,
                "total", calculo.total(),
                "metodoPago", metodoPago.name());

        return new VozResultado(true, textoRespuesta, datos, new Payload(canal, metodoPago, items));
    }

    @Override
    @RequiereRol({"ADMINISTRADOR", "ENCARGADO_VENTAS"})
    public VozResultado ejecutar(VozPendiente pendiente, Usuario usuario) {
        if (esPayloadRural(pendiente.payload())) {
            return registrarVentaRuralFlujo.ejecutar(pendiente, usuario);
        }
        if (!(pendiente.payload() instanceof Payload p)) {
            return error(MENSAJE_FALTA_PRODUCTO);
        }

        // Si el stock cambió desde interpretar, StockInsuficienteException sube tal cual (422).
        ConfirmarVentaResponse r = ventaService.confirmarVenta(
                new ConfirmarVentaRequest(p.canal(), p.metodoPago(), p.items()), usuario);

        return new VozResultado(true, "Venta registrada por $" + sinCeros(r.total()) + ".",
                Map.of("ventaId", r.ventaId(), "total", r.total()), null);
    }

    private static boolean esPayloadRural(Object payload) {
        return payload instanceof RegistrarVentaRuralFlujo.Payload
                || payload instanceof RegistrarVentaRuralFlujo.PendienteDestinatario
                || (payload instanceof PendienteProducto p && p.rural());
    }

    private VozResultado error(String mensaje) {
        return new VozResultado(false, mensaje, Map.of(), null);
    }

    /** Ambiguo si el segundo candidato queda a menos de 0.10 del primero. */
    private boolean esAmbiguo(List<ResolvedorProducto.ProductoCandidato> candidatos) {
        return candidatos.size() >= 2 && (candidatos.get(0).score() - candidatos.get(1).score()) < 0.10;
    }

    private List<ItemVozParser.ItemExtraido> extraerItems(String textoNormalizado) {
        return ItemVozParser.extraer(ITEM_PATTERN, textoNormalizado);
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