package com.gelox.backend.voz.pln;

/**
 * Resultado del modelo de PLN (FP-15 §2.4): categoría con mayor probabilidad.
 *
 * @param intencion    nombre de {@link com.gelox.backend.entities.TipoIntencionVoz} o {@code "FUERA_DE_DOMINIO"}
 * @param probabilidad probabilidad de esa categoría, entre 0 y 1
 */
public record PrediccionIntencion(String intencion, double probabilidad) {

    public static final String FUERA_DE_DOMINIO = "FUERA_DE_DOMINIO";

    public boolean esFueraDeDominio() {
        return FUERA_DE_DOMINIO.equals(intencion);
    }
}
