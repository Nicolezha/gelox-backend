package com.gelox.backend.exceptions;

public class ContrasenaNoCoincideException extends RuntimeException {
    public ContrasenaNoCoincideException(String message) {
        super(message);
    }
}
