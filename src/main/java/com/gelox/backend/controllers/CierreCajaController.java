package com.gelox.backend.controllers;

import com.gelox.backend.dto.CierreCajaDTO;
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

    @GetMapping("/{fecha}")
    public ResponseEntity<CierreCajaResponseDTO> obtenerPorFecha(
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha) {

        return ResponseEntity.ok(cierreCajaService.obtenerPorFecha(fecha));
    }
}