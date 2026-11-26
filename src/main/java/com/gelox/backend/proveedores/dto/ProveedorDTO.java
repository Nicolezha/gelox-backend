package com.gelox.backend.proveedores.dto;

import com.gelox.backend.entities.Proveedor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record ProveedorDTO(
        UUID id,
        String nombre,
        String nit,
        String contactoNombre,
        String telefono,
        String correo,
        Integer tiempoEntregaDias,
        Boolean diasHabiles,
        BigDecimal montoMinimoPedido,
        Boolean activo,
        long productosAsociados,        // productos activos del proveedor
        String plantilla,               // nombre de su plantilla de exportación; null si no tiene
        LocalDateTime createdAt
) {
    public static ProveedorDTO de(Proveedor p, long productosAsociados, String plantilla) {
        return new ProveedorDTO(p.getId(), p.getNombre(), p.getNit(), p.getContactoNombre(),
                p.getTelefono(), p.getCorreo(), p.getTiempoEntregaDias(), p.getDiasHabiles(),
                p.getMontoMinimoPedido(), p.getActivo(), productosAsociados, plantilla, p.getCreatedAt());
    }
}
