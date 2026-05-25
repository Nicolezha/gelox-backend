package com.gelox.backend.controllers;

import com.gelox.backend.dto.CierreCajaDTO;
import com.gelox.backend.dto.CierreCajaPageResponseDTO;
import com.gelox.backend.dto.CierreCajaResponseDTO;
import com.gelox.backend.entities.Usuario;
import com.gelox.backend.services.CierreCajaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/cierre-caja")
@RequiredArgsConstructor
public class CierreCajaController {

    private final CierreCajaService cierreCajaService;

    @PostMapping
    public ResponseEntity<CierreCajaResponseDTO> registrar(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha,
            @Valid @RequestBody CierreCajaDTO dto,
            @AuthenticationPrincipal Usuario usuario) {

        LocalDate fechaCierre = fecha != null ? fecha : LocalDate.now();
        CierreCajaResponseDTO response = cierreCajaService.registrarDineroFisico(fechaCierre, dto, usuario);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<CierreCajaPageResponseDTO> listar(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(required = false) String estado,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int limit) {

        return ResponseEntity.ok(cierreCajaService.listar(desde, hasta, estado, page, limit));
    }

    @GetMapping("/{id}")
    public ResponseEntity<CierreCajaResponseDTO> obtenerPorId(@PathVariable UUID id) {
        return ResponseEntity.ok(cierreCajaService.obtenerPorId(id));
    }
}
