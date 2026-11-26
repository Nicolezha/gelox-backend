package com.gelox.backend.exportacion.dto;

import java.util.List;

/** RF67 — primeras filas del archivo con productos reales del proveedor y cantidades de ejemplo. */
public record VistaPreviaDTO(
        List<String> encabezados,
        List<List<String>> filas,
        List<String> productosSinCodigo
) {
}
