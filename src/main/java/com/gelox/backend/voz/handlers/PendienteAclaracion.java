package com.gelox.backend.voz.handlers;

/**
 * Marca un payload de venta a medias: el handler hizo una pregunta ("¿para
 * quién?", "¿cuál producto?") y la siguiente frase del usuario es la respuesta,
 * no un comando nuevo. {@code VozService} la enruta de vuelta al mismo handler.
 */
public interface PendienteAclaracion {
}
