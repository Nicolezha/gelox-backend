package com.gelox.backend.voz.handlers;

import com.gelox.backend.entities.Producto;
import com.gelox.backend.entities.TipoIntencionVoz;
import com.gelox.backend.entities.Usuario;
import com.gelox.backend.repositories.ProductoRepository;
import com.gelox.backend.security.RequiereRol;
import com.gelox.backend.voz.IntencionHandler;
import com.gelox.backend.voz.NormalizadorVoz;
import com.gelox.backend.voz.ResolvedorProducto;
import com.gelox.backend.voz.VozContexto;
import com.gelox.backend.voz.VozPendiente;
import com.gelox.backend.voz.VozResultado;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * RF49 — "¿cuántas cajas de Festival me quedan?" y "qué productos están por
 * bajar del mínimo". Consulta directa (no requiere confirmación salvo por
 * baja confianza, RNF-5). No usa {@code InventarioService.listarAlertas()}
 * porque exige ADMINISTRADOR/ENCARGADO_INVENTARIO y RF49 también sirve al
 * encargado de ventas.
 */
@Component
@RequiredArgsConstructor
public class ConsultaInventarioHandler implements IntencionHandler {

    private static final Pattern PRODUCTO_PATTERN = Pattern.compile("\\bde\\s+(.+)$");
    private static final Pattern RELLENO_PRODUCTO =
            Pattern.compile("\\b(me|quedan|quedaron|hay|tengo|disponibles)\\b");

    private final ResolvedorProducto resolvedorProducto;
    private final ProductoRepository productoRepository;

    /** {@code fragmentoProducto} null significa "listar productos bajo el mínimo". */
    private record Payload(String fragmentoProducto) {}

    @Override
    public TipoIntencionVoz tipo() {
        return TipoIntencionVoz.CONSULTAR_INVENTARIO;
    }

    @Override
    public boolean requiereConfirmacion() {
        return false;
    }

    @Override
    @RequiereRol({"ADMINISTRADOR", "ENCARGADO_INVENTARIO", "ENCARGADO_VENTAS"})
    public VozResultado interpretar(VozContexto ctx) {
        String texto = NormalizadorVoz.normalizar(ctx.texto());
        Optional<String> fragmentoProducto = extraerProducto(texto);

        if (fragmentoProducto.isPresent()) {
            return consultarProducto(fragmentoProducto.get());
        }
        return consultarBajoStock();
    }

    @Override
    @RequiereRol({"ADMINISTRADOR", "ENCARGADO_INVENTARIO", "ENCARGADO_VENTAS"})
    public VozResultado ejecutar(VozPendiente pendiente, Usuario usuario) {
        Payload payload = (Payload) pendiente.payload();
        if (payload.fragmentoProducto() == null) {
            return consultarBajoStock();
        }
        return consultarProducto(payload.fragmentoProducto());
    }

    private VozResultado consultarProducto(String fragmento) {
        List<ResolvedorProducto.ProductoCandidato> candidatos = resolvedorProducto.resolver(fragmento);

        if (candidatos.isEmpty()) {
            return new VozResultado(false, "No encontré ese producto.", Map.of(), null);
        }
        if (esAmbiguo(candidatos)) {
            return new VozResultado(false,
                    "¿" + candidatos.get(0).nombre() + " o " + candidatos.get(1).nombre() + "?",
                    Map.of(), null);
        }

        Producto producto = productoRepository.findById(candidatos.get(0).id())
                .orElseThrow(() -> new IllegalStateException("Producto resuelto pero no encontrado"));

        Map<String, Object> datosProducto = productoADatos(producto);
        boolean bajoStock = "BAJO_STOCK".equals(datosProducto.get("estado"));

        String texto = "Quedan " + narracionCantidad(producto) + " de " + producto.getNombre() + ".";
        if (bajoStock) {
            texto += " Atención: está por debajo del mínimo (" + producto.getStockMinimo() + ").";
        }

        Map<String, Object> datos = new LinkedHashMap<>();
        datos.put("productos", List.of(datosProducto));
        datos.put("alertas", bajoStock ? List.of(alertaDeProducto(producto)) : List.of());

        return new VozResultado(true, texto, datos, new Payload(fragmento));
    }

