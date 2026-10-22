package com.gelox.backend.tenant;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.hibernate.Session;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.lang.reflect.UndeclaredThrowableException;
import java.util.UUID;

/**
 * Activa el filtro "tenantFilter" de Hibernate antes de cada llamada a un repositorio.
 *
 * <p>Con spring.jpa.open-in-view=false, un servicio sin @Transactional no tiene sesión
 * propia: el repositorio abre la suya después de este aspecto y el filtro quedaría en otra
 * sesión. Por eso, si no hay transacción activa, la llamada se envuelve en una transacción
 * para que el filtro y la consulta usen la misma sesión.</p>
 */
@Aspect
@Component
@RequiredArgsConstructor
public class TenantFilterAspect {

    @PersistenceContext
    private EntityManager entityManager;

    private final PlatformTransactionManager transactionManager;

    @Around("execution(* com.gelox.backend..repositories..*(..)) || execution(* com.gelox.backend..*Repository.*(..))")
    public Object activarFiltro(ProceedingJoinPoint joinPoint) throws Throwable {
        UUID tenantId = TenantContext.get();
        if (tenantId == null) {
            return joinPoint.proceed();
        }

        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            habilitarFiltro(tenantId);
            return joinPoint.proceed();
        }

        try {
            return new TransactionTemplate(transactionManager).execute(status -> {
                habilitarFiltro(tenantId);
                try {
                    return joinPoint.proceed();
                } catch (RuntimeException | Error e) {
                    throw e;
                } catch (Throwable t) {
                    throw new UndeclaredThrowableException(t);
                }
            });
        } catch (UndeclaredThrowableException e) {
            throw e.getUndeclaredThrowable();
        }
    }

    private void habilitarFiltro(UUID tenantId) {
        entityManager.unwrap(Session.class)
                .enableFilter("tenantFilter")
                .setParameter("tenantId", tenantId);
    }
}
