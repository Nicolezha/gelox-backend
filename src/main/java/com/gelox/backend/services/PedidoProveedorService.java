package com.gelox.backend.services;

import com.gelox.backend.dto.*;
import com.gelox.backend.entities.*;
import com.gelox.backend.repositories.*;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

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

    // ── Formato fecha para el Excel ────────────────────────────────────────
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

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
                    .cantidadSolicitada(ir.cantidadSolicitada())
                    .cantidadRecibida(0)
                    .precioUnitario(producto.getPrecioCosto()) // precio base del catálogo
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

        // 5. Generar Excel
        byte[] excel = generarExcelNutresa(guardado);

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

            // RF23 — actualizar stock
            int stockAntes    = producto.getStockActual();
            int stockDespues  = stockAntes + ir.cantidadRecibida();
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
                    .cantidad(ir.cantidadRecibida())
                    .stockResultante(stockDespues)
                    .descripcion(String.format("Entrada de %d unidades. Precio: $%s",
                            ir.cantidadRecibida(), precio.toPlainString()))
                    .build());

            stockActualizado.add(new EntradaResponseDTO.StockActualizadoDTO(
                    producto.getId(), producto.getNombre(), stockDespues));

            // RF22 — comparar con el pedido si aplica
            if (pedido != null && comparacion != null) {
                ItemPedidoProveedor itemPedido = itemsPedidoMap.get(ir.productoId());
                if (itemPedido != null) {
                    // Actualizar cantidad recibida en el ítem del pedido
                    itemPedido.setCantidadRecibida(ir.cantidadRecibida());
                    itemPedido.setPrecioUnitario(precio);
                    itemPedidoRepo.save(itemPedido);

                    comparacion.add(ComparacionItemDTO.of(
                            producto.getId(),
                            producto.getCodigoTecnico(),
                            producto.getNombre(),
                            itemPedido.getCantidadSolicitada(),
                            ir.cantidadRecibida()));
                } else {
                    // Producto recibido que no estaba en el pedido → sobrante total
                    comparacion.add(ComparacionItemDTO.of(
                            producto.getId(),
                            producto.getCodigoTecnico(),
                            producto.getNombre(),
                            0,
                            ir.cantidadRecibida()));
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
                        comparacionFinal.add(ComparacionItemDTO.of(       // ← usa la final
                        p.getId(), p.getCodigoTecnico(), p.getNombre(),
                        itemPedido.getCantidadSolicitada(), 0));
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
                        req.items().stream().mapToInt(ItemEntradaRequest::cantidadRecibida).sum()),
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
    // Generación de Excel con formato Nutresa (RF21)
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Genera un archivo {@code .xlsx} con el formato de orden de compra
     * requerido por el portal de proveedores de Nutresa.
     *
     * Estructura:
     * <pre>
     * Fila 1 (merge A1:F1)  — Título "ORDEN DE COMPRA – GELOX"
     * Fila 2                 — Fecha, N° Pedido
     * Fila 3                 — Notas (si existen)
     * Fila 4                 — vacía (separador)
     * Fila 5 (encabezados)   — #, Código, Referencia, Descripción, Categoría, Cant. Solicitada
     * Filas 6+               — datos por producto
     * Última fila            — TOTAL UNIDADES
     * </pre>
     */
    private byte[] generarExcelNutresa(PedidoProveedor pedido) {
        try (XSSFWorkbook wb = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            Sheet sheet = wb.createSheet("Pedido Nutresa");
            sheet.setColumnWidth(0, 4  * 256);  // #
            sheet.setColumnWidth(1, 16 * 256);  // Código
            sheet.setColumnWidth(2, 22 * 256);  // Referencia
            sheet.setColumnWidth(3, 32 * 256);  // Descripción
            sheet.setColumnWidth(4, 14 * 256);  // Categoría
            sheet.setColumnWidth(5, 18 * 256);  // Cantidad

            // ── Estilos ────────────────────────────────────────────────
            CellStyle estiloTitulo = wb.createCellStyle();
            Font fuenteTitulo = wb.createFont();
            fuenteTitulo.setBold(true);
            fuenteTitulo.setFontHeightInPoints((short) 14);
            fuenteTitulo.setColor(IndexedColors.WHITE.getIndex());
            estiloTitulo.setFont(fuenteTitulo);
            estiloTitulo.setFillForegroundColor(IndexedColors.DARK_RED.getIndex());
            estiloTitulo.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            estiloTitulo.setAlignment(HorizontalAlignment.CENTER);
            estiloTitulo.setVerticalAlignment(VerticalAlignment.CENTER);

            CellStyle estiloEncabezado = wb.createCellStyle();
            Font fuenteEncabezado = wb.createFont();
            fuenteEncabezado.setBold(true);
            fuenteEncabezado.setColor(IndexedColors.WHITE.getIndex());
            estiloEncabezado.setFont(fuenteEncabezado);
            estiloEncabezado.setFillForegroundColor(IndexedColors.GREY_50_PERCENT.getIndex());
            estiloEncabezado.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            estiloEncabezado.setAlignment(HorizontalAlignment.CENTER);
            estiloEncabezado.setBorderBottom(BorderStyle.THIN);

            CellStyle estiloDato = wb.createCellStyle();
            estiloDato.setBorderBottom(BorderStyle.HAIR);
            estiloDato.setBorderLeft(BorderStyle.HAIR);
            estiloDato.setBorderRight(BorderStyle.HAIR);

            CellStyle estiloNumero = wb.createCellStyle();
            estiloNumero.cloneStyleFrom(estiloDato);
            estiloNumero.setAlignment(HorizontalAlignment.CENTER);

            CellStyle estiloTotal = wb.createCellStyle();
            Font fuenteTotal = wb.createFont();
            fuenteTotal.setBold(true);
            estiloTotal.setFont(fuenteTotal);
            estiloTotal.setFillForegroundColor(IndexedColors.LIGHT_YELLOW.getIndex());
            estiloTotal.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            estiloTotal.setAlignment(HorizontalAlignment.RIGHT);

            CellStyle estiloMeta = wb.createCellStyle();
            Font fuenteMeta = wb.createFont();
            fuenteMeta.setBold(true);
            estiloMeta.setFont(fuenteMeta);

            // ── Fila 0: Título ─────────────────────────────────────────
            Row fila0 = sheet.createRow(0);
            fila0.setHeightInPoints(30);
            Cell celdaTitulo = fila0.createCell(0);
            celdaTitulo.setCellValue("ORDEN DE COMPRA – GELOX  ×  NUTRESA");
            celdaTitulo.setCellStyle(estiloTitulo);
            sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 5));

            // ── Fila 1: Metadatos ──────────────────────────────────────
            Row fila1 = sheet.createRow(1);
            crearCeldaMeta(fila1, 0, "Fecha:", estiloMeta);
            crearCelda(fila1, 1, LocalDate.now().format(FMT), estiloDato);
            crearCeldaMeta(fila1, 2, "N° Pedido:", estiloMeta);
            crearCelda(fila1, 3, pedido.getId().toString().toUpperCase(), estiloDato);
            crearCeldaMeta(fila1, 4, "Estado:", estiloMeta);
            crearCelda(fila1, 5, pedido.getEstado().name(), estiloDato);

            // ── Fila 2: Notas ──────────────────────────────────────────
            Row fila2 = sheet.createRow(2);
            if (pedido.getNotas() != null && !pedido.getNotas().isBlank()) {
                crearCeldaMeta(fila2, 0, "Notas:", estiloMeta);
                Cell celdaNota = fila2.createCell(1);
                celdaNota.setCellValue(pedido.getNotas());
                sheet.addMergedRegion(new CellRangeAddress(2, 2, 1, 5));
            }

            // ── Fila 3: Separador vacío ────────────────────────────────
            sheet.createRow(3);

            // ── Fila 4: Encabezados de tabla ───────────────────────────
            Row filaEncabezado = sheet.createRow(4);
            String[] encabezados = {"#", "Código", "Referencia", "Descripción", "Categoría", "Cant. Solicitada"};
            for (int i = 0; i < encabezados.length; i++) {
                Cell c = filaEncabezado.createCell(i);
                c.setCellValue(encabezados[i]);
                c.setCellStyle(estiloEncabezado);
            }

            // ── Filas de productos ─────────────────────────────────────
            int fila = 5;
            int totalUnidades = 0;
            for (ItemPedidoProveedor item : pedido.getItems()) {
                Producto p = item.getProducto();
                Row row = sheet.createRow(fila++);

                crearCeldaNum(row, 0, fila - 5, estiloNumero);
                crearCelda(row, 1, p.getCodigoTecnico(), estiloDato);
                crearCelda(row, 2, p.getNombre(), estiloDato);
                crearCelda(row, 3, p.getDescripcion() != null ? p.getDescripcion() : "", estiloDato);
                crearCelda(row, 4, p.getCategoria() != null ? p.getCategoria().name() : "", estiloDato);
                crearCeldaNum(row, 5, item.getCantidadSolicitada(), estiloNumero);

                totalUnidades += item.getCantidadSolicitada();
            }

            // ── Fila total ─────────────────────────────────────────────
            Row filaTotal = sheet.createRow(fila);
            Cell celdaLabel = filaTotal.createCell(4);
            celdaLabel.setCellValue("TOTAL UNIDADES:");
            celdaLabel.setCellStyle(estiloTotal);
            Cell celdaTotal = filaTotal.createCell(5);
            celdaTotal.setCellValue(totalUnidades);
            celdaTotal.setCellStyle(estiloTotal);

            wb.write(out);
            return out.toByteArray();

        } catch (IOException e) {
            throw new RuntimeException("Error generando archivo Excel: " + e.getMessage(), e);
        }
    }

    // ── Helpers Excel ──────────────────────────────────────────────────────

    private void crearCelda(Row row, int col, String valor, CellStyle style) {
        Cell c = row.createCell(col);
        c.setCellValue(valor);
        c.setCellStyle(style);
    }

    private void crearCeldaNum(Row row, int col, int valor, CellStyle style) {
        Cell c = row.createCell(col);
        c.setCellValue(valor);
        c.setCellStyle(style);
    }

    private void crearCeldaMeta(Row row, int col, String label, CellStyle style) {
        Cell c = row.createCell(col);
        c.setCellValue(label);
        c.setCellStyle(style);
    }
}
