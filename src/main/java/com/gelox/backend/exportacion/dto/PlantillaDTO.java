package com.gelox.backend.exportacion.dto;

import com.gelox.backend.entities.ModoPlantilla;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * RF67 — plantilla de un proveedor. {@code personalizada} es false cuando el proveedor aún no guardó
 * la suya: se devuelve la estándar de Nutresa (RELLENAR_CATALOGO) y {@code actualizadoEn} va nulo.
 */
public record PlantillaDTO(
        UUID proveedorId,
        boolean personalizada,
        ModoPlantilla modo,
        String archivoBaseUrl,
        int filaEncabezado,
        String encabezadoCodigo,
        String encabezadoCantidad,
        String encabezadoUnidad,
        List<ColumnaDTO> columnas,
        List<HomologacionDTO> homologaciones,
        LocalDateTime actualizadoEn
) {
}
