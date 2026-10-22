package com.gelox.backend.recomendacion.dto;

import java.util.UUID;

/**
 * Respuesta de POST /api/recomendaciones/{id}/pedido (RF56).
 */
public record PedidoGeneradoDTO(UUID pedidoId, String exportUrl) {}
