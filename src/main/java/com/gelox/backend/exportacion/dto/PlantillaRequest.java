package com.gelox.backend.exportacion.dto;

import com.gelox.backend.entities.ModoPlantilla;

import java.util.List;

/**
 * RF67 — cuerpo de PUT /plantilla y de POST /plantilla/vista-previa (FP-15 §2.18.1).
 * RELLENAR_CATALOGO usa archivoBaseUrl, filaEncabezado y los tres encabezados;
 * GENERAR_FILAS usa columnas. Las homologaciones son opcionales y se guardan junto con la plantilla.
 */
public record PlantillaRequest(
        ModoPlantilla modo,
        String archivoBaseUrl,
        Integer filaEncabezado,
        String encabezadoCodigo,
        String encabezadoCantidad,
        String encabezadoUnidad,
        List<ColumnaDTO> columnas,
        List<HomologacionRequest> homologaciones
) {
}
