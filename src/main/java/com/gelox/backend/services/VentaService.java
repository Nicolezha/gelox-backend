package com.gelox.backend.services;

import com.gelox.backend.dto.*;
import com.gelox.backend.entities.*;
import com.gelox.backend.repositories.ItemVentaRepository;
import com.gelox.backend.repositories.MovimientoInventarioRepository;
import com.gelox.backend.repositories.ProductoRepository;
import com.gelox.backend.repositories.VentaRepository;
import com.gelox.backend.security.RequiereRol;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class VentaService {

    private final VentaRepository               ventaRepository;
    private final ItemVentaRepository           itemVentaRepository;
    private final ProductoRepository            productoRepository;
    private final MovimientoInventarioRepository movimientoRepository;
    private final EventoSistemaService          eventoSistemaService;

    @RequiereRol({"ADMINISTRADOR", "ENCARGADO_VENTAS"})
    public VentaResponseDTO iniciarVenta(IniciarVentaRequest req, Usuario usuario) {
        Venta venta = Venta.builder()
                .canal(req.canal())
                .usuario(usuario)
                .build();

        Venta guardada = ventaRepository.save(venta);

        eventoSistemaService.registrarEvento(
                TipoEvento.INICIAR_VENTA,
                String.format("Venta iniciada por canal %s.", req.canal().name()),
                usuario.getId()
        );

        return toDTO(guardada);
    }

    @RequiereRol({"ADMINISTRADOR", "ENCARGADO_VENTAS"})
    @Transactional(readOnly = true)
    public List<CatalogoVentaDTO> getCatalogo() {
        return productoRepository.findByActivoTrueOrderByNombreAsc()
                .stream()
                .map(p -> new CatalogoVentaDTO(
                        p.getId(),
                        p.getCodigoTecnico(),
                        p.getNombre(),
                        p.getImagenUrl(),
                        p.getPrecioVenta(),
                        p.getStockActual(),
                        p.getStockActual() > 0
                ))
                .toList();
    }

    @RequiereRol({"ADMINISTRADOR", "ENCARGADO_VENTAS"})
    @Transactional(readOnly = true)
    public CalcularVentaResponse calcularVenta(CalcularVentaRequest req) {
        List<UUID> ids = req.items().stream()
                .map(ItemCalculoRequest::productoId)
                .toList();

        // Una sola query: SELECT ... WHERE id IN (...)
        Map<UUID, Producto> productosMap = productoRepository.findAllById(ids)
                .stream()
                .collect(Collectors.toMap(Producto::getId, p -> p));

        // Validar que todos los IDs existen
        ids.stream()
                .filter(id -> !productosMap.containsKey(id))
                .findFirst()
                .ifPresent(id -> { throw new IllegalArgumentException("Producto no encontrado: " + id); });

        // Calcular subtotales en memoria
        List<ItemCalculoResultado> itemsResultado = req.items().stream()
                .map(item -> {
                    BigDecimal precio    = productosMap.get(item.productoId()).getPrecioVenta();
                    BigDecimal subtotal  = precio.multiply(BigDecimal.valueOf(item.cantidad()))
                            .setScale(2, RoundingMode.HALF_UP);
                    return new ItemCalculoResultado(item.productoId(), item.cantidad(), precio, subtotal);
                })
                .toList();

        BigDecimal total = itemsResultado.stream()
                .map(ItemCalculoResultado::subtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);

        return new CalcularVentaResponse(itemsResultado, total);
    }

    @RequiereRol({"ADMINISTRADOR", "ENCARGADO_VENTAS"})
    public ConfirmarVentaResponse confirmarVenta(ConfirmarVentaRequest req, Usuario usuario) {
        List<UUID> ids = req.items().stream().map(ItemVentaRequest::productoId).toList();

        // 1. Leer todos los productos con bloqueo pesimista — una sola query
        Map<UUID, Producto> productosMap = productoRepository.findByIdInWithLock(ids)
                .stream()
                .collect(Collectors.toMap(Producto::getId, p -> p));

        // 2. Validar que todos existan y estén activos
        for (UUID id : ids) {
            Producto p = productosMap.get(id);
            if (p == null || !p.getActivo()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Producto no disponible: " + id);
            }
        }

        // 3. Validar stock suficiente para todos antes de modificar cualquiera
        for (ItemVentaRequest item : req.items()) {
            Producto p = productosMap.get(item.productoId());
            if (p.getStockActual() < item.cantidad()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        String.format("Stock insuficiente para '%s'. Disponible: %d, solicitado: %d.",
                                p.getNombre(), p.getStockActual(), item.cantidad()));
            }
        }

        // 4. Calcular total
        BigDecimal total = req.items().stream()
                .map(item -> productosMap.get(item.productoId()).getPrecioVenta()
                        .multiply(BigDecimal.valueOf(item.cantidad())))
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);

        // 5. Persistir la venta (estado COMPLETADA — ingresos del día quedan acumulados implícitamente)
        Venta venta = ventaRepository.save(Venta.builder()
                .canal(req.canal())
                .estado(EstadoVenta.COMPLETADA)
                .total(total)
                .usuario(usuario)
                .build());

        // 6. Persistir ítems, descontar stock y registrar movimientos
        List<ItemVentaResponseDTO> itemsResponse = new ArrayList<>();

        for (ItemVentaRequest item : req.items()) {
            Producto p = productosMap.get(item.productoId());
            BigDecimal precio    = p.getPrecioVenta();
            BigDecimal subtotal  = precio.multiply(BigDecimal.valueOf(item.cantidad()))
                    .setScale(2, RoundingMode.HALF_UP);

            itemVentaRepository.save(ItemVenta.builder()
                    .venta(venta)
                    .producto(p)
                    .cantidadUnidades(item.cantidad())
                    .precioUnitario(precio)
                    .subtotal(subtotal)
                    .build());

            int stockAntes   = p.getStockActual();
            int stockDespues = stockAntes - item.cantidad();
            p.setStockActual(stockDespues);
            productoRepository.save(p);

            movimientoRepository.save(MovimientoInventario.builder()
                    .producto(p)
                    .usuario(usuario)
                    .tipo(TipoMovimiento.SALIDA_VENTA)
                    .cantidad(item.cantidad())
                    .stockResultante(stockDespues)
                    .descripcion(String.format("Venta %s — %d uds.", venta.getId().toString().substring(0, 8).toUpperCase(), item.cantidad()))
                    .build());

            // Alerta de bajo stock si el producto transiciona en esta venta
            if (stockAntes > p.getStockMinimo() && stockDespues <= p.getStockMinimo()) {
                eventoSistemaService.registrarEvento(
                        TipoEvento.ALERTA_STOCK,
                        String.format("La referencia %s (%s) alcanzó el stock mínimo configurado (%d uds.).",
                                p.getNombre(), p.getCodigoTecnico(), p.getStockMinimo()),
                        usuario.getId());
            }

            itemsResponse.add(new ItemVentaResponseDTO(p.getId(), p.getNombre(), item.cantidad(), precio, subtotal));
        }

        eventoSistemaService.registrarEvento(
                TipoEvento.CONFIRMAR_VENTA,
                String.format("Venta %s confirmada por canal %s. Total: $%s.",
                        venta.getId().toString().substring(0, 8).toUpperCase(),
                        req.canal().name(),
                        total.toPlainString()),
                usuario.getId());

        return new ConfirmarVentaResponse(
                venta.getId(),
                venta.getCanal().name(),
                venta.getFecha(),
                venta.getEstado().name(),
                itemsResponse,
                total);
    }

    private VentaResponseDTO toDTO(Venta v) {
        return new VentaResponseDTO(
                v.getId(),
                v.getCanal().name(),
                v.getEstado().name(),
                v.getFecha(),
                v.getTotal()
        );
    }
}
