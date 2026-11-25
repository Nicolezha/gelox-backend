package com.gelox.backend.credito.dto;

import com.gelox.backend.entities.AbonoComerciante;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** RF65 — un abono registrado. */
public record AbonoDTO(
        UUID id,
        LocalDate fecha,
        BigDecimal valor,
        String observacion,
        String usuarioNombre
) {
    public static AbonoDTO from(AbonoComerciante a) {
        return new AbonoDTO(a.getId(), a.getFecha(), a.getValor(), a.getObservacion(), a.getUsuario().getNombre());
    }
}
