package com.gelox.backend.dto;

import com.gelox.backend.entities.Comerciante;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Representación pública de un comerciante (RF34).
 */
public record ComercianteResponseDTO(

        UUID   id,
        String nombre,
        String municipio,
        String direccion,
        String telefono,
        String contactoEmergenciaNombre,
        String contactoEmergenciaParentesco,
        String tallaUniforme,
        String placa,
        String documento,
        String tipoDocumento,
        String eps,
        String fotoUrl,
        boolean activo,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    /** Factory a partir de la entidad JPA. */
    public static ComercianteResponseDTO from(Comerciante c) {
        return new ComercianteResponseDTO(
                c.getId(),
                c.getNombre(),
                c.getMunicipio(),
                c.getDireccion(),
                c.getTelefono(),
                c.getContactoEmergenciaNombre(),
                c.getContactoEmergenciaParentesco(),
                c.getTallaUniforme(),
                c.getPlaca(),
                c.getDocumento(),
                c.getTipoDocumento(),
                c.getEps(),
                c.getFotoUrl(),
                Boolean.TRUE.equals(c.getActivo()),
                c.getCreatedAt(),
                c.getUpdatedAt()
        );
    }
}
