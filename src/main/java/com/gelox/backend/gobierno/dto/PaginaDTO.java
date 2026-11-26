package com.gelox.backend.gobierno.dto;

import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

/**
 * Página con la forma de Spring que espera el panel: { content, totalElements, totalPages, number, size }.
 * Se arma a mano porque serializar PageImpl directamente no tiene una forma JSON estable.
 */
public record PaginaDTO<T>(List<T> content, long totalElements, int totalPages, int number, int size) {

    public static <E, T> PaginaDTO<T> de(Page<E> pagina, Function<List<E>, List<T>> convertir) {
        return new PaginaDTO<>(convertir.apply(pagina.getContent()), pagina.getTotalElements(),
                Math.max(1, pagina.getTotalPages()), pagina.getNumber(), pagina.getSize());
    }
}
