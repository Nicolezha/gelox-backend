package com.gelox.backend.exceptions;

import com.gelox.backend.credito.CreditoBloqueadoException;
import com.gelox.backend.gobierno.CuotaAlcanzadaException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    /** Límite por archivo de spring.servlet.multipart.max-file-size, para el mensaje del 400. */
    @Value("${spring.servlet.multipart.max-file-size:5MB}")
    private String maxArchivo = "5MB";

    /**
     * Un archivo que pasa el límite por archivo se rechaza antes de llegar al controlador (y antes de
     * revisar la cuota ALMACENAMIENTO_MB). Sin este manejador caía en el genérico y respondía 500.
     * 400 como las demás validaciones de tamaño (logo y favicon, FP-15 §2.13).
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, String>> handleArchivoMuyGrande(MaxUploadSizeExceededException ex) {
        String limite = maxArchivo.toUpperCase().replace("MB", " MB").replace("KB", " KB").trim();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("error", "El archivo supera el tamaño máximo de " + limite + " por archivo.",
                        "codigo", "ARCHIVO_MUY_GRANDE"));
    }

    /** RF70 — 409 {error, codigo: CUOTA_PLAN_ALCANZADA, cuota, limite, usado} (FP-15 §2.16.2). */
    @ExceptionHandler(CuotaAlcanzadaException.class)
    public ResponseEntity<Map<String, Object>> handleCuotaAlcanzada(CuotaAlcanzadaException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ex.cuerpo());
    }

    /** RF65 — 409 {error, codigo: CREDITO_BLOQUEADO, deuda, cupo, planillasVencidas} (FP-15 §2.17.1). */
    @ExceptionHandler(CreditoBloqueadoException.class)
    public ResponseEntity<Map<String, Object>> handleCreditoBloqueado(CreditoBloqueadoException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ex.cuerpo());
    }

    /** {error, campo}: la pantalla marca el campo con el error (planes, alta de distribuidora). */
    @ExceptionHandler(CampoInvalidoException.class)
    public ResponseEntity<Map<String, String>> handleCampoInvalido(CampoInvalidoException ex) {
        return ResponseEntity.status(ex.getStatusCode())
                .body(Map.of("error", ex.getReason(), "campo", ex.getCampo()));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleResponseStatus(ResponseStatusException ex) {
        return ResponseEntity.status(ex.getStatusCode())
                .body(Map.of("error", ex.getReason() != null ? ex.getReason() : ex.getMessage()));
    }

    @ExceptionHandler(CorreoDuplicadoException.class)
    public ResponseEntity<Map<String, String>> handleCorreoDuplicado(CorreoDuplicadoException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(ContrasenaNoCoincideException.class)
    public ResponseEntity<Map<String, String>> handleContrasenaNoCoincide(ContrasenaNoCoincideException ex) {
        return ResponseEntity.badRequest()
                .body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(ContrasenaActualIncorrectaException.class)
    public ResponseEntity<Map<String, String>> handleContrasenaActualIncorrecta(ContrasenaActualIncorrectaException ex) {
        return ResponseEntity.badRequest()
                .body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleIllegalArgument(IllegalArgumentException ex) {
        return ResponseEntity.badRequest()
                .body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<Map<String, String>> handleNotFound(NoSuchElementException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidationErrors(MethodArgumentNotValidException ex) {
        Map<String, String> errores = new HashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(e -> errores.put(e.getField(), e.getDefaultMessage()));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errores);
    }

    @ExceptionHandler(StockInsuficienteException.class)
    public ResponseEntity<Map<String, String>> handleStockInsuficiente(StockInsuficienteException ex) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(PlanillaNoEncontradaException.class)
    public ResponseEntity<Map<String, String>> handlePlanillaNoEncontrada(PlanillaNoEncontradaException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(PlanillaYaLiquidadaException.class)
    public ResponseEntity<Map<String, String>> handlePlanillaYaLiquidada(PlanillaYaLiquidadaException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(DevolucionInvalidaException.class)
    public ResponseEntity<Map<String, String>> handleDevolucionInvalida(DevolucionInvalidaException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleGeneral(Exception ex) {
        String mensaje = ex.getMessage() != null ? ex.getMessage() : "Error interno del servidor";
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", mensaje));
    }
}
