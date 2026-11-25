package com.gelox.backend.services;

import com.gelox.backend.credito.CalculoLiquidacion;
import com.gelox.backend.credito.CalculoLiquidacionResolver;
import com.gelox.backend.credito.CreditoService;
import com.gelox.backend.credito.EsquemaLiquidacionService;
import com.gelox.backend.dto.DevolucionItemDTO;
import com.gelox.backend.dto.LiquidacionRequestDTO;
import com.gelox.backend.dto.LiquidacionResponseDTO;
import com.gelox.backend.entities.EsquemaLiquidacion;
import com.gelox.backend.entities.ItemPlanilla;
import com.gelox.backend.entities.PlanillaComerciante;
import com.gelox.backend.entities.TipoEsquemaLiquidacion;
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

        // 3. Procesar cada devolución y acumular los totales de TODA la planilla
        //    (COMISION y TARIFA_FIJA no son aditivos por ítem — espejo de calcularLiquidacion() del frontend)
        BigDecimal totalPublico = BigDecimal.ZERO;
        BigDecimal totalComerciante = BigDecimal.ZERO;
        int unidadesTotal = 0;
        List<ItemPlanilla> itemsProcesados = new ArrayList<>();
        List<BigDecimal> pvpPorItem = new ArrayList<>();
        List<Integer> vendidasPorItem = new ArrayList<>();
        boolean algunPrecioPublico = false;

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

            int unidadesVendidas = despachadas - devueltas;
            BigDecimal precioComerciante = item.getPrecioVenta();
            BigDecimal precioPublico = item.tienePrecioPublico() ? item.getPrecioPublico() : null;
            if (precioPublico != null) algunPrecioPublico = true;

            totalComerciante = totalComerciante.add(precioComerciante.multiply(BigDecimal.valueOf(unidadesVendidas)));
            totalPublico = totalPublico.add(item.precioPublicoEfectivo().multiply(BigDecimal.valueOf(unidadesVendidas)));
            unidadesTotal += unidadesVendidas;

            // c. Persistir la devolución
            item.setUnidadesDevueltas(devueltas);
            itemsProcesados.add(item);
            pvpPorItem.add(precioPublico);
            vendidasPorItem.add(unidadesVendidas);

            // Las unidades devueltas quedan con el comerciante (no regresan al stock global).
            // Se recuperan como saldo_anterior en la siguiente planilla de ese comerciante.
        }

        // 4. Aplicar el esquema vigente a los totales de la planilla
        CalculoLiquidacion.Resultado resultado = calculo.calcular(totalPublico, totalComerciante, unidadesTotal, esquema.getValor());
        BigDecimal montoCobrado = resultado.montoCobrado();
        BigDecimal gananciaComerciante = resultado.gananciaComerciante();

        // MARGEN es aditivo por ítem: guardar la ganancia de cada uno (útil para el comprobante impreso)
        if (esquema.getTipo() == TipoEsquemaLiquidacion.MARGEN) {
            for (int i = 0; i < itemsProcesados.size(); i++) {
                ItemPlanilla item = itemsProcesados.get(i);
                BigDecimal pvp = pvpPorItem.get(i);
                item.setGananciaComerciante(pvp == null ? null
                        : pvp.subtract(item.getPrecioVenta()).multiply(BigDecimal.valueOf(vendidasPorItem.get(i))));
            }
        }
        itemsProcesados.forEach(itemPlanillaRepository::save);

        // En MARGEN sin ningún precio al público la ganancia no se conoce (null). En COMISION y
        // TARIFA_FIJA se descuenta del cobro, así que siempre se informa.
        BigDecimal gananciaInformada = algunPrecioPublico || esquema.getTipo() != TipoEsquemaLiquidacion.MARGEN
                ? gananciaComerciante : null;

        BigDecimal efectivoRecibido = request.efectivoRecibido() != null ? request.efectivoRecibido() : montoCobrado;
        BigDecimal saldo = montoCobrado.subtract(efectivoRecibido).max(BigDecimal.ZERO);

        // RF65 — fecha límite de pago: solo si queda saldo (fecha de cierre + plazo de la política efectiva)
        int plazoDias = creditoService.plazoDiasEfectivo(planilla.getComerciante().getId());
        LocalDateTime ahora = LocalDateTime.now(ZONA_BOGOTA);
        var fechaLimitePago = saldo.signum() > 0 ? ahora.toLocalDate().plusDays(plazoDias) : null;

        // 5. Marcar planilla como liquidada y persistir
        planilla.setCerrada(true);
        planilla.setTimestampCierre(ahora);
        planilla.setTotalGanancia(montoCobrado);
        planilla.setGananciaComerciante(gananciaInformada);
        planilla.setEsquemaAplicado(esquema.getTipo());
        planilla.setValorEsquema(esquema.getValor());
        planilla.setEfectivoRecibido(efectivoRecibido);
        planilla.setFechaLimitePago(fechaLimitePago);
        planillaRepository.save(planilla);

        // RF11 — el cierre (liquidación) del comerciante aparece en el feed del dashboard
        eventoSistemaService.registrarEvento(
                TipoEvento.CIERRE_CAJA,
                String.format("Cierre de planilla de %s. Total liquidado: $%s.",
                        planilla.getComerciante().getNombre(), montoCobrado.toPlainString()),
                usuario != null ? usuario.getId() : null);

        // 6. Retornar comprobante
        return new LiquidacionResponseDTO(planilla.getId(), ahora, esquema.getTipo(), esquema.getValor(),
                totalPublico, gananciaInformada, montoCobrado,
                efectivoRecibido, saldo, fechaLimitePago);
    }
}
