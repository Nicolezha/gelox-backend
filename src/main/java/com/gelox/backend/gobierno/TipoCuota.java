package com.gelox.backend.gobierno;

/** RF70 — Cuotas del plan de la distribuidora (nombres de FP-15 §2.16.2). */
public enum TipoCuota {
    USUARIOS,
    PRODUCTOS,
    COMERCIANTES,
    /** Bytes de archivo_almacenado contra max_almacenamiento_mb. */
    ALMACENAMIENTO_MB,
    /** Comandos de voz clasificados por el LLM en el mes calendario (hora de Bogotá). */
    LLAMADAS_IA_MES
}
