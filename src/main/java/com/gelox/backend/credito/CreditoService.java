package com.gelox.backend.credito;

import com.gelox.backend.credito.dto.AbonoDTO;
import com.gelox.backend.credito.dto.AbonoRequest;
import com.gelox.backend.credito.dto.CreditoComercianteDTO;
import com.gelox.backend.credito.dto.PoliticaCreditoDTO;
import com.gelox.backend.credito.dto.PoliticaCreditoRequest;
import com.gelox.backend.entities.AbonoComerciante;
import com.gelox.backend.entities.Comerciante;
import com.gelox.backend.entities.PlanillaComerciante;
import com.gelox.backend.entities.PoliticaCredito;
import com.gelox.backend.entities.PoliticaVencimiento;
import com.gelox.backend.entities.TipoEvento;
import com.gelox.backend.entities.Usuario;
import com.gelox.backend.gobierno.Auditable;
import com.gelox.backend.repositories.AbonoComercianteRepository;
import com.gelox.backend.repositories.ComercianteRepository;
import com.gelox.backend.repositories.PlanillaComercianteRepository;
import com.gelox.backend.repositories.PoliticaCreditoRepository;
import com.gelox.backend.security.RequiereRol;
import com.gelox.backend.services.EventoSistemaService;
import com.gelox.backend.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

/**
 * RF65 — Política de crédito por distribuidora (con excepción por
 * comerciante), deuda, planillas vencidas y abonos (FP-15 §2.17.1).
 */
@Service
@RequiredArgsConstructor
@Transactional
public class CreditoService {

    private static final ZoneId ZONA_BOGOTA = ZoneId.of("America/Bogota");

    private final PoliticaCreditoRepository politicaRepository;
    private final AbonoComercianteRepository abonoRepository;
    private final PlanillaComercianteRepository planillaRepository;
    private final ComercianteRepository comercianteRepository;
    private final EventoSistemaService eventoSistemaService;

    /** Lo que PlanillaService.crearDespacho necesita para aplicar la política al despachar. */
    public record Evaluacion(PoliticaCreditoDTO politica, BigDecimal deuda, long planillasVencidas, boolean excede) {
    }

    // ── Política por defecto de la distribuidora ────────────────────────────

    @RequiereRol({"ADMINISTRADOR", "ENCARGADO_INVENTARIO", "ENCARGADO_VENTAS"})
    public PoliticaCreditoDTO obtenerPoliticaDistribuidora() {
        return PoliticaCreditoDTO.from(politicaPorDefecto());
    }

    @RequiereRol("ADMINISTRADOR")
    @Auditable(accion = "CREDITO_POLITICA_CAMBIAR", entidad = "PoliticaCredito")
    public PoliticaCreditoDTO actualizarPoliticaDistribuidora(PoliticaCreditoRequest req) {
        PoliticaCredito politica = politicaPorDefecto();
        politica.setCupo(req.cupo());
        politica.setPlazoDias(req.plazoDias());
        politica.setPoliticaVencimiento(req.politicaVencimiento());
        return PoliticaCreditoDTO.from(politicaRepository.save(politica));
    }

    // ── Crédito de un comerciante (política efectiva, deuda, vencidas) ──────

    @RequiereRol({"ADMINISTRADOR", "ENCARGADO_VENTAS"})
    public CreditoComercianteDTO obtenerCreditoComerciante(UUID comercianteId) {
        validarComercianteDelTenant(comercianteId);
        Evaluacion evaluacion = evaluar(comercianteId);
        boolean bloqueado = evaluacion.excede()
                && evaluacion.politica().politicaVencimiento() == PoliticaVencimiento.BLOQUEAR;
        return new CreditoComercianteDTO(comercianteId, evaluacion.politica(), evaluacion.deuda(),
                evaluacion.planillasVencidas(), bloqueado);
    }

    @RequiereRol("ADMINISTRADOR")
    @Auditable(accion = "CREDITO_POLITICA_CAMBIAR", entidad = "PoliticaCredito")
    public PoliticaCreditoDTO definirExcepcion(UUID comercianteId, PoliticaCreditoRequest req) {
        Comerciante comerciante = validarComercianteDelTenant(comercianteId);
        UUID tenantId = TenantContext.requerido();
        PoliticaCredito politica = politicaRepository.findByTenantIdAndComerciante_Id(tenantId, comercianteId)
                .orElseGet(() -> PoliticaCredito.builder().comerciante(comerciante).build());
        politica.setCupo(req.cupo());
        politica.setPlazoDias(req.plazoDias());
        politica.setPoliticaVencimiento(req.politicaVencimiento());
        return PoliticaCreditoDTO.from(politicaRepository.save(politica));
    }

