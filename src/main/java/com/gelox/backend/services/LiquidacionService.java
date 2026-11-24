package com.gelox.backend.services;

import com.gelox.backend.credito.CalculoLiquidacion;
import com.gelox.backend.credito.CalculoLiquidacionResolver;
import com.gelox.backend.credito.CreditoService;
import com.gelox.backend.credito.EsquemaLiquidacionService;
import com.gelox.backend.dto.DetalleCalculadoDTO;
import com.gelox.backend.dto.DevolucionItemDTO;
import com.gelox.backend.dto.LiquidacionRequestDTO;
import com.gelox.backend.dto.LiquidacionResponseDTO;
import com.gelox.backend.entities.EsquemaLiquidacion;
import com.gelox.backend.entities.ItemPlanilla;
import com.gelox.backend.entities.PlanillaComerciante;
import com.gelox.backend.entities.TipoEvento;
import com.gelox.backend.entities.Usuario;
import com.gelox.backend.exceptions.DevolucionInvalidaException;
import com.gelox.backend.exceptions.PlanillaNoEncontradaException;
import com.gelox.backend.exceptions.PlanillaYaLiquidadaException;
import com.gelox.backend.gobierno.Auditable;
import com.gelox.backend.repositories.ItemPlanillaRepository;
import com.gelox.backend.repositories.PlanillaComercianteRepository;
import com.gelox.backend.security.RequiereRol;
import com.gelox.backend.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class LiquidacionService {

    private static final ZoneId ZONA_BOGOTA = ZoneId.of("America/Bogota");

    private final PlanillaComercianteRepository planillaRepository;
    private final ItemPlanillaRepository        itemPlanillaRepository;
    private final InventarioService             inventarioService;
    private final EventoSistemaService          eventoSistemaService;   // RF11
    private final EsquemaLiquidacionService      esquemaLiquidacionService;   // RF66
    private final CalculoLiquidacionResolver     calculoResolver;              // RF66
    private final CreditoService                 creditoService;              // RF65

    @RequiereRol({"ADMINISTRADOR", "ENCARGADO_VENTAS"})
    @Auditable(accion = "PLANILLA_LIQUIDAR", entidad = "PlanillaComerciante")
    public LiquidacionResponseDTO liquidarPlanilla(UUID planillaId, LiquidacionRequestDTO request, Usuario usuario) {

        // 1. Obtener planilla; lanzar 404 si no existe
        PlanillaComerciante planilla = planillaRepository.findById(planillaId)
                .filter(TenantContext::pertenece)
                .orElseThrow(() -> new PlanillaNoEncontradaException(
                        "Planilla no encontrada: " + planillaId));

        // 2. Validar que la planilla no esté ya cerrada/liquidada
        if (Boolean.TRUE.equals(planilla.getCerrada())) {
            throw new PlanillaYaLiquidadaException(
                    "La planilla " + planillaId + " ya fue liquidada.");
        }

        // RF66 — esquema vigente de la distribuidora, con el que se liquida esta planilla
        EsquemaLiquidacion esquema = esquemaLiquidacionService.vigente();
        CalculoLiquidacion calculo = calculoResolver.paraTipo(esquema.getTipo());

        // 3. Procesar cada devolución
        List<DetalleCalculadoDTO> detalles = new ArrayList<>();
        BigDecimal gananciaTotal = BigDecimal.ZERO;
        BigDecimal gananciaComercianteTotal = BigDecimal.ZERO;
        boolean algunaGananciaComerciante = false;

        for (DevolucionItemDTO dev : request.devoluciones()) {

            // a. Obtener el detalle validando que pertenezca a esta planilla
            ItemPlanilla item = itemPlanillaRepository
                    .findByIdAndPlanillaId(dev.detalleId(), planillaId)
                    .orElseThrow(() -> new DevolucionInvalidaException(
                            "El ítem " + dev.detalleId() + " no pertenece a la planilla " + planillaId));

            int despachadas = item.getUnidadesDespachadas();
            int devueltas   = dev.unidadesDevueltas();

            // b. Validar que unidadesDevueltas <= unidadesDespachadas
            if (devueltas > despachadas) {
                throw new DevolucionInvalidaException(
                        "Las unidades devueltas (" + devueltas + ") no pueden superar las unidades "
                        + "despachadas (" + despachadas + ") para el ítem " + dev.detalleId());
            }

            // c. Calcular según el esquema de liquidación vigente (RF66)
            int unidadesVendidas = despachadas - devueltas;
            CalculoLiquidacion.Linea linea = calculo.calcular(
                    item.getPrecioVenta(), item.getPrecioPublico(), unidadesVendidas, esquema.getValor());

            // d. Persistir devolución y ganancia del comerciante en el detalle
            item.setUnidadesDevueltas(devueltas);
            item.setGananciaComerciante(linea.gananciaComerciante());
            itemPlanillaRepository.save(item);

            // Las unidades devueltas quedan con el comerciante (no regresan al stock global).
            // Se recuperan como saldo_anterior en la siguiente planilla de ese comerciante.

            gananciaTotal = gananciaTotal.add(linea.montoCobrado());
            if (linea.gananciaComerciante() != null) {
                algunaGananciaComerciante = true;
                gananciaComercianteTotal = gananciaComercianteTotal.add(linea.gananciaComerciante());
            }

            detalles.add(new DetalleCalculadoDTO(
                    item.getId(),
                    despachadas,
                    devueltas,
                    unidadesVendidas,
                    item.getPrecioVenta(),
                    linea.montoCobrado(),
                    linea.gananciaComerciante()));
        }

        BigDecimal gananciaComercianteFinal = algunaGananciaComerciante ? gananciaComercianteTotal : null;
        BigDecimal efectivoRecibido = request.efectivoRecibido() != null ? request.efectivoRecibido() : gananciaTotal;

        // RF65 — fecha límite de pago: fecha de cierre + plazo de la política de crédito efectiva del comerciante
        int plazoDias = creditoService.plazoDiasEfectivo(planilla.getComerciante().getId());
        LocalDateTime ahora = LocalDateTime.now(ZONA_BOGOTA);
        var fechaLimitePago = ahora.toLocalDate().plusDays(plazoDias);

        // 5-6. Marcar planilla como liquidada y persistir
        planilla.setCerrada(true);
        planilla.setTimestampCierre(ahora);
        planilla.setTotalGanancia(gananciaTotal);
        planilla.setGananciaComerciante(gananciaComercianteFinal);
        planilla.setEsquemaAplicado(esquema.getTipo());
        planilla.setValorEsquema(esquema.getValor());
        planilla.setEfectivoRecibido(efectivoRecibido);
        planilla.setFechaLimitePago(fechaLimitePago);
        planillaRepository.save(planilla);

        // RF11 — el cierre (liquidación) del comerciante aparece en el feed del dashboard
        eventoSistemaService.registrarEvento(
                TipoEvento.CIERRE_CAJA,
                String.format("Cierre de planilla de %s. Total liquidado: $%s.",
                        planilla.getComerciante().getNombre(), gananciaTotal.toPlainString()),
                usuario != null ? usuario.getId() : null);

        // 7. Retornar respuesta
        return new LiquidacionResponseDTO(planilla.getId(), ahora, detalles, gananciaTotal,
                esquema.getTipo(), gananciaComercianteFinal, efectivoRecibido, fechaLimitePago);
    }
}
