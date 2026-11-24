package com.gelox.backend.entities;

/** RF67 — campos que puede tomar una columna de una plantilla GENERAR_FILAS (FP-15 §2.18.1). */
public enum CampoPlantilla {
    CODIGO_PROVEEDOR,
    SKU,
    NOMBRE_PRODUCTO,
    UNIDAD,
    /** En la unidad de la fila: cajas si el producto tiene caja, unidades si no. */
    CANTIDAD,
    CANTIDAD_CAJAS,
    CANTIDAD_UNIDADES,
    PRECIO_UNITARIO,
    SUBTOTAL,
    FECHA_PEDIDO,
    /** Escribe {@code valor_fijo}, por ejemplo el código de cliente ante el proveedor. */
    TEXTO_FIJO
}
