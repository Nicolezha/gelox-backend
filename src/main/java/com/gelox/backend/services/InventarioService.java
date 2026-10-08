package com.gelox.backend.services;

import com.gelox.backend.dto.AlertaStockDTO;
import com.gelox.backend.dto.InventarioMuertoDTO;
import com.gelox.backend.dto.InventarioProductoDTO;
import com.gelox.backend.entities.MovimientoInventario;
import com.gelox.backend.entities.Producto;
import com.gelox.backend.entities.TipoEvento;
import com.gelox.backend.entities.TipoMovimiento;
import com.gelox.backend.entities.Usuario;
import com.gelox.backend.exceptions.StockInsuficienteException;
import com.gelox.backend.repositories.MovimientoInventarioRepository;
import com.gelox.backend.repositories.ProductoRepository;
import com.gelox.backend.security.RequiereRol;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InventarioService {

    private final ProductoRepository            productoRepository;
    private final MovimientoInventarioRepository movimientoRepository;
    private final EventoSistemaService           eventoSistemaService;   // RF11

    // ──────────────────────────────────────────────────────────────────────
    // RF17 + RF27 — GET /api/inventario/productos  (con filtros opcionales)
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Lista el inventario activo con filtros opcionales.
     *
     * @param rolUsuario rol del usuario autenticado (para exponer precioCosto solo a ADMINISTRADOR)
     * @param q          búsqueda parcial en nombre o código técnico (null/blank = sin filtro)
     * @param estado     "NORMAL" | "BAJO_STOCK" | null/blank = sin filtro
     */
    @RequiereRol({"ADMINISTRADOR", "ENCARGADO_INVENTARIO", "ENCARGADO_VENTAS"})
    public List<InventarioProductoDTO> listarInventario(String rolUsuario, String q, String estado) {
        // Normalizar: vacío cuando no se aplica el filtro
        String filtroQ      = (q      == null || q.isBlank())      ? "" : q.trim();
        String filtroEstado = (estado == null || estado.isBlank()) ? "" : estado.trim().toUpperCase();

        List<Producto> productos = productoRepository.findConFiltros(filtroQ, filtroEstado);

        return productos.stream()
                .map(p -> toDTO(p, rolUsuario))
                .toList();
    }

    // ──────────────────────────────────────────────────────────────────────
    // RF24 — Alertas de bajo stock
    // ──────────────────────────────────────────────────────────────────────

    @RequiereRol({"ADMINISTRADOR", "ENCARGADO_INVENTARIO"})
    public List<AlertaStockDTO> listarAlertas() {
        // Más urgentes primero: nivel → % del mínimo cubierto → nombre
        return productoRepository.findProductosBajoStock()
                .stream()
                .map(this::toAlertaDTO)
                .sorted(Comparator
                        .comparingInt((AlertaStockDTO a) -> AlertaStockDTO.pesoNivel(a.getNivel()))
                        .thenComparingDouble(a -> a.getStockMinimo() != null && a.getStockMinimo() > 0
                                ? (double) a.getStockActual() / a.getStockMinimo() : 0d)
                        .thenComparing(AlertaStockDTO::getNombre, Comparator.nullsLast(String::compareToIgnoreCase)))
                .toList();
    }

    // ──────────────────────────────────────────────────────────────────────
    // Inventario muerto — productos sin salidas en los últimos N días
    // ──────────────────────────────────────────────────────────────────────

    @RequiereRol({"ADMINISTRADOR", "ENCARGADO_INVENTARIO"})
    public List<InventarioMuertoDTO> listarInventarioMuerto(int dias, String rolUsuario) {
        int diasSeguros = Math.max(1, Math.min(dias, 365));
        LocalDateTime ahora = LocalDateTime.now();
        boolean esAdmin = "ADMINISTRADOR".equals(rolUsuario);   // costos solo para el Gerente

        return productoRepository.findInventarioMuerto(ahora.minusDays(diasSeguros))
                .stream()
                .map(r -> {
                    LocalDateTime ultimaSalida = toLocalDateTime(r[4]);
                    LocalDateTime referencia   = ultimaSalida != null ? ultimaSalida : toLocalDateTime(r[5]);
                    Long diasSinMov = referencia != null ? ChronoUnit.DAYS.between(referencia, ahora) : null;
                    BigDecimal valor = r[6] == null ? BigDecimal.ZERO : new BigDecimal(r[6].toString());
                    return new InventarioMuertoDTO(
                            (String) r[0], (String) r[1], (String) r[2],
                            ((Number) r[3]).intValue(),
                            ultimaSalida,
                            diasSinMov,
                            esAdmin ? valor.setScale(0, RoundingMode.HALF_UP) : null);
                })
                .toList();
    }

    private LocalDateTime toLocalDateTime(Object valor) {
        if (valor == null) return null;
        if (valor instanceof Timestamp ts)       return ts.toLocalDateTime();
        if (valor instanceof LocalDateTime ldt)  return ldt;
        if (valor instanceof java.time.Instant i) return LocalDateTime.ofInstant(i, java.time.ZoneId.systemDefault());
        return LocalDateTime.parse(valor.toString().replace(' ', 'T'));
    }

    // ──────────────────────────────────────────────────────────────────────
    // RF26 — Descuento de stock compartido (ventanilla, rural, despacho)
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Descuenta stock de un producto y registra el movimiento en inventario.
     * <p>
     * Aplica bloqueo pesimista ({@code SELECT FOR UPDATE}) para evitar race
     * conditions concurrentes sobre el mismo producto.
     * <p>
     * Lanzar desde:
     * <ul>
     *   <li>{@code VentaService}         → tipo {@code SALIDA_VENTA}     (RF31)</li>
     *   <li>{@code VentaRuralService}    → tipo {@code SALIDA_VENTA}     (RF32)</li>
     *   <li>{@code PlanillaService}      → tipo {@code SALIDA_DESPACHO}  (RF36)</li>
     * </ul>
     *
     * @throws StockInsuficienteException si {@code stock_actual < cantidad}
     */
    @Transactional
    public void descontarStock(UUID productoId, int cantidad, TipoMovimiento tipo,
                               String descripcion, Usuario usuario) {

        // 1. SELECT … FOR UPDATE — bloqueo pesimista
        Producto p = productoRepository.findByIdWithLock(productoId)
                .orElseThrow(() -> new NoSuchElementException(
                        "Producto no encontrado: " + productoId));

        // 2. Validar stock suficiente
        if (p.getStockActual() < cantidad) {
            throw new StockInsuficienteException(
                    "Stock insuficiente para " + p.getNombre());
        }

        // 3. Descontar stock
        int stockAntes   = p.getStockActual();
        int stockDespues = stockAntes - cantidad;
        p.setStockActual(stockDespues);
        productoRepository.save(p);

        // 4. Registrar movimiento en bitácora
        movimientoRepository.save(MovimientoInventario.builder()
                .producto(p)
                .usuario(usuario)
                .tipo(tipo)
                .cantidad(cantidad)
                .stockResultante(stockDespues)
                .descripcion(descripcion)
                .build());

        // 5. RF11/RF24 — evento de alerta si esta salida deja el producto en o bajo su mínimo
        //    (aplica a ventanilla, rural y despachos por igual)
        if (stockAntes > p.getStockMinimo() && stockDespues <= p.getStockMinimo()) {
            eventoSistemaService.registrarEvento(
                    TipoEvento.ALERTA_STOCK,
                    String.format("La referencia %s (%s) alcanzó el stock mínimo configurado (%d uds.). Quedan %d uds.",
                            p.getNombre(), p.getCodigoTecnico(), p.getStockMinimo(), stockDespues),
                    usuario != null ? usuario.getId() : null);
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    // RF — Reintegrar stock (devoluciones de planilla)
    // ──────────────────────────────────────────────────────────────────────

    @Transactional
    public void agregarStock(UUID productoId, int cantidad, TipoMovimiento tipo,
                             String descripcion, Usuario usuario) {
        Producto p = productoRepository.findByIdWithLock(productoId)
                .orElseThrow(() -> new NoSuchElementException(
                        "Producto no encontrado: " + productoId));

        int stockDespues = p.getStockActual() + cantidad;
        p.setStockActual(stockDespues);
        productoRepository.save(p);

        movimientoRepository.save(MovimientoInventario.builder()
                .producto(p)
                .usuario(usuario)
                .tipo(tipo)
                .cantidad(cantidad)
                .stockResultante(stockDespues)
                .descripcion(descripcion)
                .build());
    }

    // ──────────────────────────────────────────────────────────────────────
    // Helpers privados
    // ──────────────────────────────────────────────────────────────────────

    private InventarioProductoDTO toDTO(Producto p, String rolUsuario) {
        String estado = (p.getStockActual() <= p.getStockMinimo()) ? "BAJO_STOCK" : "NORMAL";

        var precioCosto = "ADMINISTRADOR".equals(rolUsuario) ? p.getPrecioCosto() : null;

        return new InventarioProductoDTO(
                p.getId().toString(),
                p.getCodigoTecnico(),
                p.getNombre(),
                p.getStockActual(),
                p.getPrecioVenta(),
                estado,
                precioCosto,
                p.getPrecioComerciente()
        );
    }

    private AlertaStockDTO toAlertaDTO(Producto p) {
        return new AlertaStockDTO(
                p.getId().toString(),
                p.getCodigoTecnico(),
                p.getNombre(),
                p.getCategoria() != null ? p.getCategoria().name() : null,
                p.getStockActual(),
                p.getStockMinimo(),
                p.getStockMedio()
        );
    }
}
