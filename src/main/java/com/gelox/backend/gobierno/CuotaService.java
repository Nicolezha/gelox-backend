package com.gelox.backend.gobierno;

import com.gelox.backend.entities.Distribuidora;
import com.gelox.backend.entities.MetodoClasificacion;
import com.gelox.backend.repositories.ComandoVozRepository;
import com.gelox.backend.repositories.ComercianteRepository;
import com.gelox.backend.repositories.DistribuidoraRepository;
import com.gelox.backend.repositories.ProductoRepository;
import com.gelox.backend.repositories.UsuarioRepository;
import com.gelox.backend.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * RF70 — Cuotas del plan de la distribuidora de la petición (FP-15 §2.16.2).
 * <p>
 * Se llama antes de guardar nada. {@link #validar(TipoCuota)} lanza
 * {@link CuotaAlcanzadaException} (409) al alcanzar el límite; {@link #disponible(TipoCuota)}
 * solo informa, para la cuota de IA, que cae al clasificador de respaldo en vez de fallar.
 * Un máximo null en el plan significa "sin límite" y no se cuenta nada.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CuotaService {

    static final ZoneId ZONA_BOGOTA = ZoneId.of("America/Bogota");
    private static final long BYTES_POR_MB = 1024L * 1024L;

    private final DistribuidoraRepository distribuidoraRepository;
    private final PlanSuscripcionRepository planRepository;
    private final UsuarioRepository usuarioRepository;
    private final ProductoRepository productoRepository;
    private final ComercianteRepository comercianteRepository;
    private final ComandoVozRepository comandoVozRepository;
    private final UsoAlmacenamientoService usoAlmacenamientoService;

    /** 409 si crear un recurso más de este tipo supera el plan. */
    @Transactional(readOnly = true)
    public void validar(TipoCuota tipo) {
        validar(tipo, 1);
    }

    /**
     * 409 si {@code usado + incremento} supera el plan. Para ALMACENAMIENTO_MB el incremento
     * son los bytes del archivo nuevo; para las demás, el número de recursos nuevos.
     */
    @Transactional(readOnly = true)
    public void validar(TipoCuota tipo, long incremento) {
        UUID tenantId = TenantContext.requerido();
        Integer limite = limite(tipo, tenantId);
        if (limite == null) return;

        long usado = contarUsado(tipo, tenantId);
        if (usado + incremento > enUnidades(tipo, limite)) {
            log.info("Cuota {} alcanzada en la distribuidora {}: usado {} + {} > límite {}",
                    tipo, tenantId, usado, incremento, limite);
            throw new CuotaAlcanzadaException(tipo, limite, usadoParaRespuesta(tipo, usado));
        }
    }

    /** true si queda cupo para un recurso más (sin distribuidora en la petición no se limita). */
    @Transactional(readOnly = true)
    public boolean disponible(TipoCuota tipo) {
        UUID tenantId = TenantContext.get();
        if (tenantId == null) return true;
        Integer limite = limite(tipo, tenantId);
        if (limite == null) return true;
        return contarUsado(tipo, tenantId) + 1 <= enUnidades(tipo, limite);
    }

    // ──────────────────────────────────────────────────────────────────────

    private Integer limite(TipoCuota tipo, UUID tenantId) {
        return distribuidoraRepository.findById(tenantId)
                .map(Distribuidora::getPlanId)
                .flatMap(planRepository::findById)
                .map(plan -> plan.limite(tipo))
                .orElse(null);
    }

    private long contarUsado(TipoCuota tipo, UUID tenantId) {
        return switch (tipo) {
            // El gerente titular es un usuario activo más: cuenta en la cuota
            case USUARIOS -> usuarioRepository.countByTenantIdAndActivoTrue(tenantId);
            case PRODUCTOS -> productoRepository.countByTenantIdAndActivoTrue(tenantId);
            case COMERCIANTES -> comercianteRepository.countByTenantIdAndActivoTrue(tenantId);
            case ALMACENAMIENTO_MB -> usoAlmacenamientoService.usadoBytes(tenantId);
            case LLAMADAS_IA_MES -> comandoVozRepository
                    .countByTenantIdAndMetodoClasificacionAndCreatedAtGreaterThanEqual(
                            tenantId, MetodoClasificacion.LLM, inicioMesBogota(Instant.now(), ZoneId.systemDefault()));
        };
    }

    private static long enUnidades(TipoCuota tipo, int limite) {
        return tipo == TipoCuota.ALMACENAMIENTO_MB ? limite * BYTES_POR_MB : limite;
    }

    private static Number usadoParaRespuesta(TipoCuota tipo, long usado) {
        if (tipo != TipoCuota.ALMACENAMIENTO_MB) return usado;
        return BigDecimal.valueOf(usado).divide(BigDecimal.valueOf(BYTES_POR_MB), 2, RoundingMode.HALF_UP);
    }

    /**
     * Inicio del mes calendario en Bogotá, expresado en la zona con que comando_voz guarda
     * created_at (LocalDateTime.now() del servidor).
     */
    public static LocalDateTime inicioMesBogota(Instant ahora, ZoneId zonaServidor) {
        ZonedDateTime inicio = ahora.atZone(ZONA_BOGOTA).withDayOfMonth(1).truncatedTo(ChronoUnit.DAYS);
        return inicio.withZoneSameInstant(zonaServidor).toLocalDateTime();
    }
}
