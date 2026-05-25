package com.gelox.backend.dto;

import java.util.List;

public record CierreCajaPageResponseDTO(
        List<CierreCajaListItemDTO> cierres,
        long total,
        int page,
        int totalPages
) {}
