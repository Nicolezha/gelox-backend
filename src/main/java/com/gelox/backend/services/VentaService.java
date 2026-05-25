package com.gelox.backend.services;

import com.gelox.backend.dto.CatalogoVentaDTO;
import com.gelox.backend.dto.IniciarVentaRequest;
import com.gelox.backend.dto.VentaResponseDTO;
import com.gelox.backend.entities.*;
import com.gelox.backend.repositories.ProductoRepository;
import com.gelox.backend.repositories.VentaRepository;
import com.gelox.backend.security.RequiereRol;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class VentaService {

    private final VentaRepository       ventaRepository;
    private final ProductoRepository    productoRepository;
    private final EventoSistemaService  eventoSistemaService;

    @RequiereRol({"ADMINISTRADOR", "ENCARGADO_VENTAS"})
    public VentaResponseDTO iniciarVenta(IniciarVentaRequest req, Usuario usuario) {
        Venta venta = Venta.builder()
                .canal(req.canal())
                .usuario(usuario)
                .build();

        Venta guardada = ventaRepository.save(venta);

        eventoSistemaService.registrarEvento(
                TipoEvento.INICIAR_VENTA,
                String.format("Venta iniciada por canal %s.", req.canal().name()),
                usuario.getId()
        );

        return toDTO(guardada);
    }

    @RequiereRol({"ADMINISTRADOR", "ENCARGADO_VENTAS"})
    @Transactional(readOnly = true)
    public List<CatalogoVentaDTO> getCatalogo() {
        return productoRepository.findByActivoTrueOrderByNombreAsc()
                .stream()
                .map(p -> new CatalogoVentaDTO(
                        p.getId(),
                        p.getCodigoTecnico(),
                        p.getNombre(),
                        p.getImagenUrl(),
                        p.getPrecioVenta(),
                        p.getStockActual(),
                        p.getStockActual() > 0
                ))
                .toList();
    }

    private VentaResponseDTO toDTO(Venta v) {
        return new VentaResponseDTO(
                v.getId(),
                v.getCanal().name(),
                v.getEstado().name(),
                v.getFecha(),
                v.getTotal()
        );
    }
}
