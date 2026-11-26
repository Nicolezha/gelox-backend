package com.gelox.backend.exportacion.dto;

import java.util.List;

/** RF67 — cuerpo de PUT /api/proveedores/{id}/homologaciones. */
public record HomologacionesRequest(
        List<HomologacionRequest> homologaciones
) {
}
