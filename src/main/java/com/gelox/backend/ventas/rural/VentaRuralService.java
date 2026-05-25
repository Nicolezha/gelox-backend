package com.gelox.backend.ventas.rural;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gelox.backend.entities.*;
import com.gelox.backend.exceptions.StockInsuficienteException;
import com.gelox.backend.repositories.ItemVentaRepository;
import com.gelox.backend.repositories.ProductoRepository;
import com.gelox.backend.repositories.VentaRepository;
import com.gelox.backend.security.RequiereRol;
import com.gelox.backend.services.InventarioService;
import com.gelox.backend.ventas.rural.dto.ConfirmarPedidoRuralRequest;
import com.gelox.backend.ventas.rural.dto.ItemPedidoRuralRequest;
import com.gelox.backend.ventas.rural.dto.PedidoRuralResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

/**
 * RF32 — Lógica de negocio para pedidos rurales.
 *
 * Constante UNIDADES_POR_CAJA = 24:
 *   La tabla 'producto' no tiene una columna 'unidades_por_caja' en BD.
 *   Se usa este valor fijo como convención del negocio. Si en el futuro
 *   se agrega dicha columna, reemplazar la constante por producto.getUnidadesPorCaja().
 */
@Service
@RequiredArgsConstructor
@Transactional
public class VentaRuralService {

    // Convención de negocio: una caja contiene 24 unidades.
    // TODO: mover a producto.unidades_por_caja cuando se agregue ese campo a BD.
    private static final int UNIDADES_POR_CAJA = 24;

    private final VentaRepository        ventaRepository;
    private final VentaRuralRepository   pedidoRuralRepository;
    private final ClienteRuralRepository clienteRuralRepository;
    private final ItemVentaRepository    itemVentaRepository;
    private final ProductoRepository     productoRepository;
    private final InventarioService      inventarioService;   // RF26
    private final ObjectMapper           objectMapper;

    // ─────────────────────────── RF32 — GET ──────────────────────────

    @RequiereRol({"ADMINISTRADOR", "ENCARGADO_VENTAS"})
    @Transactional(readOnly = true)
    public List<PedidoRuralResponse> listarPedidos(String estadoEnvioParam, int page, int size) {
        int safeSize = Math.min(size, 100);
        Pageable pageable = PageRequest.of(page, safeSize);

        Page<PedidoRural> resultPage;

        if (estadoEnvioParam != null && !estadoEnvioParam.isBlank()) {
            EstadoEnvio estado = parsearEstadoEnvio(estadoEnvioParam);
            resultPage = pedidoRuralRepository.findByEstadoEnvio(estado, pageable);
        } else {
            resultPage = pedidoRuralRepository.findAllWithVenta(pageable);
        }

        return resultPage.getContent().stream()
                .map(pr -> toDTOListado(pr))
                .toList();
    }

    // ─────────────────────────── RF32 — POST ─────────────────────────

    @RequiereRol({"ADMINISTRADOR", "ENCARGADO_VENTAS"})
    public PedidoRuralResponse confirmarPedidoRural(ConfirmarPedidoRuralRequest req, Usuario usuario) {

        // ── 0. Validar que hay al menos un ítem con cantidad > 0 ──────────
        boolean hayItems = req.items().stream()
                .anyMatch(i -> i.cajas() > 0 || i.unidades() > 0);
        if (!hayItems) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Cada ítem debe tener al menos una unidad o caja > 0");
        }

        // ── 1. Leer todos los productos con bloqueo pesimista ─────────────
        List<UUID> ids = req.items().stream()
                .map(ItemPedidoRuralRequest::productoId)
                .toList();

        Map<UUID, Producto> productosMap = productoRepository.findByIdInWithLock(ids)
                .stream()
                .collect(Collectors.toMap(Producto::getId, p -> p));

