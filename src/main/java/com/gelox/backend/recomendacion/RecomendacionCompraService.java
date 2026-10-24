package com.gelox.backend.recomendacion;

import com.gelox.backend.entities.EstadoRecomendacion;
import com.gelox.backend.entities.ItemRecomendacion;
import com.gelox.backend.entities.OrigenRecomendacion;
import com.gelox.backend.entities.Producto;
import com.gelox.backend.entities.RecomendacionCompra;
import com.gelox.backend.entities.TipoEvento;
import com.gelox.backend.entities.Usuario;
import com.gelox.backend.recomendacion.DemandaRepository.DemandaDia;
import com.gelox.backend.recomendacion.MotorRecomendacionCompra.ItemCalculado;
import com.gelox.backend.recomendacion.MotorRecomendacionCompra.Resultado;
import com.gelox.backend.recomendacion.dto.ItemDTO;
import com.gelox.backend.recomendacion.dto.ProductoExcluidoDTO;
import com.gelox.backend.recomendacion.dto.RecomendacionDTO;
import com.gelox.backend.recomendacion.dto.RecomendacionGeneradaDTO;
import com.gelox.backend.repositories.ProductoRepository;
import com.gelox.backend.repositories.RecomendacionCompraRepository;
import com.gelox.backend.services.EventoSistemaService;
import com.gelox.backend.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * RF54 — genera la recomendación de compras con {@link MotorRecomendacionCompra}, la guarda con
 * todas las variables del cálculo y ofrece la del día a la voz.
 *
 * <p>{@code generar} y {@code recomendacionDelDia} son públicos porque los usa la voz (T115);
 * el rol lo valida el controlador o el handler de voz.</p>
 */
@Service
@RequiredArgsConstructor
@Transactional
public class RecomendacionCompraService {

    private static final ZoneId ZONA_BOGOTA = ZoneId.of("America/Bogota");
    private static final int DIAS_HISTORIAL_MINIMO = 90;
    private static final int DIAS_CON_VENTAS_MINIMO = 20;

    private final ProductoRepository productoRepo;
    private final DemandaRepository demandaRepo;
    private final RecomendacionCompraRepository recomendacionRepo;
    private final MotorRecomendacionCompra motor;
    private final EventoSistemaService eventoService;

    /**
     * Calcula y guarda la recomendación en GENERADA: un ítem por producto con cajas > 0 (también
     * los EXCLUIDO por presupuesto, con 0 cajas). Los productos excluidos por datos incompletos
     * solo se devuelven. Sin productos que comprar responde con la lista vacía.
     */
    public RecomendacionGeneradaDTO generar(BigDecimal presupuesto, OrigenRecomendacion origen, Usuario usuario) {
        if (presupuesto == null || presupuesto.signum() <= 0) {
            throw new IllegalArgumentException("El presupuesto debe ser mayor a cero");
        }
        UUID tenantId = TenantContext.requerido();
        LocalDate hoy = LocalDate.now(ZONA_BOGOTA);
        LocalDate desde = hoy.minusDays(MotorRecomendacionCompra.N - 1);

        // El filtro de Hibernate ya limita findAll al tenant; se verifica de nuevo por seguridad.
        List<Producto> productos = productoRepo.findAll().stream()
                .filter(p -> tenantId.equals(p.getTenantId()))
                .toList();
        List<DemandaDia> demanda = demandaRepo.demandaDiaria(tenantId, desde, hoy);
        Resultado resultado = motor.calcular(productos, demanda, presupuesto, hoy);

        int diasHistorial = demandaRepo.diasHistorial(tenantId);
        int diasConVentas = (int) demanda.stream()
                .filter(d -> d.unidades() > 0)
                .map(DemandaDia::fecha)
                .distinct()
                .count();

        RecomendacionCompra recomendacion = RecomendacionCompra.builder()
                .tenantId(tenantId)
                .usuario(usuario)
                .presupuesto(presupuesto)
                .costoTotal(resultado.costoTotal())
                .estado(EstadoRecomendacion.GENERADA)
                .origen(origen != null ? origen : OrigenRecomendacion.PANTALLA)
                .confianzaBaja(diasHistorial < DIAS_HISTORIAL_MINIMO || diasConVentas < DIAS_CON_VENTAS_MINIMO)
                .diasHistorial(diasHistorial)
                .diasConVentas(diasConVentas)
                .build();
        resultado.items().forEach(i -> recomendacion.getItems().add(toItem(i, recomendacion)));

        RecomendacionCompra guardada = recomendacionRepo.save(recomendacion);

        eventoService.registrarEvento(
                TipoEvento.RECOMENDACION_GENERADA,
                String.format("Recomendación #%s generada: %d productos sugeridos por %s.",
                        guardada.getId().toString().substring(0, 8).toUpperCase(),
                        guardada.getItems().size(),
                        NumberFormat.getCurrencyInstance(Locale.forLanguageTag("es-CO"))
                                .format(guardada.getCostoTotal())),
                usuario != null ? usuario.getId() : null);

        List<ProductoExcluidoDTO> excluidos = resultado.productosExcluidos().stream()
                .map(e -> new ProductoExcluidoDTO(e.producto().getId(), e.producto().getNombre(), e.motivo()))
                .toList();

        return new RecomendacionGeneradaDTO(
                guardada.getId(), guardada.getEstado(), guardada.getOrigen(), guardada.getPresupuesto(),
                guardada.getCostoTotal(), guardada.getCostoAprobado(), guardada.getConfianzaBaja(),
                guardada.getDiasHistorial(), guardada.getDiasConVentas(), guardada.getCreatedAt(),
                itemsDTO(guardada), excluidos);
    }

