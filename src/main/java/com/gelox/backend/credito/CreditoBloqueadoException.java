package com.gelox.backend.credito;

import lombok.Getter;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * RF65 — 409 al crear un despacho cuando la política del comerciante es
 * BLOQUEAR y tiene deuda sobre el cupo o una planilla vencida.
 * GlobalExceptionHandler la responde como
 * {error, codigo: CREDITO_BLOQUEADO, deuda, cupo, planillasVencidas} (FP-15 §2.17.1).
 */
@Getter
public class CreditoBloqueadoException extends ResponseStatusException {

    public static final String MENSAJE = "El comerciante tiene el crédito bloqueado";
    public static final String CODIGO = "CREDITO_BLOQUEADO";

    private final BigDecimal deuda;
    private final BigDecimal cupo;
    private final long planillasVencidas;

    public CreditoBloqueadoException(BigDecimal deuda, BigDecimal cupo, long planillasVencidas) {
        super(HttpStatus.CONFLICT, MENSAJE);
        this.deuda = deuda;
        this.cupo = cupo;
        this.planillasVencidas = planillasVencidas;
    }

    public Map<String, Object> cuerpo() {
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("error", MENSAJE);
        cuerpo.put("codigo", CODIGO);
        cuerpo.put("deuda", deuda);
        cuerpo.put("cupo", cupo);
        cuerpo.put("planillasVencidas", planillasVencidas);
        return cuerpo;
    }
}
