package com.gelox.backend.controllers;

import com.gelox.backend.catalogo.dto.PagedResponse;
import com.gelox.backend.dto.*;
import com.gelox.backend.entities.Usuario;
import com.gelox.backend.services.InventarioService;
import com.gelox.backend.services.PedidoProveedorService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/inventario")
@RequiredArgsConstructor
public class InventarioController {

    private final InventarioService       inventarioService;
    private final PedidoProveedorService  pedidoService;

    // ──────────────────────────────────────────────────────────────────────
    // GET /api/inventario/productos — lista de stock (existente)
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Roles: ADMINISTRADOR, ENCARGADO_INVENTARIO.
     * precioCosto solo se expone al ADMINISTRADOR.
     */
    @GetMapping("/productos")
    public ResponseEntity<List<InventarioProductoDTO>> listarInventario(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String estado,
            @AuthenticationPrincipal Usuario usuario) {

        return ResponseEntity.ok(
                inventarioService.listarInventario(usuario.getRol().name(), q, estado));
    }

    // ──────────────────────────────────────────────────────────────────────
    // RF24 — GET /api/inventario/alertas
    // Referencias con stockActual <= stockMinimo
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Roles: ADMINISTRADOR, ENCARGADO_INVENTARIO.
     * Retorna lista vacía cuando no hay productos en BAJO_STOCK.
     */
    @GetMapping("/alertas")
    public ResponseEntity<List<AlertaStockDTO>> listarAlertas() {
        return ResponseEntity.ok(inventarioService.listarAlertas());
    }

    // ──────────────────────────────────────────────────────────────────────
    // GET /api/inventario/muerto?dias=30 — productos con stock sin salidas
    // ──────────────────────────────────────────────────────────────────────

    /** Roles: ADMINISTRADOR, ENCARGADO_INVENTARIO. valorInmovilizado solo para ADMINISTRADOR. */
    @GetMapping("/muerto")
    public ResponseEntity<List<InventarioMuertoDTO>> listarInventarioMuerto(
            @RequestParam(defaultValue = "30") int dias,
            @AuthenticationPrincipal Usuario usuario) {
        return ResponseEntity.ok(
                inventarioService.listarInventarioMuerto(dias, usuario.getRol().name()));
    }

