package com.gelox.backend.catalogo.dto;

import com.gelox.backend.entities.Categoria;

import java.util.UUID;

public record CategoriaDTO(UUID id, String nombre, Integer orden, Boolean activo) {

    public static CategoriaDTO de(Categoria c) {
        return new CategoriaDTO(c.getId(), c.getNombre(), c.getOrden(), c.getActivo());
    }
}
