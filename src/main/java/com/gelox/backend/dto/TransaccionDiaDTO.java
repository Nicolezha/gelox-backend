package com.gelox.backend.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record TransaccionDiaDTO(
        String id,
        String tipo,
        String clienteOComerciante,
        List<DetalleProductoDTO> detallesProductos,
        BigDecimal total,
        LocalDateTime hora,
        String metodoPago
) {}
