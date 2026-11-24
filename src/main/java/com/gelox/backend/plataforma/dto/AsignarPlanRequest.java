package com.gelox.backend.plataforma.dto;

import java.util.UUID;

/** RF70 — PATCH /api/plataforma/distribuidoras/{id}/plan. DistribuidoraService valida ambos campos. */
public record AsignarPlanRequest(UUID planId, String motivo) {
}
