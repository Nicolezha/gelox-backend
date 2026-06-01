package com.gelox.backend.services;

import com.gelox.backend.dto.DetalleCalculadoDTO;
import com.gelox.backend.dto.DevolucionItemDTO;
import com.gelox.backend.dto.LiquidacionRequestDTO;
import com.gelox.backend.dto.LiquidacionResponseDTO;
import com.gelox.backend.entities.ItemPlanilla;
import com.gelox.backend.entities.PlanillaComerciante;
import com.gelox.backend.entities.TipoMovimiento;
import com.gelox.backend.entities.Usuario;
import com.gelox.backend.exceptions.DevolucionInvalidaException;
import com.gelox.backend.exceptions.PlanillaNoEncontradaException;
import com.gelox.backend.exceptions.PlanillaYaLiquidadaException;
import com.gelox.backend.repositories.ItemPlanillaRepository;
import com.gelox.backend.repositories.PlanillaComercianteRepository;
import com.gelox.backend.security.RequiereRol;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class LiquidacionService {

    private final PlanillaComercianteRepository planillaRepository;
    private final ItemPlanillaRepository        itemPlanillaRepository;
    private final InventarioService             inventarioService;

    @RequiereRol({"ADMINISTRADOR", "ENCARGADO_VENTAS"})
    public LiquidacionResponseDTO liquidarPlanilla(UUID planillaId, LiquidacionRequestDTO request, Usuario usuario) {

        // 1. Obtener planilla; lanzar 404 si no existe
        PlanillaComerciante planilla = planillaRepository.findById(planillaId)
                .orElseThrow(() -> new PlanillaNoEncontradaException(
                        "Planilla no encontrada: " + planillaId));

        // 2. Validar que la planilla no esté ya cerrada/liquidada
        if (Boolean.TRUE.equals(planilla.getCerrada())) {
            throw new PlanillaYaLiquidadaException(
                    "La planilla " + planillaId + " ya fue liquidada.");
        }

        // 3. Procesar cada devolución
        List<DetalleCalculadoDTO> detalles = new ArrayList<>();
        BigDecimal gananciaTotal = BigDecimal.ZERO;

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

            // c. Calcular
            int unidadesVendidas = despachadas - devueltas;
            BigDecimal ganancia  = item.getPrecioVenta()
                    .multiply(BigDecimal.valueOf(unidadesVendidas));

            // d. Persistir devolución en el detalle
            item.setUnidadesDevueltas(devueltas);
            itemPlanillaRepository.save(item);

            // Las unidades devueltas quedan con el comerciante (no regresan al stock global).
            // Se recuperan como saldo_anterior en la siguiente planilla de ese comerciante.

            gananciaTotal = gananciaTotal.add(ganancia);

            detalles.add(new DetalleCalculadoDTO(
                    item.getId(),
                    despachadas,
                    devueltas,
                    unidadesVendidas,
                    item.getPrecioVenta(),
                    ganancia));
        }

        // 5-6. Marcar planilla como liquidada y persistir
        LocalDateTime ahora = LocalDateTime.now();
        planilla.setCerrada(true);
        planilla.setTimestampCierre(ahora);
        planilla.setTotalGanancia(gananciaTotal);
        planillaRepository.save(planilla);

        // 7. Retornar respuesta
        return new LiquidacionResponseDTO(planilla.getId(), ahora, detalles, gananciaTotal);
    }
}
