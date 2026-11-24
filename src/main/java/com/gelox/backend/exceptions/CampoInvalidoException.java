package com.gelox.backend.exceptions;

import lombok.Getter;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * Error de un campo del formulario: GlobalExceptionHandler responde {error, campo}
 * para que la pantalla marque el campo (contrato del mock de T193).
 */
@Getter
public class CampoInvalidoException extends ResponseStatusException {

    private final String campo;

    public CampoInvalidoException(HttpStatus status, String mensaje, String campo) {
        super(status, mensaje);
        this.campo = campo;
    }
}
