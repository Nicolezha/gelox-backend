package com.gelox.backend.services;

import com.gelox.backend.dto.ActualizarDespachoItemRequest;
import com.gelox.backend.dto.CrearDespachoRequest;
import com.gelox.backend.dto.CrearDespachoResponse;
import com.gelox.backend.dto.ItemDespachoRequest;
import com.gelox.backend.dto.ItemDespachoResponseDTO;
import com.gelox.backend.dto.ItemImpresionPlanillaDTO;
import com.gelox.backend.dto.PlanillaImpresionResponseDTO;
import com.gelox.backend.entities.*;
import com.gelox.backend.exceptions.StockInsuficienteException;
import com.gelox.backend.repositories.ComercianteRepository;
import com.gelox.backend.repositories.ItemPlanillaRepository;
import com.gelox.backend.repositories.PlanillaComercianteRepository;
import com.gelox.backend.repositories.ProductoRepository;
import com.gelox.backend.security.RequiereRol;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class PlanillaService {

    private final ComercianteRepository comercianteRepository;
    private final PlanillaComercianteRepository planillaRepository;
    private final ItemPlanillaRepository itemPlanillaRepository;
    private final ProductoRepository productoRepository;
    private final InventarioService inventarioService;
    private final EventoSistemaService eventoSistemaService;

    @RequiereRol({"ADMINISTRADOR", "ENCARGADO_VENTAS"})
    public CrearDespachoResponse crearDespacho(CrearDespachoRequest req, Usuario usuario) {

        // 1. Validar que el comerciante exista y esté activo
        Comerciante comerciante = comercianteRepository.findById(req.comercianteId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Comerciante no encontrado."));
        if (!Boolean.TRUE.equals(comerciante.getActivo())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "El comerciante no está activo.");
        }

        // 2. Verificar que no exista planilla ya para ese comerciante en esa fecha
        planillaRepository.findByComercianteIdAndFecha(req.comercianteId(), req.fecha())
                .ifPresent(existente -> {
                    String msg = Boolean.TRUE.equals(existente.getCerrada())
                            ? "La planilla de esa fecha ya está cerrada."
                            : "Ya existe una planilla abierta para este comerciante en esta fecha.";
                    throw new ResponseStatusException(HttpStatus.CONFLICT, msg);
                });

        // 3. Crear la planilla del día
        PlanillaComerciante planilla = planillaRepository.save(PlanillaComerciante.builder()
                        .comerciante(comerciante)
                        .usuario(usuario)
                        .fecha(req.fecha())
                        .cerrada(false)
                        .build());

        // 4. Leer todos los productos con bloqueo pesimista en una sola query
        List<UUID> productoIds = req.items().stream()
                .map(ItemDespachoRequest::productoId)
                .toList();
        Map<UUID, Producto> productoMap = productoRepository.findByIdInWithLock(productoIds)
                .stream()
                .collect(Collectors.toMap(Producto::getId, Function.identity()));

        for (ItemDespachoRequest item : req.items()) {
            Producto p = productoMap.get(item.productoId());
            if (p == null || !Boolean.TRUE.equals(p.getActivo())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Producto no válido: " + item.productoId());
            }
        }

        // 5-6. Validar stock: solo para unidades NUEVAS (saldoAnterior ya está con el comerciante)
        record ItemConNuevas(ItemDespachoRequest item, int nuevas) {}
        List<ItemConNuevas> itemsConNuevas = new ArrayList<>();
        for (ItemDespachoRequest item : req.items()) {
            int nuevas = Math.max(0, item.unidades() - item.saldoAnterior());
            itemsConNuevas.add(new ItemConNuevas(item, nuevas));
            Producto p = productoMap.get(item.productoId());
            if (p.getStockActual() < nuevas) {
                throw new StockInsuficienteException("Stock insuficiente para " + p.getNombre()
                        + " (se solicitan " + nuevas + " unidades nuevas, stock: " + p.getStockActual() + ")");
            }
        }

        // 7-8. Persistir cada ItemPlanilla y descontar SOLO las unidades nuevas del stock global
        List<ItemDespachoResponseDTO> responseItems = new ArrayList<>();
        BigDecimal totalValorDespachado = BigDecimal.ZERO;
        String planillaIdCorto = planilla.getId().toString().substring(0, 8);

        for (ItemConNuevas ic : itemsConNuevas) {
            ItemDespachoRequest item = ic.item();
            Producto p = productoMap.get(item.productoId());
            int totalUnidades = item.unidades();
            int nuevasUnidades = ic.nuevas();

            itemPlanillaRepository.save(ItemPlanilla.builder()
                    .planilla(planilla)
                    .producto(p)
                    .unidadesDespachadas(totalUnidades)
                    .unidadesDevueltas(0)
                    .precioVenta(item.precioUnitario())
                    .build());

            // Solo descontar del stock global las unidades que no venían de devoluciones anteriores
            if (nuevasUnidades > 0) {
                inventarioService.descontarStock(
                        p.getId(),
                        nuevasUnidades,
                        TipoMovimiento.SALIDA_DESPACHO,
                        "Despacho planilla " + planillaIdCorto
                            + " — " + nuevasUnidades + " uds. nuevas (+" + item.saldoAnterior() + " saldo ant.)",
                        usuario);
            }

            BigDecimal subtotal = item.precioUnitario().multiply(BigDecimal.valueOf(totalUnidades));
            totalValorDespachado = totalValorDespachado.add(subtotal);

            responseItems.add(new ItemDespachoResponseDTO(
                    p.getId(),
                    p.getNombre(),
                    totalUnidades,
                    item.precioUnitario(),
                    subtotal));
        }

        // 9. Registrar evento en EventoSistemaService
        eventoSistemaService.registrarEvento(
                TipoEvento.DESPACHO_PLANILLA,
                "Despacho a " + comerciante.getNombre() + " — " + req.items().size()
                        + " productos. Total: $" + totalValorDespachado + ".",
                usuario.getId());

        // 10. Retornar respuesta
        return new CrearDespachoResponse(
                planilla.getId(),
                comerciante.getId(),
                req.fecha(),
                responseItems,
                totalValorDespachado);
    }

    // ══════════════════════════════════════════════════════════════════════
    // ══════════════════════════════════════════════════════════════════════
    // Actualizar cantidades de un despacho ya registrado (editar cierre matutino)
    // ══════════════════════════════════════════════════════════════════════

    @RequiereRol({"ADMINISTRADOR", "ENCARGADO_VENTAS"})
    public void actualizarDespacho(UUID planillaId,
                                   List<ActualizarDespachoItemRequest> items,
                                   Usuario usuario) {

        PlanillaComerciante planilla = planillaRepository.findById(planillaId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Planilla no encontrada"));
        if (Boolean.TRUE.equals(planilla.getCerrada())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "La planilla ya está cerrada y no se puede editar.");
        }

        for (ActualizarDespachoItemRequest req : items) {
            ItemPlanilla item = itemPlanillaRepository.findByPlanillaIdAndProductoId(planillaId, req.productoId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "El producto no tiene ítem en esta planilla: " + req.productoId()));

            int original = item.getUnidadesDespachadas();
            int nuevo    = req.unidades();
            int diff     = nuevo - original;

            if (diff > 0) {
                // Despachar más: descontar del stock global
                inventarioService.descontarStock(
                        item.getProducto().getId(), diff,
                        TipoMovimiento.SALIDA_DESPACHO,
                        "Ajuste despacho planilla " + planillaId.toString().substring(0, 8).toUpperCase()
                                + " +" + diff + " uds.",
                        usuario);
            } else if (diff < 0) {
                // Reducir despacho: devolver la diferencia al stock global
                inventarioService.agregarStock(
                        item.getProducto().getId(), -diff,
                        TipoMovimiento.DEVOLUCION,
                        "Ajuste despacho planilla " + planillaId.toString().substring(0, 8).toUpperCase()
                                + " " + diff + " uds.",
                        usuario);
            }

            item.setUnidadesDespachadas(nuevo);
            itemPlanillaRepository.save(item);
        }
    }

    // RF39 — Datos para impresión de planilla
    // ══════════════════════════════════════════════════════════════════════

    @Transactional(readOnly = true)
    public PlanillaImpresionResponseDTO obtenerParaImpresion(UUID planillaId) {
        PlanillaComerciante planilla = planillaRepository.findById(planillaId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Planilla no encontrada"));

        List<ItemPlanilla> itemEntities = itemPlanillaRepository.findByPlanillaIdWithProducto(planillaId);

        int totalDespachadas = 0;
        int totalDevueltas   = 0;
        int totalVendidas    = 0;
        BigDecimal sumaGanancia = BigDecimal.ZERO;
        List<ItemImpresionPlanillaDTO> items = new ArrayList<>();

        for (ItemPlanilla item : itemEntities) {
            ItemImpresionPlanillaDTO dto = ItemImpresionPlanillaDTO.from(item);
            totalDespachadas += dto.unidadesDespachadas();
            totalDevueltas   += dto.unidadesDevueltas();
            totalVendidas    += dto.unidadesVendidas();
            sumaGanancia      = sumaGanancia.add(dto.subtotal());
            items.add(dto);
        }

        // Si está cerrada usar el valor consolidado de la BD; si está abierta, calcular
        BigDecimal totalGanancia = Boolean.TRUE.equals(planilla.getCerrada())
                && planilla.getTotalGanancia() != null
                ? planilla.getTotalGanancia()
                : sumaGanancia;

        var comerciante = planilla.getComerciante();
        var usuario     = planilla.getUsuario();

        return new PlanillaImpresionResponseDTO(
                planilla.getId(),
                planilla.getFecha(),
                Boolean.TRUE.equals(planilla.getCerrada()),
                planilla.getTimestampCierre(),
                planilla.getCreatedAt(),
                comerciante.getId(),
                comerciante.getNombre(),
                comerciante.getTelefono(),
                comerciante.getMunicipio(),
                comerciante.getDireccion(),
                usuario.getId(),
                usuario.getNombre(),
                items,
                totalDespachadas,
                totalDevueltas,
                totalVendidas,
                totalGanancia,
                planilla.getEfectivoRecibido()
        );
    }
}