    // ──────────────────────────────────────────────────────────────────────
    // RF21 — POST /api/inventario/pedidos
    // Crear pedido al proveedor + descargar Excel Nutresa
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Registra un pedido PENDIENTE por proveedor (RF63) y devuelve su archivo con la plantilla de
     * cada proveedor (RF67): con un solo proveedor, el .xlsx; con varios, un .zip con un .xlsx por pedido.
     *
     * <p>Encabezados: {@code X-Pedido-Id} (el primero), {@code X-Pedidos-Ids} (todos, separados por
     * coma), {@code X-Advertencias-Monto-Minimo} (pedidos bajo el monto mínimo de su proveedor: se crean
     * igual) y los avisos de exportación; los textos van separados por «|» y codificados para URL.</p>
     *
     * Roles: ENCARGADO_INVENTARIO, ADMINISTRADOR.
     */
    @PostMapping("/pedidos")
    public ResponseEntity<byte[]> crearPedido(
            @RequestBody @Valid CrearPedidoRequest req,
            @AuthenticationPrincipal Usuario usuario) {

        List<PedidoProveedorService.PedidoCreado> pedidos = pedidoService.crearPedido(req, usuario);

        HttpHeaders headers = new HttpHeaders();
        headers.add("X-Pedido-Id", pedidos.get(0).pedidoId().toString());
        headers.add("X-Pedidos-Ids", pedidos.stream()
                .map(p -> p.pedidoId().toString()).collect(java.util.stream.Collectors.joining(",")));
        avisar(headers, "X-Advertencias-Monto-Minimo", pedidos.stream()
                .map(PedidoProveedorService.PedidoCreado::advertenciaMontoMinimo)
                .filter(java.util.Objects::nonNull).toList());
        avisar(headers, "X-Productos-Sin-Codigo", pedidos.stream()
                .flatMap(p -> p.productosSinCodigo().stream()).toList());
        avisar(headers, "X-Productos-No-Ubicados", pedidos.stream()
                .flatMap(p -> p.productosNoUbicados().stream()).toList());
        // Permitir que el frontend lea los encabezados propios
        headers.add("Access-Control-Expose-Headers", "X-Pedido-Id, X-Pedidos-Ids, X-Advertencias-Monto-Minimo, "
                + "X-Productos-Sin-Codigo, X-Productos-No-Ubicados, Content-Disposition");

        if (pedidos.size() == 1) {
            headers.setContentDisposition(
                    ContentDisposition.attachment().filename(pedidos.get(0).nombreArchivo()).build());
            return ResponseEntity.ok()
                    .headers(headers)
                    .contentType(MediaType.parseMediaType(
                            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    .body(pedidos.get(0).excel());
        }

        headers.setContentDisposition(ContentDisposition.attachment()
                .filename("Pedidos_" + LocalDate.now(java.time.ZoneId.of("America/Bogota")) + ".zip").build());
        return ResponseEntity.ok()
                .headers(headers)
                .contentType(MediaType.parseMediaType("application/zip"))
                .body(comprimir(pedidos));
    }

    /** Un .zip con el archivo de cada pedido; nombres repetidos (mismo proveedor y día) llevan sufijo. */
    public static byte[] comprimir(List<PedidoProveedorService.PedidoCreado> pedidos) {
        java.io.ByteArrayOutputStream salida = new java.io.ByteArrayOutputStream();
        java.util.Set<String> usados = new java.util.HashSet<>();
        try (java.util.zip.ZipOutputStream zip = new java.util.zip.ZipOutputStream(salida)) {
            for (PedidoProveedorService.PedidoCreado p : pedidos) {
                String nombre = p.nombreArchivo();
                for (int n = 2; !usados.add(nombre); n++) {
                    nombre = p.nombreArchivo().replaceFirst("\\.xlsx$", "") + "_" + n + ".xlsx";
                }
                zip.putNextEntry(new java.util.zip.ZipEntry(nombre));
                zip.write(p.excel());
                zip.closeEntry();
            }
        } catch (java.io.IOException e) {
            throw new IllegalStateException("No se pudo comprimir los pedidos", e);
        }
        return salida.toByteArray();
    }

    // ──────────────────────────────────────────────────────────────────────
    // RF21 — GET /api/inventario/pedidos
    // Historial paginado de pedidos al proveedor
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Lista los pedidos al proveedor con filtros y paginación.
     *
     * <p>Query params:</p>
     * <ul>
     *   <li>{@code page}    — página base-1 (default 1)</li>
     *   <li>{@code limit}   — registros por página (default 10)</li>
     *   <li>{@code q}       — búsqueda por UUID o texto en notas (opcional)</li>
     *   <li>{@code estado}  — PENDIENTE | RECIBIDO (opcional)</li>
     *   <li>{@code periodo} — 7d | 30d | mes (opcional)</li>
     * </ul>
     *
     * Roles: ENCARGADO_INVENTARIO, ADMINISTRADOR.
     */
    @GetMapping("/pedidos")
    public ResponseEntity<PagedResponse<PedidoResumenDTO>> listarPedidos(
            @RequestParam(defaultValue = "1")  int    page,
            @RequestParam(defaultValue = "10") int    limit,
            @RequestParam(required = false)    String q,
            @RequestParam(required = false)    String estado,
            @RequestParam(required = false)    String periodo) {

        return ResponseEntity.ok(
                pedidoService.listarPedidos(page, limit, q, estado, periodo));
    }

    // ──────────────────────────────────────────────────────────────────────
    // RF22 — GET /api/inventario/pedidos/{id}
    // Detalle de un pedido con sus ítems (pedido vs recibido)
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Retorna un pedido completo con todos sus ítems.
     * Usado en DetallePedido.jsx para mostrar la comparación solicitado / recibido.
     *
     * Roles: ENCARGADO_INVENTARIO, ADMINISTRADOR.
     */
    @GetMapping("/pedidos/{id}")
    public ResponseEntity<PedidoDetalleDTO> obtenerDetallePedido(@PathVariable UUID id) {
        return ResponseEntity.ok(pedidoService.obtenerDetallePedido(id));
    }

    // ──────────────────────────────────────────────────────────────────────
    // T47 — GET /api/inventario/pedidos/{id}/exportar
    // Reexportar el Excel Nutresa de un pedido ya creado
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Vuelve a descargar el Excel de un pedido existente (p. ej. uno creado
     * por voz, cuyo archivo no viaja en la respuesta de /api/voz/confirmar).
     *
     * Roles: ENCARGADO_INVENTARIO, ADMINISTRADOR.
     */
    @GetMapping("/pedidos/{id}/exportar")
    public ResponseEntity<byte[]> exportarPedido(@PathVariable UUID id) {
        PedidoProveedorService.ArchivoPedido archivo = pedidoService.exportarPedido(id);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentDisposition(
                ContentDisposition.attachment().filename(archivo.nombreArchivo()).build());
        // RF67: un producto sin código del proveedor no se omite; el archivo sale igual y se avisa aquí
        // (nombres separados por «|», codificados para URL).
        avisar(headers, "X-Productos-Sin-Codigo", archivo.productosSinCodigo());
        avisar(headers, "X-Productos-No-Ubicados", archivo.productosNoUbicados());

        return ResponseEntity.ok()
                .headers(headers)
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(archivo.contenido());
    }

    private static void avisar(HttpHeaders headers, String nombre, java.util.List<String> productos) {
        if (productos == null || productos.isEmpty()) return;
        headers.add(nombre, productos.stream()
                .map(p -> java.net.URLEncoder.encode(p, java.nio.charset.StandardCharsets.UTF_8))
                .collect(java.util.stream.Collectors.joining("|")));
    }

    // ──────────────────────────────────────────────────────────────────────
    // RF22 + RF23 — POST /api/inventario/entradas
    // Registrar entrada de mercancía (actualiza stock + compara con pedido)
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Registra el ingreso físico de mercancía:
     * <ul>
     *   <li>Suma cantidades al stock de cada producto (RF23).</li>
     *   <li>Registra movimiento_inventario tipo ENTRADA (RF23).</li>
     *   <li>Si se envía {@code pedidoId}: compara solicitado vs recibido,
     *       retorna CORRECTO / SOBRANTE / FALTANTE por producto (RF22)
     *       y cierra el pedido como RECIBIDO.</li>
     * </ul>
     *
     * Roles: ENCARGADO_INVENTARIO, ADMINISTRADOR.
     */
    @PostMapping("/entradas")
    public ResponseEntity<EntradaResponseDTO> registrarEntrada(
            @RequestBody @Valid RegistrarEntradaRequest req,
            @AuthenticationPrincipal Usuario usuario) {

        return ResponseEntity.ok(pedidoService.registrarEntrada(req, usuario));
    }

    // ──────────────────────────────────────────────────────────────────────
    // RF25 — POST /api/inventario/perdidas
    // Registrar pérdida de producto (no afecta utilidad)
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Registra la baja de unidades por pérdida (vencimiento, rotura, robo, etc.).
     * <ul>
     *   <li>Descuenta stock del producto.</li>
     *   <li>Crea registro en tabla {@code perdida}.</li>
     *   <li>Crea movimiento_inventario tipo PERDIDA.</li>
     *   <li><strong>No</strong> se computa en ingresos ni utilidad neta.</li>
     * </ul>
     *
     * Roles: ENCARGADO_INVENTARIO, ADMINISTRADOR.
     */
    @PostMapping("/perdidas")
    public ResponseEntity<PerdidaResponseDTO> registrarPerdida(
            @RequestBody @Valid RegistrarPerdidaRequest req,
            @AuthenticationPrincipal Usuario usuario) {

        return ResponseEntity.ok(pedidoService.registrarPerdida(req, usuario));
    }
}
