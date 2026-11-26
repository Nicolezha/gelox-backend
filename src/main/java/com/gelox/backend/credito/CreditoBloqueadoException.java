package com.gelox.backend.credito;

import com.gelox.backend.dto.AdvertenciaCreditoDTO;
import lombok.Getter;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * RF65 — 409 al crear un despacho cuando la política del comerciante es
 * BLOQUEAR y tiene deuda sobre el cupo o una planilla vencida.
 * GlobalExceptionHandler la responde como
 * {error, codigo: CREDITO_BLOQUEADO, deuda, cupo, planillasVencidas, advertencias} (FP-15 §2.17.1).
 */
@Getter
public class CreditoBloqueadoException extends ResponseStatusException {

    public static final String MENSAJE = "El comerciante tiene el crédito bloqueado";
    public static final String CODIGO = "CREDITO_BLOQUEADO";

    private final BigDecimal deuda;
    private final BigDecimal cupo;
    private final long planillasVencidas;
    private final List<AdvertenciaCreditoDTO> advertencias;

    public CreditoBloqueadoException(BigDecimal deuda, BigDecimal cupo, long planillasVencidas,
                                      List<AdvertenciaCreditoDTO> advertencias) {
        super(HttpStatus.CONFLICT, MENSAJE);
        this.deuda = deuda;
        this.cupo = cupo;
        this.planillasVencidas = planillasVencidas;
        this.advertencias = advertencias;
    }

    public Map<String, Object> cuerpo() {
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("error", MENSAJE);
        cuerpo.put("codigo", CODIGO);
        cuerpo.put("deuda", deuda);
        cuerpo.put("cupo", cupo);
        cuerpo.put("planillasVencidas", planillasVencidas);
        cuerpo.put("advertencias", advertencias);
        return cuerpo;
    }
}
