package com.gelox.backend.credito;

import com.gelox.backend.credito.dto.AbonoRequest;
import com.gelox.backend.credito.dto.CreditoVistaDTO;
import com.gelox.backend.credito.dto.PoliticaCreditoDTO;
import com.gelox.backend.credito.dto.PoliticaCreditoRequest;
import com.gelox.backend.entities.Usuario;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/** RF65 — Política de crédito, estado de crédito por comerciante y abonos (FP-15 §2.17.1). */
@RestController
@RequiredArgsConstructor
public class CreditoController {

    private final CreditoService creditoService;

    @GetMapping("/api/credito/politica")
    public ResponseEntity<PoliticaCreditoDTO> obtenerPolitica() {
        return ResponseEntity.ok(creditoService.obtenerPoliticaDistribuidora());
    }

    @PutMapping("/api/credito/politica")
    public ResponseEntity<PoliticaCreditoDTO> actualizarPolitica(@Valid @RequestBody PoliticaCreditoRequest req) {
        return ResponseEntity.ok(creditoService.actualizarPoliticaDistribuidora(req));
    }

    @GetMapping("/api/comerciantes/{id}/credito")
    public ResponseEntity<CreditoVistaDTO> obtenerCredito(@PathVariable UUID id) {
        return ResponseEntity.ok(creditoService.obtenerCreditoComerciante(id));
    }

    @PutMapping("/api/comerciantes/{id}/credito")
    public ResponseEntity<CreditoVistaDTO> definirExcepcion(
            @PathVariable UUID id, @Valid @RequestBody PoliticaCreditoRequest req) {
        return ResponseEntity.ok(creditoService.definirExcepcion(id, req));
    }

    @DeleteMapping("/api/comerciantes/{id}/credito")
    public ResponseEntity<CreditoVistaDTO> eliminarExcepcion(@PathVariable UUID id) {
        return ResponseEntity.ok(creditoService.eliminarExcepcion(id));
    }

    @PostMapping("/api/comerciantes/{id}/abonos")
    public ResponseEntity<CreditoVistaDTO> registrarAbono(
            @PathVariable UUID id,
            @Valid @RequestBody AbonoRequest req,
            @AuthenticationPrincipal Usuario usuario) {
        return ResponseEntity.status(HttpStatus.CREATED).body(creditoService.registrarAbono(id, req, usuario));
    }
}
