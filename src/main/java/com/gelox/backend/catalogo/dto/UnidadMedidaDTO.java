package com.gelox.backend.catalogo.dto;

import com.gelox.backend.entities.UnidadMedida;

import java.util.UUID;

public record UnidadMedidaDTO(UUID id, String codigo, String nombre) {

    public static UnidadMedidaDTO de(UnidadMedida u) {
        return new UnidadMedidaDTO(u.getId(), u.getCodigo(), u.getNombre());
    }
}
