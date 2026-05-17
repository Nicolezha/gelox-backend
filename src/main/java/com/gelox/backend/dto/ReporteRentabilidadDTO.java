package com.gelox.backend.dto;

import java.util.List;

public record ReporteRentabilidadDTO(
        List<RentabilidadCanalDTO> canales
) {}
