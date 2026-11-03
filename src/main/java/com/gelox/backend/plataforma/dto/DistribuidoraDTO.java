package com.gelox.backend.plataforma.dto;

import com.gelox.backend.entities.Distribuidora;
import com.gelox.backend.entities.EstadoDistribuidora;

import java.util.UUID;

/**
 * RF58/RF59 — Respuesta del panel del Superadministrador.
 * {@code correoEnviado} solo se completa al aprovisionar o reenviar acceso.
 */
public record DistribuidoraDTO(
        UUID id,
        String razonSocial,
        String nit,
        String nombreComercial,
        String subdominio,
        String correoGerente,
        String telefono,
        String direccion,
        String barrio,
        String ciudad,
        String plan,
        EstadoDistribuidora estado,
        String motivoEstado,
        String logoUrl,
        String faviconUrl,
        String colorPrimario,
        String colorSecundario,
        Boolean correoEnviado
) {

    public static DistribuidoraDTO from(Distribuidora d, Boolean correoEnviado) {
        return new DistribuidoraDTO(
                d.getId(),
                d.getRazonSocial(),
                d.getNit(),
                d.getNombreComercial(),
                d.getSubdominio(),
                d.getCorreoGerente(),
                d.getTelefono(),
                d.getDireccion(),
                d.getBarrio(),
                d.getCiudad(),
                d.getPlan(),
                d.getEstado(),
                d.getMotivoEstado(),
                d.getLogoUrl(),
                d.getFaviconUrl(),
                d.getColorPrimario(),
                d.getColorSecundario(),
                correoEnviado
        );
    }
}
