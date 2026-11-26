package com.gelox.backend.credito;

import com.gelox.backend.credito.dto.AbonoDTO;
import com.gelox.backend.credito.dto.AbonoRequest;
import com.gelox.backend.credito.dto.CreditoVistaDTO;
import com.gelox.backend.credito.dto.PlanillaSaldoDTO;
import com.gelox.backend.credito.dto.PoliticaCreditoDTO;
import com.gelox.backend.credito.dto.PoliticaCreditoRequest;
import com.gelox.backend.dto.AdvertenciaCreditoDTO;
import com.gelox.backend.entities.AbonoComerciante;
import com.gelox.backend.entities.Comerciante;
import com.gelox.backend.entities.PlanillaComerciante;
import com.gelox.backend.entities.PoliticaCredito;
import com.gelox.backend.entities.PoliticaVencimiento;
import com.gelox.backend.entities.TipoEvento;
import com.gelox.backend.entities.Usuario;
import com.gelox.backend.gobierno.Auditable;
import com.gelox.backend.gobierno.AuditoriaDetalle;
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
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * RF65 — Política de crédito por distribuidora (con excepción por
 * comerciante), deuda, planillas vencidas y abonos (FP-15 §2.17.1).
 * Contrato alineado con creditoService.js / SeccionCredito.jsx del frontend (T196).
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

    /** Lo que PlanillaService.crearDespacho necesita para decidir si bloquea o solo advierte. */
    public record Evaluacion(PoliticaVencimiento politica, BigDecimal cupo, BigDecimal deuda, long planillasVencidas) {
        public boolean excedeCupo() {
            return deuda.compareTo(cupo) > 0;
        }

        public boolean excede() {
            return excedeCupo() || planillasVencidas > 0;
        }

        public List<AdvertenciaCreditoDTO> advertencias() {
            List<AdvertenciaCreditoDTO> lista = new ArrayList<>();
            if (excedeCupo()) {
                lista.add(new AdvertenciaCreditoDTO("CUPO_SUPERADO", "El despacho supera el cupo de crédito del comerciante."));
            }
            if (planillasVencidas > 0) {
                lista.add(new AdvertenciaCreditoDTO("PLANILLA_VENCIDA", "El comerciante tiene planillas vencidas."));
            }
            return lista;
        }
    }

    /** Planillas con saldo (filtradas) y los totales agregados de la deuda del comerciante. */
    private record Saldos(List<PlanillaSaldoDTO> planillas, BigDecimal deuda, long vencidas) {
    }

    // ── Política por defecto de la distribuidora ────────────────────────────

    @RequiereRol({"ADMINISTRADOR", "ENCARGADO_INVENTARIO", "ENCARGADO_VENTAS"})
    public PoliticaCreditoDTO obtenerPoliticaDistribuidora() {
        return PoliticaCreditoDTO.from(politicaPorDefecto());
    }

    @RequiereRol("ADMINISTRADOR")
    @Auditable(accion = "CREDITO_POLITICA_CAMBIAR", entidad = "PoliticaCredito")
    public PoliticaCreditoDTO actualizarPoliticaDistribuidora(PoliticaCreditoRequest req, Usuario usuario) {
        PoliticaCredito politica = politicaPorDefecto();
        Map<String, Object> antes = valoresDe(politica);
        politica.setCupo(req.cupo());
        politica.setPlazoDias(req.plazoDias());
        politica.setPoliticaVencimiento(req.politica());
        politica.setActualizadoPor(usuario.getId());
        PoliticaCreditoDTO dto = PoliticaCreditoDTO.from(politicaRepository.save(politica));
        AuditoriaDetalle.entidadId(politica.getId());
        auditarCambioDePolitica(null, antes, valoresDe(politica));
        return dto;
    }

    // ── Crédito de un comerciante (política efectiva, deuda, planillas, abonos) ─

    @RequiereRol({"ADMINISTRADOR", "ENCARGADO_VENTAS"})
    public CreditoVistaDTO obtenerCreditoComerciante(UUID comercianteId) {
        validarComercianteDelTenant(comercianteId);
        return vistaDe(comercianteId);
    }

    @RequiereRol("ADMINISTRADOR")
    @Auditable(accion = "CREDITO_POLITICA_CAMBIAR", entidad = "PoliticaCredito")
    public CreditoVistaDTO definirExcepcion(UUID comercianteId, PoliticaCreditoRequest req, Usuario usuario) {
        Comerciante comerciante = validarComercianteDelTenant(comercianteId);
        UUID tenantId = TenantContext.requerido();
        Optional<PoliticaCredito> existente = politicaRepository.findByTenantIdAndComerciante_Id(tenantId, comercianteId);
        // La que regía hasta ahora: la excepción activa o, si no había, la de la distribuidora
        Map<String, Object> antes = valoresDe(existente.filter(p -> Boolean.TRUE.equals(p.getActivo()))
                .orElseGet(this::politicaPorDefecto));
        PoliticaCredito politica = existente
                .orElseGet(() -> PoliticaCredito.builder().comerciante(comerciante).build());
        politica.setCupo(req.cupo());
        politica.setPlazoDias(req.plazoDias());
        politica.setPoliticaVencimiento(req.politica());
        politica.setActivo(true);   // solo puede haber una fila por comerciante: si estaba inactiva, se reutiliza
        politica.setActualizadoPor(usuario.getId());
        politicaRepository.save(politica);
        auditarCambioDePolitica(comerciante, antes, valoresDe(politica));
        return vistaDe(comercianteId);
    }

    @RequiereRol("ADMINISTRADOR")
    @Auditable(accion = "CREDITO_POLITICA_CAMBIAR", entidad = "PoliticaCredito")
    public CreditoVistaDTO eliminarExcepcion(UUID comercianteId) {
        Comerciante comerciante = validarComercianteDelTenant(comercianteId);
        UUID tenantId = TenantContext.requerido();
        politicaRepository.findByTenantIdAndComerciante_Id(tenantId, comercianteId)
                .ifPresent(excepcion -> {
                    PoliticaCredito defecto = politicaPorDefecto();
                    PoliticaCredito regia = Boolean.TRUE.equals(excepcion.getActivo()) ? excepcion : defecto;
                    auditarCambioDePolitica(comerciante, valoresDe(regia), valoresDe(defecto));
                    politicaRepository.delete(excepcion);
                });
        return vistaDe(comercianteId);
    }

    // ── Abonos ───────────────────────────────────────────────────────────────

    @RequiereRol("ADMINISTRADOR")
    @Auditable(accion = "ABONO_REGISTRAR", entidad = "AbonoComerciante")
    public CreditoVistaDTO registrarAbono(UUID comercianteId, AbonoRequest req, Usuario usuario) {
        Comerciante comerciante = validarComercianteDelTenant(comercianteId);

        BigDecimal deudaActual = calcularSaldos(comercianteId).deuda();
        if (req.valor().compareTo(deudaActual) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El abono supera la deuda actual");
        }

        AbonoComerciante abono = abonoRepository.save(AbonoComerciante.builder()
                .comerciante(comerciante)
                .valor(req.valor())
                .observacion(req.observacion() != null && !req.observacion().isBlank() ? req.observacion().trim() : null)
                .usuario(usuario)
                .build());

        Map<String, Object> detalle = new LinkedHashMap<>();
        detalle.put("comerciante", comerciante.getNombre());
        detalle.put("valor", req.valor());
        AuditoriaDetalle.entidadId(abono.getId());
        AuditoriaDetalle.poner(detalle);

        eventoSistemaService.registrarEvento(TipoEvento.ABONO_COMERCIANTE,
                "Abono de $" + req.valor().toPlainString() + " de " + comerciante.getNombre() + ".",
                usuario.getId());

        return vistaDe(comercianteId);
    }

    // ── Evaluación para PlanillaService.crearDespacho ───────────────────────

    /** Política efectiva, deuda y planillas vencidas del comerciante, para aplicar al crear un despacho. */
    public Evaluacion evaluar(UUID comercianteId) {
        PoliticaCredito politicaEfectiva = politicaEfectivaDe(comercianteId);
        Saldos saldos = calcularSaldos(comercianteId);
        return new Evaluacion(politicaEfectiva.getPoliticaVencimiento(), politicaEfectiva.getCupo(),
                saldos.deuda(), saldos.vencidas());
    }

    /** Plazo (días) de la política efectiva del comerciante, para fijar la fecha límite de pago al liquidar. */
    public int plazoDiasEfectivo(UUID comercianteId) {
        return politicaEfectivaDe(comercianteId).getPlazoDias();
    }

    // ── Privados ─────────────────────────────────────────────────────────────

    /** Foto de una política para la auditoría (se toma antes de modificar la entidad). */
    private static Map<String, Object> valoresDe(PoliticaCredito p) {
        Map<String, Object> valores = new LinkedHashMap<>();
        valores.put("cupo", p.getCupo());
        valores.put("plazoDias", p.getPlazoDias());
        valores.put("politica", p.getPoliticaVencimiento());
        return valores;
    }

    /** FP-15 §2.16.3 — cupo, plazo y política anteriores y nuevos. {@code comerciante} null = la de la distribuidora. */
    private static void auditarCambioDePolitica(Comerciante comerciante, Map<String, Object> antes,
                                                Map<String, Object> despues) {
        Map<String, Object> detalle = new LinkedHashMap<>();
        if (comerciante != null) detalle.put("comerciante", comerciante.getNombre());
        detalle.put("antes", antes);
        detalle.put("despues", despues);
        AuditoriaDetalle.poner(detalle);
    }

    private CreditoVistaDTO vistaDe(UUID comercianteId) {
        PoliticaCredito politicaDefecto = politicaPorDefecto();
        Optional<PoliticaCredito> excepcion = excepcionActivaDe(comercianteId);
        PoliticaCredito efectiva = excepcion.orElse(politicaDefecto);
        Saldos saldos = calcularSaldos(comercianteId);
        List<AbonoDTO> abonos = abonoRepository.findByComerciante_IdOrderByFechaDesc(comercianteId).stream()
                .map(AbonoDTO::from).toList();

        return new CreditoVistaDTO(
                comercianteId,
                excepcion.isPresent() ? "EXCEPCION" : "DEFECTO",
                efectiva.getCupo(), efectiva.getPlazoDias(), efectiva.getPoliticaVencimiento(),
                new CreditoVistaDTO.PoliticaDefectoDTO(
                        politicaDefecto.getCupo(), politicaDefecto.getPlazoDias(), politicaDefecto.getPoliticaVencimiento()),
                saldos.deuda(), efectiva.getCupo().subtract(saldos.deuda()).max(BigDecimal.ZERO),
                saldos.planillas(), abonos);
    }

    /**
     * Reparte los abonos (del más antiguo al más reciente) sobre las planillas
     * cerradas (de la más antigua a la más reciente) — espejo exacto de
     * distribuirAbono() en creditoUtils.js del frontend.
     */
    private Saldos calcularSaldos(UUID comercianteId) {
        List<PlanillaComerciante> planillas = planillaRepository.findCerradasPorComercianteOrdenadas(comercianteId);
        BigDecimal abonoRestante = abonoRepository.findByComerciante_IdOrderByFechaAsc(comercianteId).stream()
                .map(AbonoComerciante::getValor)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        LocalDate hoy = LocalDate.now(ZONA_BOGOTA);
        List<PlanillaSaldoDTO> conSaldo = new ArrayList<>();
        BigDecimal deudaTotal = BigDecimal.ZERO;
        long vencidas = 0;

        for (PlanillaComerciante p : planillas) {
            BigDecimal valor = p.getTotalGanancia() != null ? p.getTotalGanancia() : BigDecimal.ZERO;
            BigDecimal recibido = p.getEfectivoRecibido() != null ? p.getEfectivoRecibido() : BigDecimal.ZERO;
            BigDecimal saldoInicial = valor.subtract(recibido).max(BigDecimal.ZERO);

            BigDecimal aplicado = abonoRestante.min(saldoInicial);
            abonoRestante = abonoRestante.subtract(aplicado);
            BigDecimal saldoActual = saldoInicial.subtract(aplicado);

            if (saldoActual.signum() > 0) {
                boolean vencida = p.getFechaLimitePago() != null && p.getFechaLimitePago().isBefore(hoy);
                Long diasMora = vencida ? ChronoUnit.DAYS.between(p.getFechaLimitePago(), hoy) : null;
                conSaldo.add(new PlanillaSaldoDTO(p.getId(), p.getFecha(), p.getFechaLimitePago(), valor, saldoActual, vencida, diasMora));
                deudaTotal = deudaTotal.add(saldoActual);
                if (vencida) vencidas++;
            }
        }
        return new Saldos(conSaldo, deudaTotal, vencidas);
    }

    private PoliticaCredito politicaEfectivaDe(UUID comercianteId) {
        return excepcionActivaDe(comercianteId).orElseGet(this::politicaPorDefecto);
    }

    private Optional<PoliticaCredito> excepcionActivaDe(UUID comercianteId) {
        UUID tenantId = TenantContext.requerido();
        return politicaRepository.findByTenantIdAndComerciante_Id(tenantId, comercianteId)
                .filter(p -> Boolean.TRUE.equals(p.getActivo()));
    }

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
