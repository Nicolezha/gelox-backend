package com.gelox.backend.controllers;

import com.gelox.backend.dto.CatalogoProductoPublicoDTO;
import com.gelox.backend.dto.InfoNegocioDTO;
import com.gelox.backend.services.LandingService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/landing")
public class LandingController {

    private final LandingService landingService;

    public LandingController(LandingService landingService) {
        this.landingService = landingService;
    }

    @GetMapping("/productos")
    public ResponseEntity<Map<String, List<CatalogoProductoPublicoDTO>>> productos() {
        return ResponseEntity.ok(landingService.obtenerCatalogo());
    }

    @GetMapping("/productos/paletas")
    public ResponseEntity<List<CatalogoProductoPublicoDTO>> paletas() {
        return ResponseEntity.ok(landingService.obtenerPaletas());
    }

    @GetMapping("/productos/conos")
    public ResponseEntity<List<CatalogoProductoPublicoDTO>> conos() {
        return ResponseEntity.ok(landingService.obtenerConos());
    }

    @GetMapping("/productos/familiares")
    public ResponseEntity<List<CatalogoProductoPublicoDTO>> familiares() {
        return ResponseEntity.ok(landingService.obtenerFamiliares());
    }

    @GetMapping("/whatsapp")
    public ResponseEntity<Map<String, String>> whatsapp() {
        return ResponseEntity.ok(Map.of("url", landingService.obtenerUrlWhatsApp()));
    }

    @GetMapping("/info-negocio")
    public ResponseEntity<InfoNegocioDTO> infoNegocio() {
        return ResponseEntity.ok(landingService.obtenerInfoNegocio());
    }
}
