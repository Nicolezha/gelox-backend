package com.gelox.backend.services;

import com.gelox.backend.catalogo.dto.PagedResponse;
import com.gelox.backend.dto.*;
import com.gelox.backend.entities.*;
import com.gelox.backend.repositories.*;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.NoSuchElementException;

@Service
@RequiredArgsConstructor
@Transactional
public class PedidoProveedorService {

    private final PedidoProveedorRepository     pedidoRepo;
    private final ItemPedidoProveedorRepository  itemPedidoRepo;
    private final ProductoRepository            productoRepo;
    private final MovimientoInventarioRepository movimientoRepo;
    private final PerdidaRepository             perdidaRepo;
    private final EventoSistemaService          eventoService;

    @Value("classpath:templates/catalogo.xlsx")
    private Resource plantillaExcel;

    // ══════════════════════════════════════════════════════════════════════
    // RF21 — Crear pedido + generar Excel Nutresa
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Registra un pedido con estado PENDIENTE y genera el archivo Excel
     * con el formato de orden de compra requerido por Nutresa.
     *
     * @return mapa con "pedidoId" (UUID) y "excel" (byte[])
     */
    public Map<String, Object> crearPedido(CrearPedidoRequest req, Usuario usuarioActual) {

        // 1. Construir la entidad pedido
        PedidoProveedor pedido = PedidoProveedor.builder()
                .usuario(usuarioActual)
                .fecha(LocalDate.now())
                .estado(EstadoPedido.PENDIENTE)
                .notas(req.notas())
                .build();

        // 2. Resolver productos y construir ítems
        List<ItemPedidoProveedor> items = new ArrayList<>();
        for (ItemPedidoRequest ir : req.items()) {
            Producto producto = productoRepo.findById(ir.productoId())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Producto no encontrado: " + ir.productoId()));

            ItemPedidoProveedor item = ItemPedidoProveedor.builder()
                    .pedido(pedido)
                    .producto(producto)
                    .cantidadCajas(ir.cantidadCajas())
                    .cantidadUnidades(ir.cantidadUnidades())
                    .cantidadRecibida(0)
                    .precioUnitario(producto.getPrecioCosto())
                    .build();
            items.add(item);
        }
        pedido.setItems(items);

        // 3. Persistir
        PedidoProveedor guardado = pedidoRepo.save(pedido);

        // 4. Registrar evento en bitácora
        eventoService.registrarEvento(
                TipoEvento.PEDIDO_PROVEEDOR,
                String.format("Pedido #%s generado: %d referencias. Estado: PENDIENTE.",
                        guardado.getId().toString().substring(0, 8).toUpperCase(),
                        items.size()),
                usuarioActual.getId());

        // 5. Generar Excel llenando la plantilla catálogo
        byte[] excel = generarExcelDesdeTemplate(guardado);

        return Map.of("pedidoId", guardado.getId(), "excel", excel);
    }

