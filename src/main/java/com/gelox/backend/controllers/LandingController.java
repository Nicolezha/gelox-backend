package com.gelox.backend.controllers;

import com.gelox.backend.dto.CatalogoProductoPublicoDTO;
import com.gelox.backend.dto.IdentidadDTO;
import com.gelox.backend.dto.InfoNegocioDTO;
import com.gelox.backend.entities.CategoriaProducto;
import com.gelox.backend.services.LandingService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

import static com.gelox.backend.services.LandingService.SUBDOMINIO_PILOTO;

/**
 * Landing pública (permitAll). RF61: los datos salen de la distribuidora del subdominio;
 * las rutas sin subdominio responden con la distribuidora piloto.
 */
@RestController
@RequestMapping("/api/landing")
public class LandingController {

    private final LandingService landingService;

    public LandingController(LandingService landingService) {
        this.landingService = landingService;
    }

    @GetMapping("/productos")
    public ResponseEntity<Map<String, List<CatalogoProductoPublicoDTO>>> productos() {
        return ResponseEntity.ok(landingService.productos(SUBDOMINIO_PILOTO));
    }

    @GetMapping("/productos/paletas")
    public ResponseEntity<List<CatalogoProductoPublicoDTO>> paletas() {
        return ResponseEntity.ok(landingService.productos(SUBDOMINIO_PILOTO, CategoriaProducto.PALETAS));
    }

    @GetMapping("/productos/conos")
    public ResponseEntity<List<CatalogoProductoPublicoDTO>> conos() {
        return ResponseEntity.ok(landingService.productos(SUBDOMINIO_PILOTO, CategoriaProducto.CONOS));
    }

    @GetMapping("/productos/familiares")
    public ResponseEntity<List<CatalogoProductoPublicoDTO>> familiares() {
        return ResponseEntity.ok(landingService.productos(SUBDOMINIO_PILOTO, CategoriaProducto.FAMILIARES));
    }

    @GetMapping("/whatsapp")
    public ResponseEntity<Map<String, String>> whatsapp() {
        return whatsapp(SUBDOMINIO_PILOTO);
    }

    @GetMapping("/info-negocio")
    public ResponseEntity<InfoNegocioDTO> infoNegocio() {
        return infoNegocio(SUBDOMINIO_PILOTO);
    }

    // ── RF61 — landing por subdominio ──

    @GetMapping("/{subdominio}/productos")
    public ResponseEntity<Map<String, List<CatalogoProductoPublicoDTO>>> productos(@PathVariable String subdominio) {
        return ResponseEntity.ok(landingService.productos(subdominio));
    }

    @GetMapping("/{subdominio}/whatsapp")
    public ResponseEntity<Map<String, String>> whatsapp(@PathVariable String subdominio) {
        return ResponseEntity.ok(Map.of("url", landingService.whatsapp(subdominio)));
    }

    @GetMapping("/{subdominio}/info-negocio")
    public ResponseEntity<InfoNegocioDTO> infoNegocio(@PathVariable String subdominio) {
        return ResponseEntity.ok(landingService.infoNegocio(subdominio));
    }

    @GetMapping("/{subdominio}/identidad")
    public ResponseEntity<IdentidadDTO> identidad(@PathVariable String subdominio) {
        return ResponseEntity.ok(landingService.identidad(subdominio));
    }
}
