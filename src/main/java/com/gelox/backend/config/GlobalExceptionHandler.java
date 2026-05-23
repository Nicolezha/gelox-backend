package com.gelox.backend.config;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

/**
 * Garantiza que todas las excepciones no manejadas devuelvan JSON,
 * en lugar del Whitelabel Error Page HTML de Spring Boot.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> handleResponseStatus(ResponseStatusException ex) {
        String mensaje = ex.getReason() != null ? ex.getReason() : ex.getMessage();
        return ResponseEntity
                .status(ex.getStatusCode())
                .body(Map.of(
                        "status", ex.getStatusCode().value(),
                        "error", mensaje != null ? mensaje : "Error desconocido"
                ));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneral(Exception ex) {
        String mensaje = ex.getMessage() != null ? ex.getMessage() : "Error interno del servidor";
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of(
                        "status", 500,
                        "error", mensaje
                ));
    }
}
