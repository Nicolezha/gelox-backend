package com.gelox.backend.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record CrearDespachoRequest(
        @NotNull UUID comercianteId,
        @NotNull LocalDate fecha,
        @NotEmpty List<@Valid ItemDespachoRequest> items
) {}
