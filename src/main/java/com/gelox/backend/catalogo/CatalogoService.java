package com.gelox.backend.catalogo;

import com.gelox.backend.catalogo.dto.CatalogoProductoDTO;
import com.gelox.backend.catalogo.dto.CrearProductoRequest;
import com.gelox.backend.catalogo.dto.EditarProductoRequest;
import com.gelox.backend.catalogo.dto.PagedResponse;
import com.gelox.backend.entities.CategoriaProducto;
import com.gelox.backend.entities.Producto;
import com.gelox.backend.entities.RolUsuario;
import com.gelox.backend.entities.Usuario;
import com.gelox.backend.security.RequiereRol;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class CatalogoService {

    private final CatalogoRepository catalogoRepository;

    // ─────────────────────────── RF18 ────────────────────────────

    @RequiereRol({"ADMINISTRADOR", "ENCARGADO_INVENTARIO", "ENCARGADO_VENTAS"})
    @Transactional(readOnly = true)
    public PagedResponse<CatalogoProductoDTO> listarProductos(
            String categoria, int page, int size, Usuario usuario) {

        // Limitar tamaño máximo
        int safeSize = Math.min(size, 100);
        Pageable pageable = PageRequest.of(page, safeSize);

        Page<Producto> resultPage;

        if (categoria != null && !categoria.isBlank()) {
            CategoriaProducto cat = parsearCategoria(categoria);
            resultPage = catalogoRepository
                    .findByActivoTrueAndCategoriaOrderByNombreAsc(cat, pageable);
        } else {
            resultPage = catalogoRepository.findByActivoTrueOrderByNombreAsc(pageable);
        }

        boolean esAdmin = usuario.getRol() == RolUsuario.ADMINISTRADOR;

        return new PagedResponse<>(
                resultPage.getContent().stream()
                        .map(p -> toDTO(p, esAdmin))
                        .toList(),
                resultPage.getNumber(),
                resultPage.getSize(),
                resultPage.getTotalElements(),
                resultPage.getTotalPages()
        );
    }

    // ─────────────────────────── RF19 ────────────────────────────

    @RequiereRol({"ADMINISTRADOR", "ENCARGADO_INVENTARIO"})
    public CatalogoProductoDTO crearProducto(CrearProductoRequest req, Usuario usuario) {

        // Validar categoría
        CategoriaProducto categoria = parsearCategoria(req.getCategoria());

        // Verificar código duplicado entre productos activos
        if (catalogoRepository.existsByCodigoTecnicoAndActivoTrue(req.getCodigoTecnico())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "El código ya está registrado en el catálogo");
        }

        Producto producto = new Producto();
        producto.setCodigoTecnico(req.getCodigoTecnico());
        producto.setNombre(req.getNombre());
        producto.setCategoria(categoria);
        producto.setPrecioVenta(req.getPrecioVenta());
        producto.setPrecioCosto(req.getPrecioCosto());
        producto.setDescripcion(req.getDescripcion());
        producto.setStockMinimo(req.getStockMinimo() != null ? req.getStockMinimo() : 0);
        producto.setStockMedio(req.getStockMedio() != null ? req.getStockMedio() : 0);
        producto.setStockActual(0);
        producto.setImagenUrl(req.getImagenUrl());
        producto.setUnidadMedida(req.getUnidadMedida() != null ? req.getUnidadMedida() : "Unidades");
        producto.setActivo(true);

        Producto guardado = catalogoRepository.save(producto);

        boolean esAdmin = usuario.getRol() == RolUsuario.ADMINISTRADOR;
        return toDTO(guardado, esAdmin);
    }

    // ─────────────────────────── RF20 — PUT ──────────────────────

    @RequiereRol({"ADMINISTRADOR", "ENCARGADO_INVENTARIO"})
    public CatalogoProductoDTO editarProducto(UUID id, EditarProductoRequest req, Usuario usuario) {

        Producto producto = catalogoRepository.findByIdAndActivoTrue(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Producto no encontrado"));

        // Verificar colisión de código con otro producto activo
        if (req.getCodigoTecnico() != null && !req.getCodigoTecnico().isBlank()) {
            if (catalogoRepository.existsByCodigoTecnicoAndActivoTrueAndIdNot(
                    req.getCodigoTecnico(), id)) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "El código ya está en uso por otro producto");
            }
            producto.setCodigoTecnico(req.getCodigoTecnico());
        }

        if (req.getNombre() != null)        producto.setNombre(req.getNombre());
        if (req.getCategoria() != null)     producto.setCategoria(parsearCategoria(req.getCategoria()));
        if (req.getPrecioVenta() != null)   producto.setPrecioVenta(req.getPrecioVenta());
        if (req.getPrecioCosto() != null)   producto.setPrecioCosto(req.getPrecioCosto());
        if (req.getDescripcion() != null)   producto.setDescripcion(req.getDescripcion());
        if (req.getStockMinimo() != null)   producto.setStockMinimo(req.getStockMinimo());
        if (req.getStockMedio()  != null)   producto.setStockMedio(req.getStockMedio());
        if (req.getImagenUrl() != null)     producto.setImagenUrl(req.getImagenUrl());
        if (req.getUnidadMedida() != null)  producto.setUnidadMedida(req.getUnidadMedida());

        Producto actualizado = catalogoRepository.save(producto);

        boolean esAdmin = usuario.getRol() == RolUsuario.ADMINISTRADOR;
        return toDTO(actualizado, esAdmin);
    }

    // ─────────────────────────── RF20 — DELETE ───────────────────

    @RequiereRol({"ADMINISTRADOR", "ENCARGADO_INVENTARIO"})
    public void eliminarProducto(UUID id) {

        Producto producto = catalogoRepository.findByIdAndActivoTrue(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Producto no encontrado o ya inactivo"));

        producto.setActivo(false);
        catalogoRepository.save(producto);
    }

    // ─────────────────────────── Helpers ─────────────────────────

    private CategoriaProducto parsearCategoria(String categoria) {
        try {
            return CategoriaProducto.valueOf(categoria.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Categoría inválida. Use: PALETAS, CONOS o FAMILIARES");
        }
    }

    private CatalogoProductoDTO toDTO(Producto p, boolean esAdmin) {
        return new CatalogoProductoDTO(
                p.getId().toString(),
                p.getCodigoTecnico(),
                p.getNombre(),
                p.getCategoria() != null ? p.getCategoria().name() : null,
                p.getPrecioVenta(),
                esAdmin ? p.getPrecioCosto() : null,   // solo ADMINISTRADOR ve precioCosto
                p.getDescripcion(),
                p.getStockMinimo(),
                p.getStockMedio(),
                p.getStockActual(),
                p.getImagenUrl(),
                p.getUnidadMedida()
        );
    }
}
