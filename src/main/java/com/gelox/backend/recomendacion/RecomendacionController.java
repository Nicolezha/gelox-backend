package com.gelox.backend.recomendacion;

import com.gelox.backend.entities.Usuario;
import com.gelox.backend.recomendacion.dto.GenerarRecomendacionRequest;
import com.gelox.backend.recomendacion.dto.RecomendacionGeneradaDTO;
import com.gelox.backend.security.RequiereRol;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * RF54 — POST /api/recomendaciones   Generar la recomendación de compras
 *
 * <p>Las demás rutas de /api/recomendaciones (detalle, decisión, pedido, historial y métricas)
 * están en {@link RecomendacionDecisionController}.</p>
 */
@RestController
@RequestMapping("/api/recomendaciones")
@RequiredArgsConstructor
public class RecomendacionController {

    private final RecomendacionCompraService service;

    /** Presupuesto ≤ 0 o ausente → 400 {"error": "El presupuesto debe ser mayor a cero"}. */
    @PostMapping
    @RequiereRol({"ADMINISTRADOR", "ENCARGADO_INVENTARIO"})
    public ResponseEntity<RecomendacionGeneradaDTO> generar(
            @RequestBody(required = false) GenerarRecomendacionRequest req,
            @AuthenticationPrincipal Usuario usuario) {

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.generar(req != null ? req.presupuesto() : null,
                        req != null ? req.origen() : null, usuario));
    }
}
