package com.gelox.backend.gobierno;

import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * RF71 — Registra en {@code auditoria} cada método {@link Auditable} que termina sin excepción
 * (si falla no queda fila, CP-71-03). La fila se guarda en una transacción nueva con la IP,
 * el usuario autenticado, su rol y TenantContext, tomados antes de ejecutar el método.
 * <p>
 * entidad_id: el primer argumento UUID del método (editar(id, …)) o, si no hay, el id
 * del resultado (DTO con getId()/id(), también dentro de un ResponseEntity).
 */
@Aspect
@Component
@RequiredArgsConstructor
public class AuditoriaAspect {

    private final AuditoriaService auditoriaService;

    @Around("@annotation(auditable)")
    public Object auditar(ProceedingJoinPoint joinPoint, Auditable auditable) throws Throwable {
        // Antes de ejecutar: el método puede limpiar el contexto (p. ej. cerrar sesión)
        AuditoriaService.Contexto contexto = AuditoriaService.contextoActual();

        Object resultado = joinPoint.proceed();

        Map<String, Object> detalle = new LinkedHashMap<>();
        detalle.put("metodo", joinPoint.getSignature().getDeclaringType().getSimpleName()
                + "." + joinPoint.getSignature().getName());
        auditoriaService.registrar(auditable.accion(), auditable.entidad(),
                entidadId(joinPoint.getArgs(), resultado), detalle, contexto);
        return resultado;
    }

    static UUID entidadId(Object[] args, Object resultado) {
        if (args != null) {
            for (Object arg : args) {
                if (arg instanceof UUID id) return id;
            }
        }
        Object cuerpo = resultado instanceof ResponseEntity<?> respuesta ? respuesta.getBody() : resultado;
        if (cuerpo == null) return null;
        for (String nombre : new String[]{"getId", "id"}) {
            try {
                Method metodo = cuerpo.getClass().getMethod(nombre);
                metodo.trySetAccessible();
                Object valor = metodo.invoke(cuerpo);
                if (valor instanceof UUID id) return id;
            } catch (ReflectiveOperationException | RuntimeException ignorada) {
                // sin id: entidad_id queda null
            }
        }
        return null;
    }
}
