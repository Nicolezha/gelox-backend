package com.gelox.backend.credito;

import com.gelox.backend.credito.dto.EsquemaLiquidacionDTO;
import com.gelox.backend.credito.dto.EsquemaLiquidacionRequest;
import com.gelox.backend.entities.EsquemaLiquidacion;
import com.gelox.backend.gobierno.Auditable;
import com.gelox.backend.repositories.EsquemaLiquidacionRepository;
import com.gelox.backend.security.RequiereRol;
import com.gelox.backend.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

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
        EsquemaLiquidacion esquema = EsquemaLiquidacion.builder()
                .tipo(req.tipo())
                .valor(req.valor())
                .build();
        return EsquemaLiquidacionDTO.from(repository.save(esquema));
    }

    /** El esquema vigente, para que LiquidacionService lo aplique al liquidar. */
    public EsquemaLiquidacion vigente() {
        UUID tenantId = TenantContext.requerido();
        return repository.findTopByTenantIdOrderByVigenteDesdeDesc(tenantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                        "La distribuidora no tiene esquema de liquidación"));
    }
}
