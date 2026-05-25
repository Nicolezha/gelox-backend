package com.gelox.backend.dto;

import com.gelox.backend.entities.CanalVenta;
import jakarta.validation.constraints.NotNull;

public record IniciarVentaRequest(
        @NotNull(message = "El canal de venta es obligatorio") CanalVenta canal
) {}
