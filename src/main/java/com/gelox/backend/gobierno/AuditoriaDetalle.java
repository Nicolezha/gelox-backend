package com.gelox.backend.gobierno;

/**
 * RF71 — Detalle a medida para la fila de un método {@link Auditable}.
 * <p>
 * Dentro del método auditado se llama {@code AuditoriaDetalle.poner(Map.of(...))}; al terminar
 * sin excepción {@link AuditoriaAspect} guarda ese objeto como JSON en {@code auditoria.detalle}
 * en lugar de {@code {"metodo": ...}}. Formas acordadas con el panel (mock de T193):
 * PLAN_ASIGNAR {planAnterior, planNuevo, motivo, cuotas}, PLAN_CREAR {plan, precioMensual, cuotas},
 * PLAN_EDITAR {plan, cambios: [{campo, antes, despues}]}, etc.
 * <p>
 * También permite fijar el entidad_id cuando no es el primer argumento UUID ni el id del resultado.
 */
public final class AuditoriaDetalle {

    private static final ThreadLocal<Object> DETALLE = new ThreadLocal<>();
    private static final ThreadLocal<java.util.UUID> ENTIDAD_ID = new ThreadLocal<>();

    private AuditoriaDetalle() {
    }

    public static void poner(Object detalle) {
        DETALLE.set(detalle);
    }

    public static void entidadId(java.util.UUID id) {
        ENTIDAD_ID.set(id);
    }

    static Object detalle() {
        return DETALLE.get();
    }

    static java.util.UUID entidadId() {
        return ENTIDAD_ID.get();
    }

    static void limpiar() {
        DETALLE.remove();
        ENTIDAD_ID.remove();
    }
}
