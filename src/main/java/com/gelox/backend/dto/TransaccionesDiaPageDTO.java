package com.gelox.backend.dto;

import java.util.List;

public record TransaccionesDiaPageDTO(
        List<TransaccionDiaDTO> transacciones,
        long totalElementos,
        int page,
        int totalPages
) {}