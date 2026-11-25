package com.gelox.backend.credito;

import com.gelox.backend.credito.dto.EsquemaLiquidacionDTO;
import com.gelox.backend.credito.dto.EsquemaLiquidacionRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** RF66 — Esquema de liquidación vigente de la distribuidora (FP-15 §2.17.2). */
@RestController
@RequestMapping("/api/liquidacion/esquema")
@RequiredArgsConstructor
public class EsquemaLiquidacionController {

    private final EsquemaLiquidacionService esquemaLiquidacionService;

    @GetMapping
    public ResponseEntity<EsquemaLiquidacionDTO> obtenerActual() {
        return ResponseEntity.ok(esquemaLiquidacionService.obtenerActual());
    }

    @PutMapping
    public ResponseEntity<EsquemaLiquidacionDTO> actualizar(@Valid @RequestBody EsquemaLiquidacionRequest req) {
        return ResponseEntity.ok(esquemaLiquidacionService.actualizar(req));
    }
}