    /** La recomendación de hoy del tenant en GENERADA o EN_REVISION, si existe (voz). */
    @Transactional(readOnly = true)
    public Optional<RecomendacionDTO> recomendacionDelDia() {
        LocalDate hoy = LocalDate.now(ZONA_BOGOTA);
        return recomendacionRepo.buscarUltima(
                        TenantContext.requerido(),
                        List.of(EstadoRecomendacion.GENERADA, EstadoRecomendacion.EN_REVISION),
                        hoy.atStartOfDay(), hoy.plusDays(1).atStartOfDay())
                .map(r -> new RecomendacionDTO(
                        r.getId(), r.getEstado(), r.getOrigen(), r.getPresupuesto(), r.getCostoTotal(),
                        r.getCostoAprobado(), r.getConfianzaBaja(), r.getDiasHistorial(), r.getDiasConVentas(),
                        r.getPedidoProveedor() != null ? r.getPedidoProveedor().getId() : null,
                        r.getCreatedAt(), r.getUpdatedAt(), itemsDTO(r)));
    }

    // ══════════════════════════════════════════════════════════════════════
    // Mapeo
    // ══════════════════════════════════════════════════════════════════════

    private static ItemRecomendacion toItem(ItemCalculado i, RecomendacionCompra recomendacion) {
        return ItemRecomendacion.builder()
                .recomendacion(recomendacion)
                .producto(i.producto())
                .prioridad(i.prioridad())
                .urgencia(i.urgencia())
                .score(i.score())
                .demandaDiaria(i.demandaDiaria())
                .factorEstacionalidad(i.factorEstacionalidad())
                .stockSeguridad(i.stockSeguridad())
                .puntoReorden(i.puntoReorden())
                .stockActual(i.stockActual())
                .coberturaDias(i.coberturaDias())
                .unidadesSugeridas(i.unidadesSugeridas())
                .cajasSugeridas(i.cajasSugeridas())
                .ajustePresupuesto(i.ajustePresupuesto())
                .costoEstimado(i.costoEstimado())
                .margenEsperado(i.margenEsperado())
                .justificacion(i.justificacion())
                .build();
    }

    private static List<ItemDTO> itemsDTO(RecomendacionCompra r) {
        return r.getItems().stream()
                .sorted(Comparator.comparing(ItemRecomendacion::getPrioridad,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .map(i -> new ItemDTO(
                        i.getId(), i.getProducto().getId(), i.getProducto().getNombre(), i.getPrioridad(),
                        i.getUrgencia(), i.getScore(), i.getDemandaDiaria(), i.getFactorEstacionalidad(),
                        i.getStockSeguridad(), i.getPuntoReorden(), i.getStockActual(), i.getCoberturaDias(),
                        i.getUnidadesSugeridas(), i.getCajasSugeridas(), i.getAjustePresupuesto(),
                        i.getCostoEstimado(), i.getMargenEsperado(), i.getJustificacion(), i.getDecision(),
                        i.getCajasAprobadas(), i.getMotivoRechazo(), i.getFechaDecision()))
                .toList();
    }
}
