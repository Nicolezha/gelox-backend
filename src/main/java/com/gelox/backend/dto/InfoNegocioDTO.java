package com.gelox.backend.dto;

public record InfoNegocioDTO(
        String direccion,
        String barrio,
        String ciudad,
        String horario,
        String enlaceComoLlegar
) {}
