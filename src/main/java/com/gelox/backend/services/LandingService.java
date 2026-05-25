package com.gelox.backend.services;

import com.gelox.backend.config.NegocioProperties;
import com.gelox.backend.dto.CatalogoProductoPublicoDTO;
import com.gelox.backend.dto.InfoNegocioDTO;
import com.gelox.backend.entities.CategoriaProducto;
import com.gelox.backend.entities.Producto;
import com.gelox.backend.repositories.ProductoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class LandingService {

    private final NegocioProperties negocioProperties;
    private final ProductoRepository productoRepository;

    public LandingService(NegocioProperties negocioProperties,
                          ProductoRepository productoRepository) {
        this.negocioProperties = negocioProperties;
        this.productoRepository = productoRepository;
    }

    public Map<String, List<CatalogoProductoPublicoDTO>> obtenerCatalogo() {
        List<Producto> productos = productoRepository.findByActivoTrue();
        return productos.stream()
                .map(this::toDTO)
                .filter(dto -> dto.categoria() != null)
                .collect(Collectors.groupingBy(CatalogoProductoPublicoDTO::categoria));
    }

    public String obtenerUrlWhatsApp() {
        return "https://wa.me/" + negocioProperties.getWhatsapp().getNumero();
    }

    public InfoNegocioDTO obtenerInfoNegocio() {
        return new InfoNegocioDTO(
                negocioProperties.getDireccion(),
                negocioProperties.getBarrio(),
                negocioProperties.getCiudad(),
                negocioProperties.getHorario(),
                negocioProperties.getMaps().getUrl()
        );
    }

    public List<CatalogoProductoPublicoDTO> obtenerPaletas() {
        return productoRepository.findByActivoTrueAndCategoria(CategoriaProducto.PALETAS)
                .stream().map(this::toDTO).toList();
    }

    public List<CatalogoProductoPublicoDTO> obtenerConos() {
        return productoRepository.findByActivoTrueAndCategoria(CategoriaProducto.CONOS)
                .stream().map(this::toDTO).toList();
    }

    public List<CatalogoProductoPublicoDTO> obtenerFamiliares() {
        return productoRepository.findByActivoTrueAndCategoria(CategoriaProducto.FAMILIARES)
                .stream().map(this::toDTO).toList();
    }

    private CatalogoProductoPublicoDTO toDTO(Producto p) {
        return new CatalogoProductoPublicoDTO(
                p.getNombre(),
                p.getImagenUrl(),
                p.getPrecioVenta(),
                p.getDescripcion(),
                p.getCategoria() != null ? p.getCategoria().name() : null
        );
    }
}
