package com.gelox.backend.entities;

public enum TipoEvento {
    DESPACHO,
    CIERRE_CAJA,
    ALERTA_STOCK,
    NUEVO_REGISTRO,
    EDICION_USUARIO,
    DESHABILITAR_USUARIO,
    HABILITAR_USUARIO,
    INICIO_SESION,
    CIERRE_SESION,
    ACTUALIZACION_PERFIL,
    CAMBIO_CONTRASENA,
    CREAR_PRODUCTO,
    EDITAR_PRODUCTO,
    DESACTIVAR_PRODUCTO,
    ACTIVAR_PRODUCTO,
    // RF21 — Pedido al proveedor
    PEDIDO_PROVEEDOR,
    // RF23 — Registro de entrada de mercancía
    ENTRADA_MERCANCIA,
    // RF25 — Registro de pérdida
    PERDIDA_PRODUCTO,
    // RF28 — Inicio de venta
    INICIAR_VENTA,
    // RF31 — Confirmación de venta
    CONFIRMAR_VENTA,
    // RF36 — Despacho planilla comerciante
    DESPACHO_PLANILLA
}