package com.gelox.backend.credito;

import com.gelox.backend.credito.dto.EsquemaLiquidacionDTO;
import com.gelox.backend.credito.dto.EsquemaLiquidacionRequest;
import com.gelox.backend.entities.EsquemaLiquidacion;
import com.gelox.backend.entities.TipoEsquemaLiquidacion;
import com.gelox.backend.gobierno.Auditable;
import com.gelox.backend.repositories.EsquemaLiquidacionRepository;
import com.gelox.backend.security.RequiereRol;
import com.gelox.backend.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.UUID;

/** RF66 — esquema de liquidación vigente de la distribuidora (FP-15 §2.17.2). */
@Service
@RequiredArgsConstructor
@Transactional
public class EsquemaLiquidacionService {

    private final EsquemaLiquidacionRepository repository;

    @RequiereRol({"ADMINISTRADOR", "ENCARGADO_VENTAS"})
    public EsquemaLiquidacionDTO obtenerActual() {
        return EsquemaLiquidacionDTO.from(vigente());
    }

    @RequiereRol("ADMINISTRADOR")
    @Auditable(accion = "ESQUEMA_LIQUIDACION_CAMBIAR", entidad = "EsquemaLiquidacion")
    public EsquemaLiquidacionDTO actualizar(EsquemaLiquidacionRequest req) {
        validar(req);
        BigDecimal valor = req.tipo() == TipoEsquemaLiquidacion.MARGEN
                ? BigDecimal.ZERO
                : req.valor();
        EsquemaLiquidacion esquema = EsquemaLiquidacion.builder()
                .tipo(req.tipo())
                .valor(valor)
                .build();
        return EsquemaLiquidacionDTO.from(repository.save(esquema));
    }

    private void validar(EsquemaLiquidacionRequest req) {
        if (req.tipo() == TipoEsquemaLiquidacion.COMISION) {
            if (req.valor() == null || req.valor().compareTo(BigDecimal.ZERO) <= 0 || req.valor().compareTo(BigDecimal.valueOf(100)) > 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La comisión debe ser mayor a 0 y máximo 100 %");
            }
        } else if (req.tipo() == TipoEsquemaLiquidacion.TARIFA_FIJA) {
            if (req.valor() == null || req.valor().compareTo(BigDecimal.ZERO) < 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La tarifa no puede ser negativa");
            }
        }
    }

    /** El esquema vigente, para que LiquidacionService lo aplique al liquidar. */
    public EsquemaLiquidacion vigente() {
        UUID tenantId = TenantContext.requerido();
        return repository.findTopByTenantIdOrderByVigenteDesdeDesc(tenantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                        "La distribuidora no tiene esquema de liquidación"));
    }
}
