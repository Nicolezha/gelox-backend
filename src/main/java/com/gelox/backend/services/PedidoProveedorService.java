package com.gelox.backend.services;

import com.gelox.backend.catalogo.dto.PagedResponse;
import com.gelox.backend.dto.*;
import com.gelox.backend.entities.*;
import com.gelox.backend.exportacion.ExportadorPedido;
import com.gelox.backend.repositories.*;
import com.gelox.backend.security.RequiereRol;
import com.gelox.backend.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.ZoneId;
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
    private final ExportadorPedido             exportador;                // RF67
    private final ConversionUnidadService      conversionUnidadService;   // RF64
    private final ProveedorRepository           proveedorRepo;             // RF63

    /** Proveedor de los productos sin proveedor (FP-15 D6). */
    static final String PROVEEDOR_POR_DEFECTO = "Nutresa";

    // ══════════════════════════════════════════════════════════════════════
    // RF21 + RF63 — Crear los pedidos (uno por proveedor) y su archivo
    // ══════════════════════════════════════════════════════════════════════

    /** Proveedor al que va un producto: el suyo o, si no tiene, Nutresa (FP-15 D6). */
    public record ProveedorPedido(UUID id, String nombre) {
    }

    /**
     * Un pedido creado por {@link #crearPedido}. {@code total} es el costo estimado (cajas × precio_costo
     * + unidades × precio_costo ÷ unidades_por_caja). {@code advertenciaMontoMinimo} es nula si el total
     * alcanza el monto mínimo del proveedor: el pedido se crea igual (T194).
     */
    public record PedidoCreado(UUID pedidoId, UUID proveedorId, String proveedorNombre, BigDecimal total,
                               String nombreArchivo, byte[] excel,
                               List<String> productosSinCodigo, List<String> productosNoUbicados,
                               String advertenciaMontoMinimo) {
        public String exportUrl() {
            return "/api/inventario/pedidos/" + pedidoId + "/exportar";
        }
    }

    /**
     * Registra un pedido PENDIENTE por cada proveedor de los productos, en el orden en que aparecen,
     * y arma el archivo de cada uno con la plantilla de su proveedor (RF67).
     */
    public List<PedidoCreado> crearPedido(CrearPedidoRequest req, Usuario usuarioActual) {
        LocalDate hoy = LocalDate.now(ZoneId.of("America/Bogota"));

        // 1. Resolver productos y agruparlos por proveedor
        Map<UUID, Proveedor> proveedores = new LinkedHashMap<>();
        Map<UUID, List<ItemPedidoProveedor>> itemsPorProveedor = new LinkedHashMap<>();
        Proveedor[] porDefecto = new Proveedor[1];
        for (ItemPedidoRequest ir : req.items()) {
            Producto producto = productoRepo.findById(ir.productoId())
                    .filter(TenantContext::pertenece)
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Producto no encontrado: " + ir.productoId()));
            Proveedor proveedor = proveedorDelProducto(producto, porDefecto);
            proveedores.putIfAbsent(proveedor.getId(), proveedor);
            itemsPorProveedor.computeIfAbsent(proveedor.getId(), k -> new ArrayList<>())
                    .add(ItemPedidoProveedor.builder()
                            .producto(producto)
                            .cantidadCajas(ir.cantidadCajas())
                            .cantidadUnidades(ir.cantidadUnidades())
                            .cantidadRecibida(0)
                            .precioUnitario(producto.getPrecioCosto())
                            .build());
        }

        // 2. Un pedido por proveedor
        List<PedidoCreado> creados = new ArrayList<>();
        for (Map.Entry<UUID, List<ItemPedidoProveedor>> grupo : itemsPorProveedor.entrySet()) {
            Proveedor proveedor = proveedores.get(grupo.getKey());
            PedidoProveedor pedido = PedidoProveedor.builder()
                    .usuario(usuarioActual)
                    .proveedor(proveedor)
                    .fecha(hoy)
                    .estado(EstadoPedido.PENDIENTE)
                    .notas(req.notas())
                    .build();
            grupo.getValue().forEach(i -> i.setPedido(pedido));
            pedido.setItems(grupo.getValue());

            PedidoProveedor guardado = pedidoRepo.save(pedido);

            eventoService.registrarEvento(
                    TipoEvento.PEDIDO_PROVEEDOR,
                    String.format("Pedido #%s a %s generado: %d referencias. Estado: PENDIENTE.",
                            guardado.getId().toString().substring(0, 8).toUpperCase(),
                            proveedor.getNombre(), grupo.getValue().size()),
                    usuarioActual.getId());

            BigDecimal total = totalEstimado(guardado);
            ExportadorPedido.Resultado archivo = exportador.generar(guardado, proveedor);
            creados.add(new PedidoCreado(guardado.getId(), proveedor.getId(), proveedor.getNombre(), total,
                    nombreArchivo(guardado, proveedor), archivo.contenido(),
                    archivo.productosSinCodigo(), archivo.productosNoUbicados(),
                    advertenciaMontoMinimo(proveedor, total)));
        }
        return creados;
    }

    /**
     * Proveedor al que iría cada producto, sin crear nada: la voz lo usa para decir cuántos pedidos
     * se van a generar antes de confirmar.
     */
    @Transactional(readOnly = true)
    public Map<UUID, ProveedorPedido> proveedoresDe(Collection<UUID> productoIds) {
        Map<UUID, ProveedorPedido> resultado = new LinkedHashMap<>();
        Proveedor[] porDefecto = new Proveedor[1];
        for (UUID id : productoIds) {
            productoRepo.findById(id).filter(TenantContext::pertenece).ifPresent(p -> {
                Proveedor proveedor = proveedorDelProducto(p, porDefecto);
                resultado.put(id, new ProveedorPedido(proveedor.getId(), proveedor.getNombre()));
            });
        }
        return resultado;
    }

    /**
     * El proveedor del producto; si no tiene (productos creados sin proveedor), el proveedor
     * «Nutresa» de la distribuidora. Sin ninguno de los dos → 400: el pedido necesita proveedor
     * (pedido_proveedor.proveedor_id es NOT NULL desde V10).
     */
    private Proveedor proveedorDelProducto(Producto producto, Proveedor[] porDefecto) {
        if (producto.getProveedor() != null) return producto.getProveedor();
        if (porDefecto[0] == null) {
            porDefecto[0] = proveedorRepo.findFirstByNombreIgnoreCaseAndActivoTrue(PROVEEDOR_POR_DEFECTO)
                    .filter(TenantContext::pertenece)
                    .orElse(null);
        }
        if (porDefecto[0] == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "El producto " + producto.getNombre() + " no tiene proveedor asignado");
        }
        return porDefecto[0];
    }

    /** Costo estimado del pedido con el precio de cada ítem (por caja). */
    static BigDecimal totalEstimado(PedidoProveedor pedido) {
        BigDecimal total = BigDecimal.ZERO;
        for (ItemPedidoProveedor i : pedido.getItems()) {
            BigDecimal precioCaja = i.getPrecioUnitario() != null ? i.getPrecioUnitario() : BigDecimal.ZERO;
            Integer porCaja = i.getProducto().getUnidadesPorCaja();
            int unidadesCaja = porCaja != null && porCaja > 0 ? porCaja : 1;
            total = total.add(precioCaja.multiply(BigDecimal.valueOf(valorOrCero(i.getCantidadCajas()))))
                    .add(precioCaja.multiply(BigDecimal.valueOf(valorOrCero(i.getCantidadUnidades())))
                            .divide(BigDecimal.valueOf(unidadesCaja), 2, RoundingMode.HALF_UP));
        }
        return total.setScale(2, RoundingMode.HALF_UP);
    }

    /** «El pedido a Nutresa suma $ 250.000 y su monto mínimo es $ 300.000.» o null si lo alcanza. */
    static String advertenciaMontoMinimo(Proveedor proveedor, BigDecimal total) {
        BigDecimal minimo = proveedor.getMontoMinimoPedido();
        if (minimo == null || minimo.signum() <= 0 || total.compareTo(minimo) >= 0) return null;
        return "El pedido a " + proveedor.getNombre() + " suma " + pesos(total)
                + " y su monto mínimo es " + pesos(minimo) + ".";
    }

    private static String pesos(BigDecimal valor) {
        DecimalFormat formato = new DecimalFormat("#,##0", DecimalFormatSymbols.getInstance(Locale.forLanguageTag("es-CO")));
        return "$ " + formato.format(valor.setScale(0, RoundingMode.HALF_UP));
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
                    .filter(TenantContext::pertenece)
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Pedido no encontrado: " + req.pedidoId()));
            itemPedidoRepo.findByPedidoIdWithProducto(req.pedidoId())
                    .forEach(i -> itemsPedidoMap.put(i.getProducto().getId(), i));
            comparacion = new ArrayList<>();
        }

        // Procesar cada producto recibido
        for (ItemEntradaRequest ir : req.items()) {
            Producto producto = productoRepo.findById(ir.productoId())
                    .filter(TenantContext::pertenece)
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Producto no encontrado: " + ir.productoId()));

            // RF23 — convertir cajas a unidades y actualizar stock
            int upC          = producto.getUnidadesPorCaja() != null ? producto.getUnidadesPorCaja() : 1;
            // CJ → UN con unidades_por_caja; otra unidad (p. ej. BOL) con factor_conversion. Stock en unidad base.
            int otras        = conversionUnidadService.aUnidadBase(producto, ir.unidad(), ir.cantidadUnidad());
            int totalEntrada = ir.cantidadUnidades() + ir.cantidadCajas() * upC + otras;
            int stockAntes   = producto.getStockActual();
            int stockDespues = stockAntes + totalEntrada;
            producto.setStockActual(stockDespues);
            productoRepo.save(producto);

            // RF23 — registrar movimiento
            // precio_costo del catálogo es por caja: se convierte a costo por unidad, porque la
            // inversión se calcula como precio_unitario × cantidad_recibida (en unidades)
            BigDecimal precio = (ir.precioUnitario() != null && ir.precioUnitario().compareTo(BigDecimal.ZERO) > 0)
                    ? ir.precioUnitario()
                    : producto.getPrecioCosto().divide(BigDecimal.valueOf(upC), 2, java.math.RoundingMode.HALF_UP);

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
                .filter(TenantContext::pertenece)
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
    // Archivo del pedido con la plantilla de su proveedor (RF21, RF67)
    // ══════════════════════════════════════════════════════════════════════

    /** Archivo listo para descargar y avisos de la exportación (FP-15 §2.18.1). */
    public record ArchivoPedido(byte[] contenido, String nombreArchivo,
                                List<String> productosSinCodigo, List<String> productosNoUbicados) {
    }

    /**
     * Arma el .xlsx del pedido con la plantilla de su proveedor ({@link ExportadorPedido}): el del
     * pedido (RF63) o, en pedidos sin él, el de sus productos; sin ninguno, la plantilla de Nutresa.
     */
    private ExportadorPedido.Resultado exportar(PedidoProveedor pedido) {
        return exportador.generar(pedido, proveedorDe(pedido));
    }

    private static Proveedor proveedorDe(PedidoProveedor pedido) {
        if (pedido.getProveedor() != null) return pedido.getProveedor();
        return pedido.getItems().stream()
                .map(i -> i.getProducto().getProveedor())
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    /** Pedido_&lt;proveedor&gt;_&lt;fecha&gt;.xlsx; un pedido sin proveedor es de Nutresa. */
    private static String nombreArchivo(PedidoProveedor pedido, Proveedor proveedor) {
        String nombre = proveedor != null ? proveedor.getNombre() : "Nutresa";
        String limpio = nombre.trim().replaceAll("[^\\p{L}\\p{N}]+", "-").replaceAll("^-|-$", "");
        return "Pedido_" + limpio + "_" + pedido.getFecha() + ".xlsx";
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
            todos = pedidoRepo.findResumenWithFilters(TenantContext.requerido(), estadoParam, fechaInicioStr, fechaFinStr)
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

    // ══════════════════════════════════════════════════════════════════════
    // T47 — Reexportar el Excel de un pedido ya creado
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Vuelve a generar el Excel Nutresa de un pedido existente (por ejemplo,
     * cuando se creó por voz y el archivo no viajó en esa respuesta).
     */
    @Transactional(readOnly = true)
    @RequiereRol({"ADMINISTRADOR", "ENCARGADO_INVENTARIO"})
    public byte[] exportarExcelPedido(UUID pedidoId) {
        PedidoProveedor pedido = pedidoRepo.findByIdWithItems(pedidoId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Pedido no encontrado"));
        return exportar(pedido).contenido();
    }

    /** Igual que {@link #exportarExcelPedido} pero con el nombre del archivo y los avisos (RF67). */
    @Transactional(readOnly = true)
    @RequiereRol({"ADMINISTRADOR", "ENCARGADO_INVENTARIO"})
    public ArchivoPedido exportarPedido(UUID pedidoId) {
        PedidoProveedor pedido = pedidoRepo.findByIdWithItems(pedidoId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Pedido no encontrado"));
        ExportadorPedido.Resultado r = exportar(pedido);
        return new ArchivoPedido(r.contenido(), nombreArchivo(pedido, proveedorDe(pedido)),
                r.productosSinCodigo(), r.productosNoUbicados());
    }

    // ══════════════════════════════════════════════════════════════════════
    // RF53 — Modificar un pedido en estado PENDIENTE (por voz)
    // ══════════════════════════════════════════════════════════════════════

    @Transactional
    @RequiereRol({"ADMINISTRADOR", "ENCARGADO_INVENTARIO"})
    public Map<String, Object> modificarPedidoPendiente(UUID pedidoId, ModificarPedidoRequest req, Usuario usuarioActual) {

        PedidoProveedor pedido = pedidoRepo.findByIdWithItems(pedidoId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Pedido no encontrado"));

        if (pedido.getEstado() != EstadoPedido.PENDIENTE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Solo se pueden modificar pedidos en estado PENDIENTE");
        }

        Producto producto = productoRepo.findById(req.productoId())
                .filter(TenantContext::pertenece)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Producto no encontrado: " + req.productoId()));

        // RF63 — un pedido es de un solo proveedor
        if (req.accion() == AccionPedido.AGREGAR && pedido.getProveedor() != null
                && producto.getProveedor() != null
                && !producto.getProveedor().getId().equals(pedido.getProveedor().getId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    producto.getNombre() + " es de " + producto.getProveedor().getNombre()
                            + " y el pedido es de " + pedido.getProveedor().getNombre());
        }

        switch (req.accion()) {
            case AGREGAR    -> agregarItemPedido(pedido, producto, req);
            case ELIMINAR   -> eliminarItemPedido(pedido, producto);
            case ACTUALIZAR -> actualizarItemPedido(pedido, producto, req);
        }

        PedidoProveedor guardado = pedidoRepo.save(pedido);

        eventoService.registrarEvento(
                TipoEvento.PEDIDO_PROVEEDOR,
                String.format("Pedido #%s modificado: %s %s.",
                        guardado.getId().toString().substring(0, 8).toUpperCase(),
                        req.accion().name(),
                        producto.getNombre()),
                usuarioActual.getId());

        ExportadorPedido.Resultado archivo = exportar(guardado);
        return Map.of("pedidoId", guardado.getId(), "excel", archivo.contenido(),
                "productosSinCodigo", archivo.productosSinCodigo(),
                "productosNoUbicados", archivo.productosNoUbicados());
    }

    private void agregarItemPedido(PedidoProveedor pedido, Producto producto, ModificarPedidoRequest req) {
        int cajas = valorOrCero(req.cantidadCajas());
        int unidades = valorOrCero(req.cantidadUnidades());

        pedido.getItems().stream()
                .filter(i -> i.getProducto().getId().equals(producto.getId()))
                .findFirst()
                .ifPresentOrElse(
                        item -> {
                            item.setCantidadCajas(item.getCantidadCajas() + cajas);
                            item.setCantidadUnidades(item.getCantidadUnidades() + unidades);
                        },
                        () -> pedido.getItems().add(ItemPedidoProveedor.builder()
                                .pedido(pedido)
                                .producto(producto)
                                .cantidadCajas(cajas)
                                .cantidadUnidades(unidades)
                                .cantidadRecibida(0)
                                .precioUnitario(producto.getPrecioCosto())
                                .build()));
    }

    private void eliminarItemPedido(PedidoProveedor pedido, Producto producto) {
        boolean existia = pedido.getItems().removeIf(i -> i.getProducto().getId().equals(producto.getId()));
        if (!existia) {
            throw new IllegalArgumentException("El producto no está en el pedido: " + producto.getId());
        }
        if (pedido.getItems().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "El pedido no puede quedar sin productos");
        }
    }

    private void actualizarItemPedido(PedidoProveedor pedido, Producto producto, ModificarPedidoRequest req) {
        ItemPedidoProveedor item = pedido.getItems().stream()
                .filter(i -> i.getProducto().getId().equals(producto.getId()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "El producto no está en el pedido: " + producto.getId()));

        int cajas = valorOrCero(req.cantidadCajas());
        int unidades = valorOrCero(req.cantidadUnidades());
        if (cajas < 0 || unidades < 0 || (cajas == 0 && unidades == 0)) {
            throw new IllegalArgumentException("Las cantidades deben ser >= 0 y al menos una mayor que 0");
        }

        item.setCantidadCajas(cajas);
        item.setCantidadUnidades(unidades);
    }

    private static int valorOrCero(Integer valor) {
        return valor != null ? valor : 0;
    }

}
