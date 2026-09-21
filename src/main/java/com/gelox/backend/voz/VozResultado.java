package com.gelox.backend.voz;

import java.util.Map;

/**
 * Salida común de un {@link IntencionHandler}, tanto al interpretar un
 * comando como al ejecutarlo. {@code payload} lleva, cuando aplica, el
 * {@link VozPendiente} que el llamador debe reenviar tras la confirmación
 * del usuario.
 */
public record VozResultado(boolean ok, String textoRespuesta, Map<String, Object> datos, Object payload) {
}
