package com.gelox.backend.gobierno.dto;

import com.gelox.backend.entities.EstadoDistribuidora;
import com.gelox.backend.gobierno.PlanSuscripcion;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * RF70 — Plan de suscripción para el panel (contrato del mock de T193):
 * { id, nombre, descripcion, precioMensual, maxUsuarios, maxProductos, maxComerciantes,
 *   maxAlmacenamientoMb, maxLlamadasIaMes, totalDistribuidoras,
 *   distribuidoras: [{ id, nombreComercial, estado, uso: { usuarios, productos, comerciantes,
 *                      almacenamientoMb, llamadasIaMes } }] }
 * En distribuidoras[].uso van solo los números usados, para avisar al bajar una cuota.
 */
public record PlanDTO(
        UUID id,
        String nombre,
        String descripcion,
        BigDecimal precioMensual,
        Integer maxUsuarios,
        Integer maxProductos,
        Integer maxComerciantes,
        Integer maxAlmacenamientoMb,
        Integer maxLlamadasIaMes,
        int totalDistribuidoras,
        List<DistribuidoraDelPlan> distribuidoras
) {

    public record DistribuidoraDelPlan(UUID id, String nombreComercial, EstadoDistribuidora estado,
                                       Map<String, Number> uso) {
    }

    public static PlanDTO from(PlanSuscripcion p, List<DistribuidoraDelPlan> distribuidoras) {
        return new PlanDTO(p.getId(), p.getNombre(), p.getDescripcion(), p.getPrecioMensual(),
                p.getMaxUsuarios(), p.getMaxProductos(), p.getMaxComerciantes(),
                p.getMaxAlmacenamientoMb(), p.getMaxLlamadasIaMes(),
                distribuidoras.size(), distribuidoras);
    }
}