    // ══════════════════════════════════════════════════════════════════════
    // RF22 + RF23 — Registrar entrada de mercancía
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Registra la recepción física de mercancía:
     * <ol>
     *   <li>Actualiza stock de cada producto recibido (RF23).</li>
     *   <li>Crea movimiento_inventario de tipo ENTRADA por cada producto (RF23).</li>
     *   <li>Si se proporcionó pedidoId, actualiza cantidades recibidas en el pedido,
     *       cierra el pedido (RECIBIDO) y retorna la comparación (RF22).</li>
     * </ol>
     */
    public EntradaResponseDTO registrarEntrada(RegistrarEntradaRequest req, Usuario usuarioActual) {

        List<EntradaResponseDTO.StockActualizadoDTO> stockActualizado = new ArrayList<>();
        List<ComparacionItemDTO> comparacion = null;

        // Mapa productoId → ítem del pedido (para comparación RF22)
        Map<UUID, ItemPedidoProveedor> itemsPedidoMap = new HashMap<>();
        PedidoProveedor pedido = null;

        if (req.pedidoId() != null) {
            pedido = pedidoRepo.findById(req.pedidoId())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Pedido no encontrado: " + req.pedidoId()));
            itemPedidoRepo.findByPedidoIdWithProducto(req.pedidoId())
                    .forEach(i -> itemsPedidoMap.put(i.getProducto().getId(), i));
            comparacion = new ArrayList<>();
        }

        // Procesar cada producto recibido
        for (ItemEntradaRequest ir : req.items()) {
            Producto producto = productoRepo.findById(ir.productoId())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Producto no encontrado: " + ir.productoId()));

            // RF23 — convertir cajas a unidades y actualizar stock
            int upC          = producto.getUnidadesPorCaja() != null ? producto.getUnidadesPorCaja() : 1;
            int totalEntrada = ir.cantidadUnidades() + ir.cantidadCajas() * upC;
            int stockAntes   = producto.getStockActual();
            int stockDespues = stockAntes + totalEntrada;
            producto.setStockActual(stockDespues);
            productoRepo.save(producto);

            // RF23 — registrar movimiento
            BigDecimal precio = (ir.precioUnitario() != null && ir.precioUnitario().compareTo(BigDecimal.ZERO) > 0)
                    ? ir.precioUnitario()
                    : producto.getPrecioCosto();

            movimientoRepo.save(MovimientoInventario.builder()
                    .producto(producto)
                    .usuario(usuarioActual)
                    .tipo(TipoMovimiento.ENTRADA)
                    .cantidad(totalEntrada)
                    .stockResultante(stockDespues)
                    .descripcion(String.format("Entrada de %d uds. (%d cajas × %d + %d sueltas). Precio: $%s",
                            totalEntrada, ir.cantidadCajas(), upC, ir.cantidadUnidades(), precio.toPlainString()))
                    .build());

            stockActualizado.add(new EntradaResponseDTO.StockActualizadoDTO(
                    producto.getId(), producto.getNombre(), stockDespues));

            // RF22 — comparar con el pedido si aplica
            if (pedido != null && comparacion != null) {
                ItemPedidoProveedor itemPedido = itemsPedidoMap.get(ir.productoId());
                if (itemPedido != null) {
                    itemPedido.setCantidadRecibida(totalEntrada);
                    itemPedido.setPrecioUnitario(precio);
                    itemPedidoRepo.save(itemPedido);

                    int totalSolicitado = (itemPedido.getCantidadCajas()    != null ? itemPedido.getCantidadCajas() * upC : 0)
                                       + (itemPedido.getCantidadUnidades() != null ? itemPedido.getCantidadUnidades()      : 0);
                    comparacion.add(ComparacionItemDTO.of(
                            producto.getId(),
                            producto.getCodigoTecnico(),
                            producto.getNombre(),
                            totalSolicitado,
                            totalEntrada));
                } else {
                    // Producto recibido que no estaba en el pedido → sobrante total
                    comparacion.add(ComparacionItemDTO.of(
                            producto.getId(),
                            producto.getCodigoTecnico(),
                            producto.getNombre(),
                            0,
                            totalEntrada));
                }
            }
        }

        // RF22 — agregar productos del pedido que no llegaron (faltantes totales)
        if (pedido != null && comparacion != null) {
                Set<UUID> recibidosIds = new HashSet<>();
                req.items().forEach(i -> recibidosIds.add(i.productoId()));

                final List<ComparacionItemDTO> comparacionFinal = comparacion; // ← aquí

                itemsPedidoMap.forEach((prodId, itemPedido) -> {
                    if (!recibidosIds.contains(prodId)) {
                        Producto p = itemPedido.getProducto();
                        int upCSol = p.getUnidadesPorCaja() != null ? p.getUnidadesPorCaja() : 1;
                        int totalSolicitado = (itemPedido.getCantidadCajas()    != null ? itemPedido.getCantidadCajas() * upCSol : 0)
                                           + (itemPedido.getCantidadUnidades() != null ? itemPedido.getCantidadUnidades()        : 0);
                        comparacionFinal.add(ComparacionItemDTO.of(
                        p.getId(), p.getCodigoTecnico(), p.getNombre(),
                        totalSolicitado, 0));
                    }
                });

                pedido.setEstado(EstadoPedido.RECIBIDO);
                pedidoRepo.save(pedido);
        }

        // Registrar evento en bitácora
        eventoService.registrarEvento(
                TipoEvento.ENTRADA_MERCANCIA,
                String.format("Entrada de mercancía registrada: %d referencias, %d unidades en total.",
                        req.items().size(),
                        req.items().stream().mapToInt(i -> i.cantidadUnidades() + i.cantidadCajas()).sum()),
                usuarioActual.getId());

        String mensaje = (pedido != null)
                ? "Entrada registrada y pedido cerrado como RECIBIDO."
                : "Entrada registrada directamente (sin pedido asociado).";

        return new EntradaResponseDTO(mensaje, req.pedidoId(), comparacion, stockActualizado);
    }

