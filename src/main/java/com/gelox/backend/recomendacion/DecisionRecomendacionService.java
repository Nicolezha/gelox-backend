package com.gelox.backend.recomendacion;

import com.gelox.backend.catalogo.dto.PagedResponse;
import com.gelox.backend.dto.CrearPedidoRequest;
import com.gelox.backend.dto.ItemPedidoRequest;
import com.gelox.backend.entities.AjustePresupuesto;
import com.gelox.backend.entities.DecisionItem;
import com.gelox.backend.entities.EstadoPedido;
import com.gelox.backend.entities.EstadoRecomendacion;
import com.gelox.backend.entities.ItemPedidoProveedor;
import com.gelox.backend.entities.ItemRecomendacion;
import com.gelox.backend.entities.PedidoProveedor;
import com.gelox.backend.entities.RecomendacionCompra;
import com.gelox.backend.entities.TipoEvento;
import com.gelox.backend.entities.Usuario;
import com.gelox.backend.recomendacion.dto.DecidirItemRequest;
import com.gelox.backend.recomendacion.dto.ItemDTO;
import com.gelox.backend.recomendacion.dto.MetricasRecomendacionDTO;
import com.gelox.backend.recomendacion.dto.PedidoGeneradoDTO;
import com.gelox.backend.recomendacion.dto.RecomendacionDTO;
import com.gelox.backend.recomendacion.dto.RecomendacionResumenDTO;
import com.gelox.backend.repositories.ItemPedidoProveedorRepository;
import com.gelox.backend.repositories.PedidoProveedorRepository;
import com.gelox.backend.repositories.RecomendacionCompraRepository;
import com.gelox.backend.security.RequiereRol;
import com.gelox.backend.services.EventoSistemaService;
import com.gelox.backend.services.PedidoProveedorService;
import com.gelox.backend.services.PedidoProveedorService.PedidoCreado;
import com.gelox.backend.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * RF55 — decidir por ítem y aprobar todo · RF56 — generar el pedido a proveedor con lo aprobado ·
 * RF57 — historial y métricas de precisión.
 *
 * <p>Toda búsqueda por id valida el tenant con {@link TenantContext#pertenece}: findById no pasa
 * por el filtro de Hibernate y una recomendación de otra distribuidora responde 404.</p>
 */
@Service
@RequiredArgsConstructor
@Transactional
public class DecisionRecomendacionService {

    private static final ZoneId ZONA_BOGOTA = ZoneId.of("America/Bogota");
    private static final LocalDateTime SIN_LIMITE_INFERIOR = LocalDateTime.of(2000, 1, 1, 0, 0);
    private static final LocalDateTime SIN_LIMITE_SUPERIOR = LocalDateTime.of(2100, 1, 1, 0, 0);
    private static final int DIAS_METRICAS_POR_DEFECTO = 30;
    private static final int TAMANO_MAXIMO_PAGINA = 100;

    private final RecomendacionCompraRepository recomendacionRepo;
    private final PedidoProveedorRepository pedidoRepo;
    private final ItemPedidoProveedorRepository itemPedidoRepo;
    private final PedidoProveedorService pedidoService;
    private final EventoSistemaService eventoService;

    // ══════════════════════════════════════════════════════════════════════
    // RF55 — Decidir
    // ══════════════════════════════════════════════════════════════════════

    @RequiereRol({"ADMINISTRADOR", "ENCARGADO_INVENTARIO"})
    @Transactional(readOnly = true)
    public RecomendacionDTO obtener(UUID id) {
        return toDTO(buscar(id));
    }

    /**
     * Aprueba, modifica o rechaza un producto sugerido. Recalcula el costo aprobado y deja la
     * recomendación EN_REVISION.
     */
    @RequiereRol({"ADMINISTRADOR", "ENCARGADO_INVENTARIO"})
    public ItemDTO decidir(UUID id, UUID itemId, DecidirItemRequest req) {
        RecomendacionCompra recomendacion = buscar(id);
        ItemRecomendacion item = recomendacion.getItems().stream()
                .filter(i -> i.getId().equals(itemId))
                .findFirst()
                .orElseThrow(() -> new NoSuchElementException("Ítem no encontrado"));
        exigirAdmiteDecision(recomendacion);

        if (req == null || req.decision() == null) {
            throw new IllegalArgumentException("Indica la decisión");
        }
        switch (req.decision()) {
            case APROBADO -> {
                if (!tieneCajasSugeridas(item)) {
                    throw new IllegalArgumentException("Indica las cajas");
                }
                item.aprobar();
            }
            case MODIFICADO -> {
                if (req.cajas() == null) {
                    throw new IllegalArgumentException("Indica las cajas");
                }
                item.modificar(req.cajas());
            }
            case RECHAZADO -> item.rechazar(req.motivo());
            default -> throw new IllegalArgumentException("Decisión no válida");
        }

        recomendacion.recalcularCostoAprobado();
        recomendacion.setEstado(EstadoRecomendacion.EN_REVISION);
        recomendacionRepo.save(recomendacion);
        return toItemDTO(item);
    }

    /**
     * Aprueba los ítems PENDIENTE con cajas sugeridas. Sin endpoint: lo usa la voz (T115),
     * que ya valida el rol en su handler.
     */
    public RecomendacionDTO aprobarTodo(UUID id) {
        RecomendacionCompra recomendacion = buscar(id);
        exigirAdmiteDecision(recomendacion);

        recomendacion.getItems().stream()
                .filter(i -> i.getDecision() == DecisionItem.PENDIENTE && tieneCajasSugeridas(i))
                .forEach(ItemRecomendacion::aprobar);

        recomendacion.recalcularCostoAprobado();
        recomendacion.setEstado(EstadoRecomendacion.EN_REVISION);
        return toDTO(recomendacionRepo.save(recomendacion));
    }

    // ══════════════════════════════════════════════════════════════════════
    // RF56 — Generar el pedido con lo aprobado
    // ══════════════════════════════════════════════════════════════════════

    @RequiereRol({"ADMINISTRADOR", "ENCARGADO_INVENTARIO"})
    public PedidoGeneradoDTO generarPedido(UUID id, Usuario usuario) {
        RecomendacionCompra recomendacion = buscar(id);
        exigirAdmiteDecision(recomendacion);

        List<ItemPedidoRequest> items = recomendacion.getItems().stream()
                .filter(i -> (i.getDecision() == DecisionItem.APROBADO || i.getDecision() == DecisionItem.MODIFICADO)
                        && i.getCajasAprobadas() != null && i.getCajasAprobadas() > 0)
                .map(i -> new ItemPedidoRequest(i.getProducto().getId(), i.getCajasAprobadas(), 0))
                .toList();
        if (items.isEmpty()) {
            throw new IllegalArgumentException("Aprueba al menos un producto");
        }

        // RF63 — crearPedido parte los ítems en un pedido por proveedor
        String prefijo = id.toString().substring(0, 8);
        List<PedidoCreado> creados = pedidoService.crearPedido(new CrearPedidoRequest(items, nota(id)), usuario);
        if (creados.isEmpty()) {
            throw new IllegalStateException("No se generó ningún pedido");
        }

        // La recomendación guarda el primero; los demás se encuentran por la nota (pedidosDe)
        UUID pedidoId = creados.get(0).pedidoId();
        PedidoProveedor pedido = pedidoRepo.findById(pedidoId)
                .filter(TenantContext::pertenece)
                .orElseThrow(() -> new NoSuchElementException("Pedido no encontrado: " + pedidoId));
        recomendacion.setPedidoProveedor(pedido);
        recomendacion.setEstado(EstadoRecomendacion.PEDIDO_GENERADO);
        recomendacionRepo.save(recomendacion);

        String numeros = creados.stream()
                .map(c -> "#" + c.pedidoId().toString().substring(0, 8).toUpperCase() + " a " + c.proveedorNombre())
                .collect(Collectors.joining(", "));
        eventoService.registrarEvento(
                TipoEvento.PEDIDO_DESDE_RECOMENDACION,
                String.format("%s %s generado%s desde la recomendación #%s: %d referencias.",
                        creados.size() == 1 ? "Pedido" : creados.size() + " pedidos", numeros,
                        creados.size() == 1 ? "" : "s", prefijo.toUpperCase(), items.size()),
                usuario.getId());

        return PedidoGeneradoDTO.de(creados);
    }

    /** Nota de los pedidos de una recomendación; la comparten todos sus pedidos (uno por proveedor). */
    private static String nota(UUID recomendacionId) {
        return "Generado desde recomendación #" + recomendacionId.toString().substring(0, 8);
    }

    /** Pedidos de la recomendación: el guardado en ella y los demás proveedores con la misma nota. */
    private List<PedidoProveedor> pedidosDe(RecomendacionCompra recomendacion) {
        PedidoProveedor principal = recomendacion.getPedidoProveedor();
        if (principal == null) return List.of();
        List<PedidoProveedor> pedidos = new ArrayList<>(pedidoRepo.findByNotas(nota(recomendacion.getId())));
        if (pedidos.stream().noneMatch(p -> p.getId().equals(principal.getId()))) {
            pedidos.add(0, principal);
        }
        return pedidos;
    }

    // ══════════════════════════════════════════════════════════════════════
    // RF57 — Historial y métricas
    // ══════════════════════════════════════════════════════════════════════

    /** Fechas y estado opcionales; page base 0. */
    @RequiereRol({"ADMINISTRADOR", "ENCARGADO_INVENTARIO"})
    @Transactional(readOnly = true)
    public PagedResponse<RecomendacionResumenDTO> historial(LocalDate desde, LocalDate hasta,
                                                            EstadoRecomendacion estado, int page, int size) {
        validarRango(desde, hasta);
        int tamano = Math.min(Math.max(size, 1), TAMANO_MAXIMO_PAGINA);
        List<EstadoRecomendacion> estados = estado == null
                ? List.of(EstadoRecomendacion.values()) : List.of(estado);

        Page<RecomendacionCompra> pagina = recomendacionRepo.buscar(
                TenantContext.requerido(), estados,
                desde != null ? desde.atStartOfDay() : SIN_LIMITE_INFERIOR,
                hasta != null ? hasta.plusDays(1).atStartOfDay() : SIN_LIMITE_SUPERIOR,
                PageRequest.of(Math.max(page, 0), tamano));

        return new PagedResponse<>(
                pagina.getContent().stream().map(this::toResumenDTO).toList(),
                pagina.getNumber(), pagina.getSize(), pagina.getTotalElements(), pagina.getTotalPages());
    }

    /**
     * Sin fechas, toma los últimos 30 días hasta hoy (hora de Bogotá).
     *
     * <p>precisionCantidad promedia 1 − |cajas reales − cajas sugeridas| ÷ cajas sugeridas
     * (acotado a [0, 1]) sobre los ítems aprobados o modificados de recomendaciones con pedido:
     * un término con las cajas pedidas y, si el pedido ya se recibió, otro con las cajas
     * recibidas (cantidad_recibida está en unidades). Sin pedidos no hay datos y vale null.</p>
     */
    @RequiereRol({"ADMINISTRADOR", "ENCARGADO_INVENTARIO"})
    @Transactional(readOnly = true)
    public MetricasRecomendacionDTO metricas(LocalDate desde, LocalDate hasta) {
        LocalDate fin = hasta != null ? hasta : LocalDate.now(ZONA_BOGOTA);
        LocalDate inicio = desde != null ? desde : fin.minusDays(DIAS_METRICAS_POR_DEFECTO);
        validarRango(inicio, fin);

        List<RecomendacionCompra> recomendaciones = recomendacionRepo.buscarConItems(
                TenantContext.requerido(), inicio.atStartOfDay(), fin.plusDays(1).atStartOfDay());

        List<ItemRecomendacion> items = recomendaciones.stream()
                .flatMap(r -> r.getItems().stream())
                .toList();
        long aprobados = contar(items, DecisionItem.APROBADO);
        long modificados = contar(items, DecisionItem.MODIFICADO);
        long rechazados = contar(items, DecisionItem.RECHAZADO);
        double tasaAprobacion = items.isEmpty() ? 0 : redondear((aprobados + modificados) / (double) items.size());

        return new MetricasRecomendacionDTO(recomendaciones.size(), items.size(),
                aprobados, modificados, rechazados, tasaAprobacion, precisionCantidad(recomendaciones));
    }

    private Double precisionCantidad(List<RecomendacionCompra> recomendaciones) {
        List<Double> precisiones = new ArrayList<>();
        for (RecomendacionCompra recomendacion : recomendaciones) {
            List<PedidoProveedor> pedidos = pedidosDe(recomendacion);
            if (pedidos.isEmpty()) continue;

            // Un pedido por proveedor: se juntan los ítems de los que ya se recibieron
            Map<UUID, ItemPedidoProveedor> itemsPedido = pedidos.stream()
                    .filter(p -> p.getEstado() == EstadoPedido.RECIBIDO)
                    .flatMap(p -> itemPedidoRepo.findByPedidoIdWithProducto(p.getId()).stream())
                    .collect(Collectors.toMap(i -> i.getProducto().getId(), Function.identity(), (a, b) -> a));

            for (ItemRecomendacion item : recomendacion.getItems()) {
                boolean decidido = item.getDecision() == DecisionItem.APROBADO
                        || item.getDecision() == DecisionItem.MODIFICADO;
                if (!decidido || !tieneCajasSugeridas(item) || item.getCajasAprobadas() == null) continue;

                precisiones.add(precision(item.getCajasAprobadas(), item.getCajasSugeridas()));

                ItemPedidoProveedor itemPedido = itemsPedido.get(item.getProducto().getId());
                if (itemPedido != null && itemPedido.getCantidadRecibida() != null) {
                    Integer porCaja = itemPedido.getProducto().getUnidadesPorCaja();
                    int unidadesPorCaja = porCaja != null && porCaja > 0 ? porCaja : 1;
                    precisiones.add(precision((double) itemPedido.getCantidadRecibida() / unidadesPorCaja,
                            item.getCajasSugeridas()));
                }
            }
        }
        return precisiones.isEmpty()
                ? null
                : redondear(precisiones.stream().mapToDouble(Double::doubleValue).average().orElse(0));
    }

    // ══════════════════════════════════════════════════════════════════════
    // Auxiliares
    // ══════════════════════════════════════════════════════════════════════

    private RecomendacionCompra buscar(UUID id) {
        return recomendacionRepo.findById(id)
                .filter(TenantContext::pertenece)
                .orElseThrow(() -> new NoSuchElementException("Recomendación no encontrada"));
    }

    private void exigirAdmiteDecision(RecomendacionCompra recomendacion) {
        if (!recomendacion.admiteDecision()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "La recomendación ya se convirtió en pedido");
        }
    }

    /** Un ítem EXCLUIDO no cupo en el presupuesto y trae 0 cajas sugeridas. */
    private boolean tieneCajasSugeridas(ItemRecomendacion item) {
        return item.getAjustePresupuesto() != AjustePresupuesto.EXCLUIDO
                && item.getCajasSugeridas() != null && item.getCajasSugeridas() > 0;
    }

    private void validarRango(LocalDate desde, LocalDate hasta) {
        if (desde != null && hasta != null && desde.isAfter(hasta)) {
            throw new IllegalArgumentException("La fecha inicial no puede ser posterior a la final");
        }
    }

    private long contar(List<ItemRecomendacion> items, DecisionItem decision) {
        return items.stream().filter(i -> i.getDecision() == decision).count();
    }

    private double precision(double cajasReales, double cajasSugeridas) {
        return Math.max(0, 1 - Math.abs(cajasReales - cajasSugeridas) / cajasSugeridas);
    }

    private double redondear(double valor) {
        return Math.round(valor * 10_000) / 10_000.0;
    }

    private RecomendacionDTO toDTO(RecomendacionCompra r) {
        return new RecomendacionDTO(
                r.getId(), r.getEstado(), r.getOrigen(), r.getPresupuesto(), r.getCostoTotal(),
                r.getCostoAprobado(), r.getConfianzaBaja(), r.getDiasHistorial(), r.getDiasConVentas(),
                r.getPedidoProveedor() != null ? r.getPedidoProveedor().getId() : null,
                r.getCreatedAt(), r.getUpdatedAt(),
                r.getItems().stream()
                        .sorted(Comparator.comparing(ItemRecomendacion::getPrioridad,
                                Comparator.nullsLast(Comparator.naturalOrder())))
                        .map(this::toItemDTO)
                        .toList());
    }

    private RecomendacionResumenDTO toResumenDTO(RecomendacionCompra r) {
        List<ItemRecomendacion> items = r.getItems();
        return new RecomendacionResumenDTO(
                r.getId(), r.getCreatedAt(), r.getEstado(), r.getOrigen(), r.getPresupuesto(),
                r.getCostoTotal(), r.getCostoAprobado(), r.getConfianzaBaja(),
                r.getPedidoProveedor() != null ? r.getPedidoProveedor().getId() : null,
                items.size(), contar(items, DecisionItem.APROBADO), contar(items, DecisionItem.MODIFICADO),
                contar(items, DecisionItem.RECHAZADO), contar(items, DecisionItem.PENDIENTE));
    }

    private ItemDTO toItemDTO(ItemRecomendacion i) {
        return new ItemDTO(
                i.getId(), i.getProducto().getId(), i.getProducto().getNombre(), i.getPrioridad(),
                i.getUrgencia(), i.getScore(), i.getDemandaDiaria(), i.getFactorEstacionalidad(),
                i.getStockSeguridad(), i.getPuntoReorden(), i.getStockActual(), i.getCoberturaDias(),
                i.getUnidadesSugeridas(), i.getCajasSugeridas(), i.getAjustePresupuesto(),
                i.getCostoEstimado(), i.getMargenEsperado(), i.getJustificacion(), i.getDecision(),
                i.getCajasAprobadas(), i.getMotivoRechazo(), i.getFechaDecision());
    }
}
