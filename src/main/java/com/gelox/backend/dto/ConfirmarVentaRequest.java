package com.gelox.backend.dto;

import com.gelox.backend.entities.CanalVenta;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record ConfirmarVentaRequest(
        @NotNull(message = "El canal de venta es obligatorio") CanalVenta canal,
        @NotEmpty(message = "La lista de ítems no puede estar vacía")
        @Valid List<ItemVentaRequest> items
) {}
