package com.gelox.backend.dto;

/** Datos de contacto de la landing (RF61); enlaceComoLlegar es el maps_url de la distribuidora. */
public record InfoNegocioDTO(
        String nombreComercial,
        String eslogan,
        String direccion,
        String barrio,
        String ciudad,
        String horario,
        String enlaceComoLlegar,
        String whatsapp
) {}