        // ── 2. Validar existencia y activo ────────────────────────────────
        for (UUID id : ids) {
            Producto p = productosMap.get(id);
            if (p == null || !p.getActivo()) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Producto no encontrado: " + id);
            }
        }

        // ── 3. Validar stock antes de escribir nada ───────────────────────
        for (ItemPedidoRuralRequest item : req.items()) {
            int totalUnidades = (item.cajas() * UNIDADES_POR_CAJA) + item.unidades();
            if (totalUnidades == 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "El ítem para el producto " + item.productoId()
                        + " debe tener al menos una unidad o caja > 0");
            }
            Producto p = productosMap.get(item.productoId());
            if (p.getStockActual() < totalUnidades) {
                throw new StockInsuficienteException("Stock insuficiente para " + p.getNombre());
            }
        }

        // ── 4. Resolver datos del destinatario ────────────────────────────
        ClienteRural clienteRural = null;
        String nombreDestinatario;
        String telefonoDestinatario;
        String direccionDestinatario;
        String corregimientoDestinatario;

        if (req.clienteRuralId() != null) {
            clienteRural = clienteRuralRepository.findById(req.clienteRuralId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                            "Cliente rural no encontrado: " + req.clienteRuralId()));
            nombreDestinatario       = clienteRural.getNombre();
            telefonoDestinatario     = clienteRural.getTelefono();
            direccionDestinatario    = clienteRural.getDireccion();
            corregimientoDestinatario = clienteRural.getCorregimiento();
        } else {
            if (req.nombreDestinatario() == null || req.nombreDestinatario().isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Debe indicar clienteRuralId o nombreDestinatario");
            }
            nombreDestinatario       = req.nombreDestinatario();
            telefonoDestinatario     = req.telefonoDestinatario();
            direccionDestinatario    = req.direccionDestinatario();
            corregimientoDestinatario = req.corregimiento();
        }

        // Serializar datos_destinatario como JSON
        String datosDestinatarioJson = serializarDestinatario(
                nombreDestinatario, telefonoDestinatario,
                direccionDestinatario, corregimientoDestinatario);

        // ── 5. Calcular totales ───────────────────────────────────────────
        BigDecimal costoEnvio  = req.costoEnvioEfectivo();
        BigDecimal subtotalItems = BigDecimal.ZERO;

        record CalcItem(ItemPedidoRuralRequest req, Producto producto, int totalUnidades,
                        BigDecimal subtotal) {}

        List<CalcItem> calcItems = new ArrayList<>();
        for (ItemPedidoRuralRequest item : req.items()) {
            int totalUnidades = (item.cajas() * UNIDADES_POR_CAJA) + item.unidades();
            BigDecimal subtotal = productosMap.get(item.productoId()).getPrecioVenta()
                    .multiply(BigDecimal.valueOf(totalUnidades))
                    .setScale(2, RoundingMode.HALF_UP);
            calcItems.add(new CalcItem(item, productosMap.get(item.productoId()), totalUnidades, subtotal));
            subtotalItems = subtotalItems.add(subtotal);
        }

        BigDecimal totalVenta = subtotalItems.add(costoEnvio).setScale(2, RoundingMode.HALF_UP);

        // ── 6. Insertar Venta ─────────────────────────────────────────────
        Venta venta = Venta.builder()
                .canal(CanalVenta.RURAL)
                .estado(EstadoVenta.COMPLETADA)
                .total(totalVenta)
                .usuario(usuario)
                .clienteRural(clienteRural)
                .build();
        venta = ventaRepository.save(venta);

        // ── 7. Insertar ítems, descontar stock y registrar movimientos ────
        List<PedidoRuralResponse.ItemPedidoRuralResponse> itemsResponse = new ArrayList<>();

        for (CalcItem ci : calcItems) {
            Producto p = ci.producto();
            ItemPedidoRuralRequest item = ci.req();

            itemVentaRepository.save(ItemVenta.builder()
                    .venta(venta)
                    .producto(p)
                    .cantidadCajas(item.cajas())
                    .cantidadUnidades(item.unidades())
                    .precioUnitario(p.getPrecioVenta())
                    .subtotal(ci.subtotal())
                    .build());

            // RF26 — descuento centralizado: actualiza stock + crea movimiento_inventario
            inventarioService.descontarStock(
                    p.getId(),
                    ci.totalUnidades(),
                    TipoMovimiento.SALIDA_VENTA,
                    "Pedido rural - " + nombreDestinatario,
                    usuario
            );

            itemsResponse.add(new PedidoRuralResponse.ItemPedidoRuralResponse(
                    p.getId(),
                    p.getNombre(),
                    item.cajas(),
                    item.unidades(),
                    ci.totalUnidades(),
                    p.getPrecioVenta(),
                    ci.subtotal()
            ));
        }

        // ── 8. Insertar PedidoRural ───────────────────────────────────────
        PedidoRural pedidoRural = PedidoRural.builder()
                .venta(venta)
                .costoEnvio(costoEnvio)
                .estadoEnvio(EstadoEnvio.PENDIENTE)
                .datosDestinatario(datosDestinatarioJson)
                .build();
        pedidoRural = pedidoRuralRepository.save(pedidoRural);

        // ── 9. Construir respuesta ────────────────────────────────────────
        Map<String, String> destinatarioMap = new LinkedHashMap<>();
        destinatarioMap.put("nombre",        nombreDestinatario);
        destinatarioMap.put("telefono",      telefonoDestinatario);
        destinatarioMap.put("direccion",     direccionDestinatario);
        destinatarioMap.put("corregimiento", corregimientoDestinatario);

        return new PedidoRuralResponse(
                venta.getId(),
                pedidoRural.getId(),
                venta.getFecha(),
                totalVenta,
                costoEnvio,
                pedidoRural.getEstadoEnvio().name(),
                destinatarioMap,
                itemsResponse
        );
    }

    // ─────────────────────────── Helpers ─────────────────────────────

    /** Convierte un PedidoRural a DTO de listado (sin detalle de ítems). */
    private PedidoRuralResponse toDTOListado(PedidoRural pr) {
        Map<String, String> destinatario = parsearDestinatario(pr.getDatosDestinatario());
        return new PedidoRuralResponse(
                pr.getVenta().getId(),
                pr.getId(),
                pr.getVenta().getFecha(),
                pr.getVenta().getTotal(),
                pr.getCostoEnvio(),
                pr.getEstadoEnvio().name(),
                destinatario,
                null   // el listado no incluye detalle de ítems
        );
    }

    /** Serializa los datos del destinatario como JSON string. */
    private String serializarDestinatario(String nombre, String telefono,
                                          String direccion, String corregimiento) {
        Map<String, String> datos = new LinkedHashMap<>();
        datos.put("nombre",        nombre);
        datos.put("telefono",      telefono);
        datos.put("direccion",     direccion);
        datos.put("corregimiento", corregimiento);
        try {
            return objectMapper.writeValueAsString(datos);
        } catch (JsonProcessingException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Error al serializar datos del destinatario");
        }
    }

    /** Deserializa el JSON de datos_destinatario. Devuelve mapa vacío si falla. */
    @SuppressWarnings("unchecked")
    private Map<String, String> parsearDestinatario(String json) {
        if (json == null || json.isBlank()) return Map.of();
        try {
            return objectMapper.readValue(json, Map.class);
        } catch (JsonProcessingException e) {
            return Map.of("nombre", json); // fallback: texto libre
        }
    }

    private EstadoEnvio parsearEstadoEnvio(String valor) {
        try {
            return EstadoEnvio.valueOf(valor.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "estadoEnvio inválido. Use: PENDIENTE, ENVIADO o ENTREGADO");
        }
    }
}
