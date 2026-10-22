package com.gelox.backend.dto;

/**
 * Campos de texto del PUT /api/distribuidora/identidad (multipart); logo y favicon llegan aparte.
 */
public record ActualizarIdentidadRequest(
        String nombreComercial,
        String colorPrimario,
        String colorSecundario
) {}
