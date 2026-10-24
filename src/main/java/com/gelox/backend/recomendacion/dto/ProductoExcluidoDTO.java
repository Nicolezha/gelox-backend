package com.gelox.backend.recomendacion.dto;

import java.util.UUID;

/**
 * Producto que no entró al cálculo de la recomendación: inactivo, sin unidades por caja o sin
 * precio de costo (RF54). Solo se devuelve en la respuesta; no se guarda.
 */
public record ProductoExcluidoDTO(
        UUID productoId,
        String nombre,
        String motivo
) {}
