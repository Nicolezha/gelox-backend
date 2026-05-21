package com.gelox.backend.services;

import com.gelox.backend.dto.CrearProductoDTO;
import com.gelox.backend.dto.EditarProductoDTO;
import com.gelox.backend.dto.ProductoResponseDTO;
import com.gelox.backend.entities.CategoriaProducto;
import com.gelox.backend.entities.Producto;
import com.gelox.backend.entities.TipoEvento;
import com.gelox.backend.repositories.ProductoRepository;
import com.gelox.backend.security.RequiereRol;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class ProductoService {

    private final ProductoRepository productoRepository;
    private final EventoSistemaService eventoSistemaService;

    @RequiereRol({"ADMINISTRADOR", "ENCARGADO_INVENTARIO"})
    public ProductoResponseDTO crearProducto(CrearProductoDTO dto, UUID usuarioId) {
        validarCategoria(dto.getCategoria());

        if (productoRepository.existsByCodigoTecnico(dto.getCodigoTecnico())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Ya existe un producto con el código técnico: " + dto.getCodigoTecnico());
        }

        Producto producto = new Producto();
        producto.setCodigoTecnico(dto.getCodigoTecnico());
        producto.setNombre(dto.getNombre());
        producto.setPrecioVenta(dto.getPrecioVenta());
        producto.setPrecioCosto(dto.getPrecioCosto());
        producto.setCategoria(CategoriaProducto.valueOf(dto.getCategoria()));
        producto.setStockActual(dto.getStockActual() != null ? dto.getStockActual() : 0);
        producto.setStockMinimo(dto.getStockMinimo() != null ? dto.getStockMinimo() : 0);
        producto.setImagenUrl(dto.getImagenUrl());
        producto.setDescripcion(dto.getDescripcion());
        producto.setActivo(true);

        Producto guardado = productoRepository.save(producto);

        eventoSistemaService.registrarEvento(
                TipoEvento.CREAR_PRODUCTO,
                "Producto creado: " + guardado.getNombre() + " (Código: " + guardado.getCodigoTecnico() + ")",
                usuarioId
        );

        return toDTO(guardado);
    }

    @RequiereRol({"ADMINISTRADOR", "ENCARGADO_INVENTARIO"})
    public ProductoResponseDTO editarProducto(UUID id, EditarProductoDTO dto, UUID usuarioId) {
        validarCategoria(dto.getCategoria());

        Producto producto = productoRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Producto no encontrado con id: " + id));

        producto.setNombre(dto.getNombre());
        producto.setPrecioVenta(dto.getPrecioVenta());
        producto.setPrecioCosto(dto.getPrecioCosto());
        producto.setCategoria(CategoriaProducto.valueOf(dto.getCategoria()));
        if (dto.getStockMinimo() != null) producto.setStockMinimo(dto.getStockMinimo());
        if (dto.getImagenUrl() != null) producto.setImagenUrl(dto.getImagenUrl());
        if (dto.getDescripcion() != null) producto.setDescripcion(dto.getDescripcion());
        if (dto.getActivo() != null) producto.setActivo(dto.getActivo());

        Producto actualizado = productoRepository.save(producto);

        eventoSistemaService.registrarEvento(
                TipoEvento.EDITAR_PRODUCTO,
                "Producto editado: " + actualizado.getNombre() + " (Código: " + actualizado.getCodigoTecnico() + ")",
                usuarioId
        );

        return toDTO(actualizado);
    }

    @RequiereRol({"ADMINISTRADOR", "ENCARGADO_INVENTARIO"})
    public void desactivarProducto(UUID id, UUID usuarioId) {
        Producto producto = productoRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Producto no encontrado con id: " + id));

        producto.setActivo(false);
        productoRepository.save(producto);

        eventoSistemaService.registrarEvento(
                TipoEvento.DESACTIVAR_PRODUCTO,
                "Producto desactivado: " + producto.getNombre() + " (Código: " + producto.getCodigoTecnico() + ")",
                usuarioId
        );
    }

    @RequiereRol({"ADMINISTRADOR", "ENCARGADO_INVENTARIO"})
    public void activarProducto(UUID id, UUID usuarioId) {
        Producto producto = productoRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Producto no encontrado con id: " + id));

        producto.setActivo(true);
        productoRepository.save(producto);

        eventoSistemaService.registrarEvento(
                TipoEvento.ACTIVAR_PRODUCTO,
                "Producto activado: " + producto.getNombre() + " (Código: " + producto.getCodigoTecnico() + ")",
                usuarioId
        );
    }

    @RequiereRol({"ADMINISTRADOR", "ENCARGADO_INVENTARIO"})
    @Transactional(readOnly = true)
    public List<ProductoResponseDTO> listarProductos() {
        return productoRepository.findAll()
                .stream()
                .map(this::toDTO)
                .toList();
    }

    @RequiereRol({"ADMINISTRADOR", "ENCARGADO_INVENTARIO"})
    @Transactional(readOnly = true)
    public ProductoResponseDTO obtenerProducto(UUID id) {
        return productoRepository.findById(id)
                .map(this::toDTO)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Producto no encontrado con id: " + id));
    }

    private void validarCategoria(String categoria) {
        try {
            CategoriaProducto.valueOf(categoria);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Categoría inválida. Use: PALETAS, CONOS o FAMILIARES");
        }
    }

    private ProductoResponseDTO toDTO(Producto p) {
        return new ProductoResponseDTO(
                p.getId(),
                p.getCodigoTecnico(),
                p.getNombre(),
                p.getPrecioVenta(),
                p.getPrecioCosto(),
                p.getStockActual(),
                p.getStockMinimo(),
                p.getImagenUrl(),
                p.getDescripcion(),
                p.getCategoria() != null ? p.getCategoria().name() : null,
                p.getActivo(),
                p.getCreatedAt(),
                p.getUpdatedAt()
        );
    }
}