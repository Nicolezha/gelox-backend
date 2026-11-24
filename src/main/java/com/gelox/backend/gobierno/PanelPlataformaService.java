package com.gelox.backend.gobierno;

import com.gelox.backend.entities.Distribuidora;
import com.gelox.backend.entities.EstadoDistribuidora;
import com.gelox.backend.gobierno.dto.PanelPlataformaDTO;
import com.gelox.backend.repositories.DistribuidoraRepository;
import com.gelox.backend.security.RequiereRol;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * RF69 — Dashboard del Superadministrador. El Superadministrador no tiene
 * TenantContext ni distribuidora propia: estas consultas son de todas las
 * distribuidoras, igual que {@code DistribuidoraService.listar}.
 */
@Service
@RequiredArgsConstructor
public class PanelPlataformaService {

    private static final ZoneId ZONA_BOGOTA = ZoneId.of("America/Bogota");

    /** La serie cubre al menos los 6 meses (5 completos + el actual) que terminan en "hasta". */
    private static final long MESES_SERIE = 5;

    private final PanelPlataformaRepository panelRepository;
    private final DistribuidoraRepository distribuidoraRepository;
    private final PlanSuscripcionRepository planRepository;
    private final CuotaService cuotaService;

    @RequiereRol("SUPERADMINISTRADOR")
    @Transactional(readOnly = true)
    public PanelPlataformaDTO generar(LocalDate desde, LocalDate hasta) {
        LocalDate hoy = LocalDate.now(ZONA_BOGOTA);
        LocalDate finEfectivo = hasta != null ? hasta : hoy;
        LocalDate inicioEfectivo = desde != null ? desde : finEfectivo.minusDays(29);

        if (inicioEfectivo.isAfter(finEfectivo)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "La fecha inicial no puede ser posterior a la final");
        }

        List<Distribuidora> todas = distribuidoraRepository.findAll();

        PanelPlataformaDTO.Distribuidoras distribuidoras = contarPorEstado(todas);
        PanelPlataformaDTO.Usuarios usuarios = new PanelPlataformaDTO.Usuarios(
                panelRepository.usuariosRegistrados(),
                panelRepository.usuariosHabilitados(),
                panelRepository.usuariosNuevos(inicioEfectivo, finEfectivo));

        PanelPlataformaRepository.FilaConteoValor ventasTotal = panelRepository.totalVentas(inicioEfectivo, finEfectivo);
        PanelPlataformaDTO.ValorConteo ventas = new PanelPlataformaDTO.ValorConteo(ventasTotal.valor(), ventasTotal.conteo());

        PanelPlataformaRepository.FilaConteoValor pedidosTotal =
                panelRepository.totalPedidosProveedor(inicioEfectivo, finEfectivo);
        PanelPlataformaDTO.PedidosProveedor pedidosProveedor =
                new PanelPlataformaDTO.PedidosProveedor(pedidosTotal.conteo(), pedidosTotal.valor());

        long planillasLiquidadas = panelRepository.totalPlanillasLiquidadas(inicioEfectivo, finEfectivo);

        BigDecimal usadoMb = BigDecimal.valueOf(panelRepository.almacenamientoUsadoBytes())
                .divide(BigDecimal.valueOf(1024L * 1024L), 2, java.math.RoundingMode.HALF_UP);
        PanelPlataformaDTO.Almacenamiento almacenamiento =
                new PanelPlataformaDTO.Almacenamiento(usadoMb, panelRepository.almacenamientoAsignadoMb());

        long llamadasIa = panelRepository.totalLlamadasIa(inicioEfectivo, finEfectivo);
        long voz = panelRepository.totalComandosVoz(inicioEfectivo, finEfectivo);
        long recomendaciones = panelRepository.totalRecomendaciones(inicioEfectivo, finEfectivo);
        PanelPlataformaDTO.Ia ia = new PanelPlataformaDTO.Ia(llamadasIa, voz, recomendaciones);

        List<PanelPlataformaDTO.PorDistribuidora> porDistribuidora = porDistribuidora(todas, inicioEfectivo, finEfectivo);

        LocalDate inicioSerie = finEfectivo.minusMonths(MESES_SERIE).withDayOfMonth(1);
        if (inicioEfectivo.isBefore(inicioSerie)) inicioSerie = inicioEfectivo;
        List<PanelPlataformaDTO.PuntoSerie> serie = panelRepository.seriePorDia(inicioSerie, finEfectivo).stream()
                .map(p -> new PanelPlataformaDTO.PuntoSerie(p.fecha(), p.ventas(), p.transacciones(),
                        p.llamadasIa(), p.usuariosNuevos()))
                .toList();

        return new PanelPlataformaDTO(inicioEfectivo, finEfectivo, LocalDateTime.now(ZONA_BOGOTA),
                distribuidoras, usuarios, ventas, pedidosProveedor, planillasLiquidadas,
                almacenamiento, ia, porDistribuidora, serie);
    }

    private PanelPlataformaDTO.Distribuidoras contarPorEstado(List<Distribuidora> todas) {
        Map<String, Long> porEstado = new LinkedHashMap<>();
        for (EstadoDistribuidora estado : EstadoDistribuidora.values()) {
            porEstado.put(estado.name(), 0L);
        }
        for (Distribuidora d : todas) {
            porEstado.merge(d.getEstado().name(), 1L, Long::sum);
        }
        return new PanelPlataformaDTO.Distribuidoras(todas.size(), porEstado);
    }

    private List<PanelPlataformaDTO.PorDistribuidora> porDistribuidora(
            List<Distribuidora> todas, LocalDate desde, LocalDate hasta) {

        Map<UUID, PanelPlataformaRepository.FilaConteoValor> ventasPorTenant = new LinkedHashMap<>();
        panelRepository.ventasPorDistribuidora(desde, hasta).forEach(f -> ventasPorTenant.put(f.tenantId(), f));

        Map<UUID, Long> iaPorTenant = new LinkedHashMap<>();
        panelRepository.llamadasIaPorDistribuidora(desde, hasta).forEach(f -> iaPorTenant.put(f.tenantId(), f.conteo()));

        Map<UUID, PlanSuscripcion> planesCache = new LinkedHashMap<>();

        return todas.stream()
                .map(d -> {
                    PanelPlataformaRepository.FilaConteoValor ventasFila = ventasPorTenant.get(d.getId());
                    PanelPlataformaDTO.ValorConteo ventas = ventasFila != null
                            ? new PanelPlataformaDTO.ValorConteo(ventasFila.valor(), ventasFila.conteo())
                            : new PanelPlataformaDTO.ValorConteo(BigDecimal.ZERO, 0);

                    PlanSuscripcion plan = d.getPlanId() == null ? null
                            : planesCache.computeIfAbsent(d.getPlanId(),
                                    id -> planRepository.findById(id).orElse(null));

                    return new PanelPlataformaDTO.PorDistribuidora(
                            d.getId(), d.getNombreComercial(), d.getRazonSocial(),
                            d.getPlanId(), plan != null ? plan.getNombre() : null, d.getEstado().name(),
                            ventas, iaPorTenant.getOrDefault(d.getId(), 0L),
                            cuotaService.uso(d.getId(), plan));
                })
                .toList();
    }
}