    @RequiereRol("ADMINISTRADOR")
    @Auditable(accion = "CREDITO_POLITICA_CAMBIAR", entidad = "PoliticaCredito")
    public void eliminarExcepcion(UUID comercianteId) {
        validarComercianteDelTenant(comercianteId);
        UUID tenantId = TenantContext.requerido();
        politicaRepository.findByTenantIdAndComerciante_Id(tenantId, comercianteId)
                .ifPresent(politicaRepository::delete);
    }

    // ── Abonos ───────────────────────────────────────────────────────────────

    @RequiereRol({"ADMINISTRADOR", "ENCARGADO_VENTAS"})
    public List<AbonoDTO> listarAbonos(UUID comercianteId) {
        validarComercianteDelTenant(comercianteId);
        return abonoRepository.findByComerciante_IdOrderByFechaDesc(comercianteId).stream()
                .map(AbonoDTO::from).toList();
    }

    @RequiereRol({"ADMINISTRADOR", "ENCARGADO_VENTAS"})
    @Auditable(accion = "ABONO_REGISTRAR", entidad = "AbonoComerciante")
    public AbonoDTO registrarAbono(UUID comercianteId, AbonoRequest req, Usuario usuario) {
        Comerciante comerciante = validarComercianteDelTenant(comercianteId);

        PlanillaComerciante planilla = null;
        if (req.planillaId() != null) {
            planilla = planillaRepository.findById(req.planillaId())
                    .filter(TenantContext::pertenece)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Planilla no encontrada"));
        }

        AbonoComerciante abono = abonoRepository.save(AbonoComerciante.builder()
                .comerciante(comerciante)
                .planilla(planilla)
                .valor(req.valor())
                .observacion(req.observacion())
                .usuario(usuario)
                .build());

        eventoSistemaService.registrarEvento(TipoEvento.ABONO_COMERCIANTE,
                "Abono de $" + req.valor().toPlainString() + " de " + comerciante.getNombre() + ".",
                usuario.getId());

        return AbonoDTO.from(abono);
    }

    // ── Evaluación para PlanillaService.crearDespacho ───────────────────────

    /** Deuda, planillas vencidas y política efectiva del comerciante, para aplicar al crear un despacho. */
    public Evaluacion evaluar(UUID comercianteId) {
        UUID tenantId = TenantContext.requerido();
        PoliticaCredito politicaEfectiva = politicaRepository.findByTenantIdAndComerciante_Id(tenantId, comercianteId)
                .orElseGet(this::politicaPorDefecto);

        BigDecimal saldoPlanillas = planillaRepository.sumarSaldoCerradas(comercianteId);
        BigDecimal abonado = abonoRepository.sumarValorPorComerciante(comercianteId);
        BigDecimal deuda = saldoPlanillas.subtract(abonado).max(BigDecimal.ZERO);

        long planillasVencidas = planillaRepository.contarVencidas(comercianteId, LocalDate.now(ZONA_BOGOTA));
        boolean excede = deuda.compareTo(politicaEfectiva.getCupo()) > 0 || planillasVencidas > 0;

        return new Evaluacion(PoliticaCreditoDTO.from(politicaEfectiva), deuda, planillasVencidas, excede);
    }

    /** Plazo (días) de la política efectiva del comerciante, para fijar la fecha límite de pago al liquidar. */
    public int plazoDiasEfectivo(UUID comercianteId) {
        UUID tenantId = TenantContext.requerido();
        return politicaRepository.findByTenantIdAndComerciante_Id(tenantId, comercianteId)
                .orElseGet(this::politicaPorDefecto)
                .getPlazoDias();
    }

    // ── Privados ─────────────────────────────────────────────────────────────

    private PoliticaCredito politicaPorDefecto() {
        UUID tenantId = TenantContext.requerido();
        return politicaRepository.findByTenantIdAndComercianteIsNull(tenantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                        "La distribuidora no tiene política de crédito por defecto"));
    }

    private Comerciante validarComercianteDelTenant(UUID comercianteId) {
        return comercianteRepository.findById(comercianteId)
                .filter(TenantContext::pertenece)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Comerciante no encontrado"));
    }
}
