package com.gelox.backend.exportacion;

import com.gelox.backend.dto.ReporteFinancieroCompletoDTO;
import com.gelox.backend.dto.Top5ComerciantesDTO;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/**
 * Todo lo que necesitan los archivos del reporte: los mismos datos de la pantalla Reportes
 * (sin cálculos nuevos) más la identidad de la distribuidora para el encabezado.
 *
 * @param logo bytes del logo ya descargado; nulo si no hay logo o no se pudo descargar
 */
record DatosReporte(
        String nombreComercial,
        String colorPrimario,
        byte[] logo,
        String generadoPor,
        LocalDateTime generadoEn,
        ReporteFinancieroCompletoDTO reporte,
        List<Top5ComerciantesDTO.ComercianteIngresoDTO> top5,
        Set<SeccionReporte> secciones
) {
    boolean incluye(SeccionReporte seccion) {
        return secciones.contains(seccion);
    }
}
