package com.gelox.backend.exceptions;

/**
 * Lanzada cuando el stock_actual de un producto es insuficiente
 * para completar una operación de salida.
 * Mapeada a 422 Unprocessable Entity en GlobalExceptionHandler.
 */
public class StockInsuficienteException extends RuntimeException {
    public StockInsuficienteException(String message) {
        super(message);
    }
}
