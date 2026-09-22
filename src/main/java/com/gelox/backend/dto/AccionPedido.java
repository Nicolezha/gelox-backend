package com.gelox.backend.dto;

/** Va en dto (junto a TipoPeriodo) para que services no dependa del paquete voz. */
public enum AccionPedido {
    AGREGAR,
    ELIMINAR,
    ACTUALIZAR
}
