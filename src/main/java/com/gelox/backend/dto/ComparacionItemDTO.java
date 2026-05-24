package com.gelox.backend.dto;

import java.util.UUID;

/**
 * Resultado de comparar lo solicitado vs lo recibido por producto (RF22).
 *
 * <ul>
 *   <li>{@code CORRECTO}  — diferencia == 0</li>
 *   <li>{@code SOBRANTE}  — diferencia > 0  (llegó más de lo pedido)</li>
 *   <li>{@code FALTANTE}  — diferencia < 0  (llegó menos de lo pedido)</li>
 * </ul>
 */
public record ComparacionItemDTO(
        UUID   productoId,
        String codigoTecnico,
        String nombre,
        int    cantidadSolicitada,
        int    cantidadRecibida,
        int    diferencia,         // positivo = sobrante, negativo = faltante
        String estado              // CORRECTO | SOBRANTE | FALTANTE
) {
    public static ComparacionItemDTO of(UUID id, String codigo, String nombre,
                                        int solicitada, int recibida) {
        int dif = recibida - solicitada;
        String est = dif == 0 ? "CORRECTO" : dif > 0 ? "SOBRANTE" : "FALTANTE";
        return new ComparacionItemDTO(id, codigo, nombre, solicitada, recibida, dif, est);
    }
}
