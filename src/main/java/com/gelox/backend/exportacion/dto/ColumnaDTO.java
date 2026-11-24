package com.gelox.backend.exportacion.dto;

import com.gelox.backend.entities.CampoPlantilla;

/** RF67 — columna de una plantilla GENERAR_FILAS (entrada y salida). Un campo desconocido → 400. */
public record ColumnaDTO(
        Integer orden,
        CampoPlantilla campo,
        String encabezado,
        String valorFijo
) {
}
