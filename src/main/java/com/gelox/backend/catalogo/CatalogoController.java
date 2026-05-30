package com.gelox.backend.catalogo;

import com.gelox.backend.catalogo.dto.CatalogoProductoDTO;
import com.gelox.backend.catalogo.dto.CrearProductoRequest;
import com.gelox.backend.catalogo.dto.EditarProductoRequest;
import com.gelox.backend.catalogo.dto.PagedResponse;
import com.gelox.backend.entities.Usuario;
import com.gelox.backend.services.SupabaseStorageService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * RF18 — GET  /api/catalogo/productos        Listar con filtro y paginación
 * RF19 — POST /api/catalogo/productos        Crear producto (JSON o multipart)
 * RF20 — PUT  /api/catalogo/productos/{id}   Editar producto (JSON o multipart)
 * RF20 — DELETE /api/catalogo/productos/{id} Eliminar (soft delete)
 */
@RestController
@RequestMapping("/api/catalogo/productos")
@RequiredArgsConstructor
public class CatalogoController {

    private final CatalogoService        catalogoService;
    private final SupabaseStorageService storageService;

    // ── RF18 ──────────────────────────────────────────────────────────────────
    @GetMapping
    public ResponseEntity<PagedResponse<CatalogoProductoDTO>> listar(
            @RequestParam(required = false) String categoria,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal Usuario usuario) {

        return ResponseEntity.ok(
                catalogoService.listarProductos(categoria, page, size, usuario));
    }

    // ── RF19 — crear (JSON) ───────────────────────────────────────────────────
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<CatalogoProductoDTO> crear(
            @Valid @RequestBody CrearProductoRequest request,
            @AuthenticationPrincipal Usuario usuario) {

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(catalogoService.crearProducto(request, usuario));
    }

    // ── RF19 — crear (multipart/form-data con imagen opcional) ───────────────
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<CatalogoProductoDTO> crearConImagen(
            @RequestParam                    String        codigoTecnico,
            @RequestParam                    String        nombre,
            @RequestParam                    String        categoria,
            @RequestParam                    BigDecimal    precioVenta,
            @RequestParam(required = false)  BigDecimal    precioCosto,
            @RequestParam(required = false)  String        descripcion,
            @RequestParam(required = false)  Integer       stockMinimo,
            @RequestParam(required = false)  Integer       stockMedio,
            @RequestParam(required = false)  String        unidadMedida,
            @RequestParam(required = false)  MultipartFile imagen,
            @AuthenticationPrincipal         Usuario       usuario) {

        CrearProductoRequest request = new CrearProductoRequest();
        request.setCodigoTecnico(codigoTecnico);
        request.setNombre(nombre);
        request.setCategoria(categoria);
        request.setPrecioVenta(precioVenta);
        request.setPrecioCosto(precioCosto);
        request.setDescripcion(descripcion);
        request.setStockMinimo(stockMinimo);
        request.setStockMedio(stockMedio);
        request.setUnidadMedida(unidadMedida);

        if (imagen != null && !imagen.isEmpty()) {
            request.setImagenUrl(storageService.subirImagen(imagen, "productos"));
        }

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(catalogoService.crearProducto(request, usuario));
    }

    // ── RF20 — editar (JSON) ──────────────────────────────────────────────────
    @PutMapping(value = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<CatalogoProductoDTO> editar(
            @PathVariable UUID id,
            @Valid @RequestBody EditarProductoRequest request,
            @AuthenticationPrincipal Usuario usuario) {

        return ResponseEntity.ok(catalogoService.editarProducto(id, request, usuario));
    }

    // ── RF20 — editar (multipart/form-data con imagen opcional) ──────────────
    @PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<CatalogoProductoDTO> editarConImagen(
            @PathVariable                    UUID          id,
            @RequestParam(required = false)  String        nombre,
            @RequestParam(required = false)  String        categoria,
            @RequestParam(required = false)  BigDecimal    precioVenta,
            @RequestParam(required = false)  BigDecimal    precioCosto,
            @RequestParam(required = false)  String        descripcion,
            @RequestParam(required = false)  Integer       stockMinimo,
            @RequestParam(required = false)  Integer       stockMedio,
            @RequestParam(required = false)  String        unidadMedida,
            @RequestParam(required = false)  MultipartFile imagen,
            @AuthenticationPrincipal         Usuario       usuario) {

        EditarProductoRequest request = new EditarProductoRequest();
        request.setNombre(nombre);
        request.setCategoria(categoria);
        request.setPrecioVenta(precioVenta);
        request.setPrecioCosto(precioCosto);
        request.setDescripcion(descripcion);
        request.setStockMinimo(stockMinimo);
        request.setStockMedio(stockMedio);
        request.setUnidadMedida(unidadMedida);

        if (imagen != null && !imagen.isEmpty()) {
            request.setImagenUrl(storageService.subirImagen(imagen, "productos"));
        }

        return ResponseEntity.ok(catalogoService.editarProducto(id, request, usuario));
    }

    // ── RF20 — eliminar ───────────────────────────────────────────────────────
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(
            @PathVariable UUID id,
            @AuthenticationPrincipal Usuario usuario) {

        catalogoService.eliminarProducto(id);
        return ResponseEntity.noContent().build();
    }
}