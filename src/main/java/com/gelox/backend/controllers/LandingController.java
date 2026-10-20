package com.gelox.backend.controllers;

import com.gelox.backend.dto.CatalogoProductoPublicoDTO;
import com.gelox.backend.dto.IdentidadDTO;
import com.gelox.backend.dto.InfoNegocioDTO;
import com.gelox.backend.services.LandingService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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

    // ── RF61 — landing por subdominio (las rutas de arriba usan la distribuidora piloto) ──

    @GetMapping("/{subdominio}/productos")
    public ResponseEntity<Map<String, List<CatalogoProductoPublicoDTO>>> productos(@PathVariable String subdominio) {
        return ResponseEntity.ok(landingService.obtenerCatalogo(subdominio));
    }

    @GetMapping("/{subdominio}/whatsapp")
    public ResponseEntity<Map<String, String>> whatsapp(@PathVariable String subdominio) {
        return ResponseEntity.ok(Map.of("url", landingService.obtenerUrlWhatsApp(subdominio)));
    }

    @GetMapping("/{subdominio}/info-negocio")
    public ResponseEntity<InfoNegocioDTO> infoNegocio(@PathVariable String subdominio) {
        return ResponseEntity.ok(landingService.obtenerInfoNegocio(subdominio));
    }

    @GetMapping("/{subdominio}/identidad")
    public ResponseEntity<IdentidadDTO> identidad(@PathVariable String subdominio) {
        return ResponseEntity.ok(landingService.obtenerIdentidad(subdominio));
    }
}
