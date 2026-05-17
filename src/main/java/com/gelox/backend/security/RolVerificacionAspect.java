package com.gelox.backend.security;

import com.google.firebase.auth.FirebaseToken;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.Arrays;

@Aspect
@Component
public class RolVerificacionAspect {

    @Before("@annotation(requiereRol)")
    public void verificarRol(RequiereRol requiereRol) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No autenticado");
        }

        Object principal = authentication.getPrincipal();
        if (!(principal instanceof FirebaseToken token)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Token inválido");
        }

        String rolUsuario = (String) token.getClaims().get("rol");
        boolean tieneRol = Arrays.asList(requiereRol.value()).contains(rolUsuario);

        if (!tieneRol) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "No tienes permiso para realizar esta acción");
        }
    }
}
