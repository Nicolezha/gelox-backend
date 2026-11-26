package com.gelox.backend.gobierno;

/** RF70 — Cuotas del plan de la distribuidora (nombres de FP-15 §2.16.2). */
public enum TipoCuota {
    USUARIOS("usuarios"),
    PRODUCTOS("productos"),
    COMERCIANTES("comerciantes"),
    /** Bytes de archivo_almacenado contra max_almacenamiento_mb (en las respuestas, en MB). */
    ALMACENAMIENTO_MB("almacenamientoMb"),
    /** Comandos de voz clasificados por el LLM en el mes calendario (hora de Bogotá). */
    LLAMADAS_IA_MES("llamadasIaMes");

    private final String clave;

    TipoCuota(String clave) {
        this.clave = clave;
    }

    /** Clave en el objeto {@code uso} de las respuestas del panel (contrato del mock de T193). */
    public String clave() {
        return clave;
    }
}
