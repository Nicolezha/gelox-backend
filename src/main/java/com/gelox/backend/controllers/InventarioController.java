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
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
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
    // RF21 — POST /api/inventario/pedidos
    // Crear pedido al proveedor + descargar Excel Nutresa
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Registra el pedido con estado PENDIENTE y devuelve el archivo Excel
     * con formato Nutresa listo para subir al portal del proveedor.
     *
     * El UUID del pedido creado se incluye en el header {@code X-Pedido-Id}.
     *
     * Roles: ENCARGADO_INVENTARIO, ADMINISTRADOR.
     */
    @PostMapping("/pedidos")
    public ResponseEntity<byte[]> crearPedido(
            @RequestBody @Valid CrearPedidoRequest req,
            @AuthenticationPrincipal Usuario usuario) {

        Map<String, Object> resultado = pedidoService.crearPedido(req, usuario);

        byte[] excel    = (byte[]) resultado.get("excel");
        String pedidoId = resultado.get("pedidoId").toString();
        String nombreArchivo = "pedido-nutresa-" +
                LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")) + ".xlsx";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentDisposition(
                ContentDisposition.attachment().filename(nombreArchivo).build());
        headers.add("X-Pedido-Id", pedidoId);
        // Permitir que el frontend lea el header personalizado
        headers.add("Access-Control-Expose-Headers", "X-Pedido-Id, Content-Disposition");

        return ResponseEntity.ok()
                .headers(headers)
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(excel);
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
