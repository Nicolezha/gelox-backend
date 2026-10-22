package com.gelox.backend.services;

import com.gelox.backend.dto.CatalogoProductoPublicoDTO;
import com.gelox.backend.dto.IdentidadDTO;
import com.gelox.backend.dto.InfoNegocioDTO;
import com.gelox.backend.entities.CategoriaProducto;
import com.gelox.backend.entities.Distribuidora;
import com.gelox.backend.entities.EstadoDistribuidora;
import com.gelox.backend.entities.Producto;
import com.gelox.backend.repositories.DistribuidoraRepository;
import com.gelox.backend.repositories.ProductoRepository;
import com.gelox.backend.tenant.TenantContext;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * RF61 — Landing pública por subdominio: los datos salen de la distribuidora, no de
 * application.properties. Las rutas sin subdominio responden con la distribuidora piloto.
 */
@Service
@Transactional(readOnly = true)
public class LandingService {

    /** Distribuidora piloto: responde las rutas de la landing que no traen subdominio. */
    public static final String SUBDOMINIO_PILOTO = "magicosabor";

    private static final Set<EstadoDistribuidora> ESTADOS_VISIBLES =
            EnumSet.of(EstadoDistribuidora.ACTIVO, EstadoDistribuidora.PRUEBA);

    private final DistribuidoraRepository distribuidoraRepository;
    private final ProductoRepository productoRepository;

    public LandingService(DistribuidoraRepository distribuidoraRepository,
                          ProductoRepository productoRepository) {
        this.distribuidoraRepository = distribuidoraRepository;
        this.productoRepository = productoRepository;
    }

    /** Productos activos de la distribuidora agrupados por categoría. */
    public Map<String, List<CatalogoProductoPublicoDTO>> productos(String subdominio) {
        List<Producto> productos = conTenantDe(resolver(subdominio), productoRepository::findByActivoTrue);
        return productos.stream()
                .filter(this::tienePrecio)
                .map(this::toDTO)
                .filter(dto -> dto.categoria() != null)
                .collect(Collectors.groupingBy(CatalogoProductoPublicoDTO::categoria));
    }

    public String whatsapp(String subdominio) {
        Distribuidora d = resolver(subdominio);
        if (d.getWhatsapp() == null || d.getWhatsapp().isBlank()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "La distribuidora no tiene WhatsApp configurado");
        }
        return "https://wa.me/" + d.getWhatsapp();
    }

    public InfoNegocioDTO infoNegocio(String subdominio) {
        Distribuidora d = resolver(subdominio);
        return new InfoNegocioDTO(
                d.getNombreComercial(),
                d.getEslogan(),
                d.getDireccion(),
                d.getBarrio(),
                d.getCiudad(),
                d.getHorario(),
                d.getMapsUrl(),
                d.getWhatsapp()
        );
    }

    public IdentidadDTO identidad(String subdominio) {
        return IdentidadDTO.from(resolver(subdominio));
    }

    /** Productos activos de una sola categoría (rutas /productos/paletas, /conos y /familiares). */
    public List<CatalogoProductoPublicoDTO> productos(String subdominio, CategoriaProducto categoria) {
        return conTenantDe(resolver(subdominio),
                () -> productoRepository.findByActivoTrueAndCategoria(categoria))
                .stream().filter(this::tienePrecio).map(this::toDTO).toList();
    }

    /** Distribuidora visible por subdominio: si no existe o no está ACTIVO/PRUEBA → 404. */
    private Distribuidora resolver(String subdominio) {
        return distribuidoraRepository.findBySubdominio(subdominio.trim().toLowerCase())
                .filter(d -> ESTADOS_VISIBLES.contains(d.getEstado()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Distribuidora no encontrada"));
    }

    /**
     * La landing es pública (sin token, sin tenant): se pone a mano el de la distribuidora para
     * que el filtro de Hibernate aplique, y al terminar se restaura el que hubiera.
     */
    private <T> T conTenantDe(Distribuidora distribuidora, Supplier<T> consulta) {
        UUID anterior = TenantContext.get();
        TenantContext.set(distribuidora.getId());
        try {
            return consulta.get();
        } finally {
            if (anterior != null) {
                TenantContext.set(anterior);
            } else {
                TenantContext.clear();
            }
        }
    }

    /** RF43 — la landing no muestra productos con precio de venta ≤ 0. */
    private boolean tienePrecio(Producto p) {
        return p.getPrecioVenta() != null && p.getPrecioVenta().compareTo(java.math.BigDecimal.ZERO) > 0;
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
