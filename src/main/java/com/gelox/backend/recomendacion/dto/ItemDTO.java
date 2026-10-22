package com.gelox.backend.recomendacion.dto;

import com.gelox.backend.entities.AjustePresupuesto;
import com.gelox.backend.entities.DecisionItem;
import com.gelox.backend.entities.UrgenciaRecomendacion;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Producto sugerido de una recomendación: variables del cálculo, resultado y decisión (RF55).
 */
public record ItemDTO(
        UUID id,
        UUID productoId,
        String productoNombre,
        Integer prioridad,
        UrgenciaRecomendacion urgencia,
        BigDecimal score,
        BigDecimal demandaDiaria,
        BigDecimal factorEstacionalidad,
        BigDecimal stockSeguridad,
        BigDecimal puntoReorden,
        Integer stockActual,
        BigDecimal coberturaDias,
        Integer unidadesSugeridas,
        Integer cajasSugeridas,
        AjustePresupuesto ajustePresupuesto,
        BigDecimal costoEstimado,
        BigDecimal margenEsperado,
        String justificacion,
        DecisionItem decision,
        Integer cajasAprobadas,
        String motivoRechazo,
        LocalDateTime fechaDecision
) {}