    // ══════════════════════════════════════════════════════════════════════
    // RF25 — Registrar pérdida de producto
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Registra una pérdida: descuenta stock y crea registro en tabla perdida.
     * <strong>No afecta ingresos ni utilidad neta.</strong>
     */
    public PerdidaResponseDTO registrarPerdida(RegistrarPerdidaRequest req, Usuario usuarioActual) {

        Producto producto = productoRepo.findById(req.productoId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Producto no encontrado: " + req.productoId()));

        int stockAntes = producto.getStockActual();

        if (req.cantidad() > stockAntes) {
            throw new IllegalArgumentException(
                    String.format("Stock insuficiente. Disponible: %d, solicitado: %d.",
                            stockAntes, req.cantidad()));
        }

        // Descontar stock
        int stockDespues = stockAntes - req.cantidad();
        producto.setStockActual(stockDespues);
        productoRepo.save(producto);

        // Crear registro en tabla perdida
        Perdida perdida = Perdida.builder()
                .producto(producto)
                .usuario(usuarioActual)
                .cantidad(req.cantidad())
                .motivo(req.motivo())
                .fecha(req.fecha() != null ? req.fecha() : LocalDate.now())
                .observaciones(req.observaciones())
                .build();
        Perdida guardada = perdidaRepo.save(perdida);

        // Registrar movimiento de inventario tipo PERDIDA
        movimientoRepo.save(MovimientoInventario.builder()
                .producto(producto)
                .usuario(usuarioActual)
                .tipo(TipoMovimiento.PERDIDA)
                .cantidad(req.cantidad())
                .stockResultante(stockDespues)
                .descripcion("Pérdida — motivo: " + req.motivo())
                .build());

        // Alerta solo cuando el producto TRANSICIONA a bajo stock (no si ya estaba en ese estado)
        if (stockAntes > producto.getStockMinimo() && stockDespues <= producto.getStockMinimo()) {
            eventoService.registrarEvento(
                    TipoEvento.ALERTA_STOCK,
                    String.format("La referencia %s (%s) alcanzó el stock mínimo configurado (%d uds.).",
                            producto.getNombre(), producto.getCodigoTecnico(), producto.getStockMinimo()),
                    usuarioActual.getId());
        }

        // Registrar evento de pérdida
        eventoService.registrarEvento(
                TipoEvento.PERDIDA_PRODUCTO,
                String.format("Pérdida registrada: %d uds. de %s. Motivo: %s.",
                        req.cantidad(), producto.getNombre(), req.motivo()),
                usuarioActual.getId());

        return new PerdidaResponseDTO(
                guardada.getId(),
                producto.getId(),
                producto.getCodigoTecnico(),
                producto.getNombre(),
                req.cantidad(),
                req.motivo(),
                guardada.getFecha(),
                req.observaciones(),
                stockAntes,
                stockDespues
        );
    }

    // ══════════════════════════════════════════════════════════════════════
    // Generación de Excel desde plantilla catálogo (RF21)
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Carga la plantilla {@code catalogo.xlsx}, localiza las columnas
     * "Producto/SKU" y "Cantidad", y escribe las cantidades del pedido
     * en cada fila cuyo SKU coincida con un codigoTecnico del carrito.
     * El resto de filas queda en blanco en la columna Cantidad.
     */
    private byte[] generarExcelDesdeTemplate(PedidoProveedor pedido) {

        // Clave compuesta "SKU|CJ" y "SKU|UN" para llenar la fila correcta en la plantilla
        Map<String, Integer> cantidades = new HashMap<>();
        for (ItemPedidoProveedor item : pedido.getItems()) {
            String sku = item.getProducto().getCodigoTecnico().trim();
            if (item.getCantidadCajas() != null && item.getCantidadCajas() > 0) {
                cantidades.put(sku + "|CJ", item.getCantidadCajas());
            }
            if (item.getCantidadUnidades() != null && item.getCantidadUnidades() > 0) {
                cantidades.put(sku + "|UN", item.getCantidadUnidades());
            }
        }

        try (InputStream tplStream = plantillaExcel.getInputStream();
             Workbook wb = new XSSFWorkbook(tplStream);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            Sheet sheet = wb.getSheetAt(0);

            // Buscar fila de encabezados y los índices de columna
            int colSku = -1, colUm = -1, colCantidad = -1, headerRow = -1;
            outer:
            for (Row row : sheet) {
                for (Cell cell : row) {
                    String val = cellText(cell).trim();
                    if (val.equalsIgnoreCase("Producto/SKU"))      { colSku      = cell.getColumnIndex(); headerRow = row.getRowNum(); }
                    if (val.equalsIgnoreCase("Unidad de Medida"))  { colUm       = cell.getColumnIndex(); }
                    if (val.equalsIgnoreCase("Cantidad"))          { colCantidad = cell.getColumnIndex(); }
                    if (colSku >= 0 && colUm >= 0 && colCantidad >= 0) break outer;
                }
            }

            if (colSku < 0 || colCantidad < 0) {
                throw new RuntimeException(
                        "La plantilla catálogo.xlsx no contiene los encabezados 'Producto/SKU' y 'Cantidad'");
            }

            // Llenar/limpiar columna Cantidad fila a fila
            for (int i = headerRow + 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;

                String sku = cellText(row.getCell(colSku)).trim();
                if (sku.isBlank()) continue;

                // Normalizar unidad de medida: CAJA/CAJAS → CJ, UNIDAD/UNIDADES/UND/UN → UN
                String rawUm = (colUm >= 0) ? cellText(row.getCell(colUm)).trim().toUpperCase() : "";
                String um;
                if (rawUm.startsWith("CAJ") || rawUm.equals("CJ")) {
                    um = "CJ";
                } else if (rawUm.startsWith("UN") || rawUm.equals("UDS")) {
                    um = "UN";
                } else {
                    um = rawUm;
                }
                String key = sku + "|" + um;

                Cell cantCell = row.getCell(colCantidad);
                if (cantCell == null) cantCell = row.createCell(colCantidad, CellType.NUMERIC);

                Integer qty = cantidades.get(key);
                if (qty != null && qty > 0) {
                    cantCell.setCellValue(qty);
                } else {
                    cantCell.setBlank();
                }
            }

            wb.write(out);
            return out.toByteArray();

        } catch (IOException e) {
            throw new RuntimeException("Error al generar Excel desde plantilla: " + e.getMessage(), e);
        }
    }

    private String cellText(Cell cell) {
        if (cell == null) return "";
        return switch (cell.getCellType()) {
            case STRING  -> cell.getStringCellValue();
            case NUMERIC -> String.valueOf((long) cell.getNumericCellValue());
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            default      -> "";
        };
    }

    // ══════════════════════════════════════════════════════════════════════
    // GET /api/inventario/pedidos — Historial paginado (RF21)
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Lista pedidos con filtros opcionales y paginación manual.
     *
     * @param page    Página solicitada (base 1 desde el frontend)
     * @param limit   Registros por página
     * @param q       Búsqueda libre: inicio del UUID o texto en notas
     * @param estado  "PENDIENTE" | "RECIBIDO" | null = todos
     * @param periodo "7d" | "30d" | "mes" | null = sin límite de fecha
     */
    @Transactional(readOnly = true)
    public PagedResponse<PedidoResumenDTO> listarPedidos(
            int page, int limit, String q, String estado, String periodo) {

        // 1. Resolver rango de fechas a partir del período
        LocalDate fechaFin    = null;
        LocalDate fechaInicio = null;
        if (periodo != null && !periodo.isBlank()) {
            fechaFin = LocalDate.now();
            fechaInicio = switch (periodo) {
                case "7d"  -> fechaFin.minusDays(7);
                case "30d" -> fechaFin.minusDays(30);
                case "mes" -> fechaFin.withDayOfMonth(1);
                default    -> null;
            };
        }

        EstadoPedido estadoEnum = (estado != null && !estado.isBlank())
                ? EstadoPedido.valueOf(estado.toUpperCase()) : null;

        List<PedidoResumenDTO> todos;

        if (fechaInicio == null && fechaFin == null) {
            // Caso común (sin filtro de fechas): usar métodos JPA derivados para evitar
            // la query nativa y sus problemas de tipado de parámetros en Hibernate 6.
            List<PedidoProveedor> pedidos = (estadoEnum != null)
                    ? pedidoRepo.findByEstadoOrderByFechaDesc(estadoEnum)
                    : pedidoRepo.findAllByOrderByFechaDesc();

            todos = pedidos.stream()
                    .map(p -> {
                        int totalCajas = p.getItems() == null ? 0 : p.getItems().stream()
                                .mapToInt(i -> i.getCantidadCajas() != null ? i.getCantidadCajas() : 0)
                                .sum();
                        int totalUnidades = p.getItems() == null ? 0 : p.getItems().stream()
                                .mapToInt(i -> i.getCantidadUnidades() != null ? i.getCantidadUnidades() : 0)
                                .sum();
                        return new PedidoResumenDTO(
                                p.getId(), p.getFecha(), p.getEstado().name(), p.getNotas(),
                                totalCajas, totalUnidades);
                    })
                    .collect(java.util.stream.Collectors.toCollection(java.util.ArrayList::new));
        } else {
            // Caso con filtro de fechas: usar query nativa con fechas como String
            String estadoParam    = (estadoEnum != null) ? estadoEnum.name() : null;
            String fechaInicioStr = fechaInicio != null ? fechaInicio.toString() : "0001-01-01";
            String fechaFinStr    = fechaFin    != null ? fechaFin.toString()    : "9999-12-31";
            todos = pedidoRepo.findResumenWithFilters(estadoParam, fechaInicioStr, fechaFinStr)
                    .stream()
                    .map(PedidoResumenDTO::fromRow)
                    .collect(java.util.stream.Collectors.toCollection(java.util.ArrayList::new));
        }

        // 3. Filtrar por q en Java (ID por prefijo o texto en notas)
        if (q != null && !q.isBlank()) {
            String qLower = q.toLowerCase();
            todos = todos.stream()
                    .filter(dto ->
                            dto.id().toString().toLowerCase().startsWith(qLower)
                            || (dto.notas() != null
                                    && dto.notas().toLowerCase().contains(qLower)))
                    .collect(java.util.stream.Collectors.toCollection(java.util.ArrayList::new));
        }

        // 4. Paginar (page es base-1 desde el frontend → convertir a índice 0)
        int pageIndex     = Math.max(0, page - 1);
        long totalElements = todos.size();
        int totalPages    = limit > 0 ? (int) Math.ceil((double) totalElements / limit) : 1;
        int fromIndex     = pageIndex * limit;
        int toIndex       = (int) Math.min(fromIndex + limit, totalElements);

        List<PedidoResumenDTO> content = (fromIndex >= totalElements || fromIndex < 0)
                ? List.of()
                : todos.subList(fromIndex, toIndex);

        return new PagedResponse<>(content, pageIndex, limit, totalElements, totalPages);
    }

    // ══════════════════════════════════════════════════════════════════════
    // GET /api/inventario/pedidos/{id} — Detalle con ítems (RF22)
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Retorna el pedido completo con todos sus ítems (JOIN FETCH en una sola consulta).
     */
    @Transactional(readOnly = true)
    public PedidoDetalleDTO obtenerDetallePedido(UUID id) {
        PedidoProveedor pedido = pedidoRepo.findByIdWithItems(id)
                .orElseThrow(() -> new NoSuchElementException(
                        "Pedido no encontrado con id: " + id));
        return PedidoDetalleDTO.from(pedido);
    }

}
