package com.gelox.backend.gobierno;

import lombok.Getter;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * RF70 — 409 al alcanzar una cuota del plan. GlobalExceptionHandler la responde como
 * {error, codigo: CUOTA_PLAN_ALCANZADA, cuota, limite, usado} (FP-15 §2.16.2).
 * Para ALMACENAMIENTO_MB, limite y usado van en MB.
 */
@Getter
public class CuotaAlcanzadaException extends ResponseStatusException {

    public static final String MENSAJE = "Cuota del plan alcanzada";
    public static final String CODIGO = "CUOTA_PLAN_ALCANZADA";

    private final TipoCuota cuota;
    private final long limite;
    private final Number usado;

    public CuotaAlcanzadaException(TipoCuota cuota, long limite, Number usado) {
        super(HttpStatus.CONFLICT, mensaje(cuota, limite, usado));
        this.cuota = cuota;
        this.limite = limite;
        this.usado = usado;
    }

    /** FP-15 §2.16.2 — p. ej. «Cuota del plan alcanzada: usuarios (5 de 5).» */
    private static String mensaje(TipoCuota cuota, long limite, Number usado) {
        String recurso = switch (cuota) {
            case USUARIOS -> "usuarios";
            case PRODUCTOS -> "productos";
            case COMERCIANTES -> "comerciantes";
            case ALMACENAMIENTO_MB -> "almacenamiento (MB)";
            case LLAMADAS_IA_MES -> "consultas de voz";
        };
        String cantidad = new BigDecimal(usado.toString()).stripTrailingZeros().toPlainString();
        return MENSAJE + ": " + recurso + " (" + cantidad + " de " + limite + ").";
    }

    public Map<String, Object> cuerpo() {
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("error", getReason());
        cuerpo.put("codigo", CODIGO);
        cuerpo.put("cuota", cuota.name());
        cuerpo.put("limite", limite);
        cuerpo.put("usado", usado);
        return cuerpo;
    }
}
