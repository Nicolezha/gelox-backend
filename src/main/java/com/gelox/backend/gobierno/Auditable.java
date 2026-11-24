package com.gelox.backend.gobierno;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * RF71 — Marca una operación crítica: al terminar sin error, {@link AuditoriaAspect}
 * guarda una fila en {@code auditoria} con fecha_hora, IP, distribuidora y usuario.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Auditable {

    /** Acción registrada, p. ej. "CREAR_USUARIO". */
    String accion();

    /** Entidad afectada, p. ej. "Usuario". Vacío si no aplica. */
    String entidad() default "";
}
