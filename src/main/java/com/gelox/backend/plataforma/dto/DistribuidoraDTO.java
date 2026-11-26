package com.gelox.backend.plataforma.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.gelox.backend.entities.Distribuidora;
import com.gelox.backend.entities.EstadoDistribuidora;
import com.gelox.backend.gobierno.CuotaService.CuotaExcedida;
import com.gelox.backend.gobierno.CuotaService.UsoCuota;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * RF58/RF59/RF70 — Distribuidora en el panel del Superadministrador (contrato del mock de T193).
 * <ul>
 *   <li>{@code plan} repite {@code planNombre} mientras exista el frontend anterior.</li>
 *   <li>{@code uso} ({usado, limite} por cuota) solo viene en el detalle y al asignar plan.</li>
 *   <li>{@code advertencias} solo al asignar un plan cuyo límite el uso actual ya supera.</li>
 *   <li>{@code correoEnviado} solo al aprovisionar o reenviar acceso.</li>
 * </ul>
 */
public record DistribuidoraDTO(
        UUID id,
        String razonSocial,
        String nit,
        String nombreComercial,
        String subdominio,
        String nombreGerente,
        String correoGerente,
        String telefono,
        String direccion,
        String barrio,
        String ciudad,
        UUID planId,
        String planNombre,
        String plan,
        LocalDateTime fechaPlan,
        EstadoDistribuidora estado,
        String motivoEstado,
        LocalDateTime fechaEstado,
        LocalDateTime createdAt,
        Long usuariosActivos,
        String logoUrl,
        String faviconUrl,
        String colorPrimario,
        String colorSecundario,
        @JsonInclude(JsonInclude.Include.NON_NULL) Map<String, UsoCuota> uso,
        @JsonInclude(JsonInclude.Include.NON_NULL) List<CuotaExcedida> advertencias,
        @JsonInclude(JsonInclude.Include.NON_NULL) Boolean correoEnviado
) {

    /** Datos del panel que no están en la entidad (los calcula DistribuidoraService). */
    public record Extra(String nombreGerente, String planNombre, LocalDateTime fechaPlan, Long usuariosActivos,
                        Map<String, UsoCuota> uso, List<CuotaExcedida> advertencias) {

        public static final Extra VACIO = new Extra(null, null, null, null, null, null);
    }

    /** Sin los datos calculados del panel. */
    public static DistribuidoraDTO from(Distribuidora d, Boolean correoEnviado) {
        return from(d, correoEnviado, Extra.VACIO);
    }

    public static DistribuidoraDTO from(Distribuidora d, Boolean correoEnviado, Extra extra) {
        String planNombre = extra.planNombre() != null ? extra.planNombre() : d.getPlan();
        return new DistribuidoraDTO(
                d.getId(),
                d.getRazonSocial(),
                d.getNit(),
                d.getNombreComercial(),
                d.getSubdominio(),
                extra.nombreGerente(),
                d.getCorreoGerente(),
                d.getTelefono(),
                d.getDireccion(),
                d.getBarrio(),
                d.getCiudad(),
                d.getPlanId(),
                planNombre,
                planNombre,
                extra.fechaPlan() != null ? extra.fechaPlan() : d.getCreatedAt(),
                d.getEstado(),
                d.getMotivoEstado(),
                d.getFechaEstado(),
                d.getCreatedAt(),
                extra.usuariosActivos(),
                d.getLogoUrl(),
                d.getFaviconUrl(),
                d.getColorPrimario(),
                d.getColorSecundario(),
                extra.uso(),
                extra.advertencias(),
                correoEnviado
        );
    }
}
