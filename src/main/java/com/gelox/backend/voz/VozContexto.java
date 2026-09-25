package com.gelox.backend.voz;

import com.gelox.backend.entities.Usuario;

import java.time.LocalDate;
import java.util.Map;

/**
 * Entrada que recibe un {@link IntencionHandler} para interpretar un comando
 * de voz ya clasificado. {@code pendiente} es la operación en curso que el
 * comando continúa (p. ej. "agrega 2 cajas de Solo Lack"), o {@code null}.
 */
public record VozContexto(String texto, Map<String, String> slots, double confianza,
                          Usuario usuario, LocalDate hoy, VozPendiente pendiente) {

    /** Sin pendiente: comandos que no continúan una operación en curso. */
    public VozContexto(String texto, Map<String, String> slots, double confianza,
                       Usuario usuario, LocalDate hoy) {
        this(texto, slots, confianza, usuario, hoy, null);
    }
}
