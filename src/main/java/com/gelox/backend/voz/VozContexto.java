package com.gelox.backend.voz;

import com.gelox.backend.entities.Usuario;

import java.time.LocalDate;
import java.util.Map;

/**
 * Entrada que recibe un {@link IntencionHandler} para interpretar un comando
 * de voz ya clasificado.
 */
public record VozContexto(String texto, Map<String, String> slots, double confianza, Usuario usuario, LocalDate hoy) {
}
