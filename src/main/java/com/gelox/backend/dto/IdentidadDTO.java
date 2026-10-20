package com.gelox.backend.dto;

import com.gelox.backend.entities.Distribuidora;

/**
 * Identidad visual de una distribuidora (T119): la usan el frontend para pintar la interfaz,
 * la landing pública y los documentos impresos.
 */
public record IdentidadDTO(
        String nombreComercial,
        String logoUrl,
        String faviconUrl,
        String colorPrimario,
        String colorSecundario
) {
    public static IdentidadDTO from(Distribuidora d) {
        return new IdentidadDTO(
                d.getNombreComercial(),
                d.getLogoUrl(),
                d.getFaviconUrl(),
                d.getColorPrimario(),
                d.getColorSecundario()
        );
    }
}
