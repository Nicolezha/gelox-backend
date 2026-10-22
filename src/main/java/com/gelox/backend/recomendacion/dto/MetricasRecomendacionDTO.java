package com.gelox.backend.recomendacion.dto;

/**
 * Métricas de precisión del modelo de recomendación en un rango de fechas (RF57).
 *
 * @param tasaAprobacion    (aprobados + modificados) ÷ ítems sugeridos; 0 si no hay ítems
 * @param precisionCantidad promedio de 1 − |cajas reales − cajas sugeridas| ÷ cajas sugeridas;
 *                          nulo si ninguna recomendación del rango tiene pedido
 */
public record MetricasRecomendacionDTO(
        long generadas,
        long itemsSugeridos,
        long aprobados,
        long modificados,
        long rechazados,
        double tasaAprobacion,
        Double precisionCantidad
) {}
