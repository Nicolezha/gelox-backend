package com.gelox.backend.exportacion;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Formatos del FP-15 §2.18.3: pesos con separador de miles sin decimales; margen con un decimal y %. */
final class FormatoReporte {

    static final String SIN_DATO = "—";
    private static final Locale CO = Locale.forLanguageTag("es-CO");
    private static final DateTimeFormatter FECHA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private FormatoReporte() {
    }

    static String pesos(BigDecimal valor) {
        DecimalFormat df = new DecimalFormat("#,##0", DecimalFormatSymbols.getInstance(CO));
        return "$ " + df.format(valor == null ? BigDecimal.ZERO : valor);
    }

    /** Margen nulo (sin inversión) se muestra «—», igual que la pantalla. */
    static String margen(BigDecimal valor) {
        if (valor == null) return SIN_DATO;
        DecimalFormat df = new DecimalFormat("0.0", DecimalFormatSymbols.getInstance(CO));
        return df.format(valor) + "%";
    }

    static String fechaHora(LocalDateTime fecha) {
        return FECHA_HORA.format(fecha);
    }

    static String canal(String canal) {
        return switch (canal) {
            case "VENTANILLA" -> "Ventanilla";
            case "RURAL" -> "Rural";
            case "COMERCIANTES" -> "Comerciantes";
            default -> canal;
        };
    }
}
