package com.gelox.backend.voz.dto;

import java.util.Map;

/** Respuesta de POST /api/voz/confirmar. {@code estado} refleja EstadoComandoVoz. */
public record VozConfirmarResponse(String estado, String textoRespuesta, Map<String, Object> datos) {}
