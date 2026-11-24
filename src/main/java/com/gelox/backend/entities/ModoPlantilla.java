package com.gelox.backend.entities;

/** RF67 — cómo se arma el archivo de pedido de un proveedor (FP-15 §2.18.1). */
public enum ModoPlantilla {
    /** Abre un archivo base con todos los productos del proveedor y solo escribe la cantidad en cada fila. */
    RELLENAR_CATALOGO,
    /** Crea un libro nuevo con las columnas configuradas y una fila por cada ítem del pedido. */
    GENERAR_FILAS
}
