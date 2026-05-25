package com.gelox.backend.services;

import com.gelox.backend.dto.AlertaStockDTO;
import com.gelox.backend.dto.InventarioProductoDTO;
import com.gelox.backend.entities.MovimientoInventario;
import com.gelox.backend.entities.Producto;
import com.gelox.backend.entities.TipoMovimiento;
import com.gelox.backend.entities.Usuario;
import com.gelox.backend.exceptions.StockInsuficienteException;
import com.gelox.backend.repositories.MovimientoInventarioRepository;
import com.gelox.backend.repositories.ProductoRepository;
import com.gelox.backend.security.RequiereRol;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InventarioService {

    private final ProductoRepository            productoRepository;
    private final MovimientoInventarioRepository movimientoRepository;

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
    @RequiereRol({"ADMINISTRADOR", "ENCARGADO_INVENTARIO"})
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
        return productoRepository.findProductosBajoStock()
                .stream()
                .map(this::toAlertaDTO)
                .toList();
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
        int stockDespues = p.getStockActual() - cantidad;
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
                precioCosto
        );
    }

    private AlertaStockDTO toAlertaDTO(Producto p) {
        return new AlertaStockDTO(
                p.getId().toString(),
                p.getCodigoTecnico(),
                p.getNombre(),
                p.getCategoria() != null ? p.getCategoria().name() : null,
                p.getStockActual(),
                p.getStockMinimo()
        );
    }
}
