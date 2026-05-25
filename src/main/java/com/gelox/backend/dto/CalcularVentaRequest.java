package com.gelox.backend.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record CalcularVentaRequest(
        @NotEmpty(message = "La lista de ítems no puede estar vacía")
        @Valid List<ItemCalculoRequest> items
) {}