    private VozResultado consultarBajoStock() {
        List<Producto> productosBajoStock = productoRepository.findProductosBajoStock();

        List<Map<String, Object>> datosProductos = productosBajoStock.stream()
                .map(this::productoADatos)
                .toList();
        List<Map<String, Object>> alertas = productosBajoStock.stream()
                .map(this::alertaDeProducto)
                .toList();

        String texto;
        if (productosBajoStock.isEmpty()) {
            texto = "No hay productos por debajo del mínimo.";
        } else {
            List<String> primeros = productosBajoStock.stream().limit(5).map(Producto::getNombre).toList();
            texto = "Los productos por debajo del mínimo son: " + String.join(", ", primeros);
            if (productosBajoStock.size() > 5) {
                texto += " y " + (productosBajoStock.size() - 5) + " más";
            }
            texto += ".";
        }

        Map<String, Object> datos = new LinkedHashMap<>();
        datos.put("productos", datosProductos);
        datos.put("alertas", alertas);

        return new VozResultado(true, texto, datos, new Payload(null));
    }

    private Map<String, Object> productoADatos(Producto p) {
        boolean bajoStock = p.getStockActual() <= p.getStockMinimo();
        Integer unidadesPorCaja = p.getUnidadesPorCaja();

        Map<String, Object> datos = new LinkedHashMap<>();
        datos.put("id", p.getId());
        datos.put("nombre", p.getNombre());
        datos.put("stockActual", p.getStockActual());
        datos.put("stockMinimo", p.getStockMinimo());
        if (unidadesPorCaja != null && unidadesPorCaja > 0) {
            datos.put("cajas", p.getStockActual() / unidadesPorCaja);
            datos.put("unidadesSueltas", p.getStockActual() % unidadesPorCaja);
        } else {
            datos.put("cajas", null);
            datos.put("unidadesSueltas", p.getStockActual());
        }
        datos.put("estado", bajoStock ? "BAJO_STOCK" : "NORMAL");
        return datos;
    }

    private Map<String, Object> alertaDeProducto(Producto p) {
        Map<String, Object> alerta = new LinkedHashMap<>();
        alerta.put("nombre", p.getNombre());
        alerta.put("stockActual", p.getStockActual());
        alerta.put("stockMinimo", p.getStockMinimo());
        return alerta;
    }

    /** "N cajas y M unidades" si el producto tiene unidadesPorCaja; si no, solo unidades. */
    private String narracionCantidad(Producto p) {
        Integer unidadesPorCaja = p.getUnidadesPorCaja();
        if (unidadesPorCaja == null || unidadesPorCaja <= 0) {
            return p.getStockActual() + " unidades";
        }
        int cajas = p.getStockActual() / unidadesPorCaja;
        int sueltas = p.getStockActual() % unidadesPorCaja;
        return cajas + " cajas y " + sueltas + " unidades";
    }

    /** Ambiguo si el segundo candidato queda a menos de 0.10 del primero. */
    private boolean esAmbiguo(List<ResolvedorProducto.ProductoCandidato> candidatos) {
        return candidatos.size() >= 2 && (candidatos.get(0).score() - candidatos.get(1).score()) < 0.10;
    }

    /** "cuántas cajas de festival me quedan" → "festival" (se descartan los rellenos). */
    private Optional<String> extraerProducto(String textoNormalizado) {
        Matcher m = PRODUCTO_PATTERN.matcher(textoNormalizado);
        if (!m.find()) return Optional.empty();

        String fragmento = RELLENO_PRODUCTO.matcher(m.group(1)).replaceAll("")
                .replaceAll("[^a-z0-9 ]", "")
                .replaceAll("\\s+", " ").trim();
        return fragmento.isEmpty() ? Optional.empty() : Optional.of(fragmento);
    }
}
