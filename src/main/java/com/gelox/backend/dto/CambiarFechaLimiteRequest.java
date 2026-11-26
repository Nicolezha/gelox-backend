package com.gelox.backend.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/** RF65 — PATCH /api/planillas/{id}/fecha-limite. motivo queda registrado en auditoría. */
public record CambiarFechaLimiteRequest(@NotNull LocalDate fechaLimitePago, String motivo) {}
