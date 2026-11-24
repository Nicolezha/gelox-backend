package com.gelox.backend.gobierno;

/**
 * RF71 — Códigos de acción de {@code auditoria.accion} (FP-15 §2.16.3, estilo ENTIDAD_VERBO).
 * {@link Auditable#accion()} recibe el nombre como texto; usar siempre uno de esta lista
 * (GET /api/plataforma/auditoria/acciones la publica para el filtro del panel).
 */
public enum AccionAuditoria {
    DISTRIBUIDORA_APROVISIONAR,
    DISTRIBUIDORA_CAMBIAR_ESTADO,
    DISTRIBUIDORA_REENVIAR_ACCESO,
    IDENTIDAD_ACTUALIZAR,
    PLAN_CREAR,
    PLAN_EDITAR,
    PLAN_ASIGNAR,
    USUARIO_CREAR,
    USUARIO_EDITAR,
    USUARIO_DESHABILITAR,
    USUARIO_HABILITAR,
    PRODUCTO_CREAR,
    PROVEEDOR_CREAR,
    PROVEEDOR_EDITAR,
    PROVEEDOR_ACTIVAR,
    PLANTILLA_EXPORTACION_CAMBIAR,
    CREDITO_POLITICA_CAMBIAR,
    ESQUEMA_LIQUIDACION_CAMBIAR,
    PLANILLA_LIQUIDAR,
    ABONO_REGISTRAR,
    SESION_INICIAR,
    SESION_CERRAR,
    CONTRASENA_CAMBIAR,
    ACCESO_BLOQUEADO,
    TOKEN_INVALIDO,
    PLANILLA_CAMBIAR_FECHA_LIMITE
}
