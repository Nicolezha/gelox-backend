package com.gelox.backend.services;

import com.gelox.backend.dto.AlertaStockDTO;
import com.gelox.backend.dto.InventarioProductoDTO;
import com.gelox.backend.entities.Producto;
import com.gelox.backend.repositories.ProductoRepository;
import com.gelox.backend.security.RequiereRol;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InventarioService {

    private final ProductoRepository productoRepository;

    @RequiereRol({"ADMINISTRADOR", "ENCARGADO_INVENTARIO"})
    public List<InventarioProductoDTO> listarInventario(String rolUsuario) {
        List<Producto> productos = productoRepository.findByActivoTrueOrderByNombreAsc();

        return productos.stream()
                .map(p -> toDTO(p, rolUsuario))
                .toList();
    }

    @RequiereRol({"ADMINISTRADOR", "ENCARGADO_INVENTARIO"})
    public List<AlertaStockDTO> listarAlertas() {
        return productoRepository.findProductosBajoStock()
                .stream()
                .map(this::toAlertaDTO)
                .toList();
    }

    private InventarioProductoDTO toDTO(Producto p, String rolUsuario) {
        String estado = (p.getStockActual() <= p.getStockMinimo()) ? "BAJO_STOCK" : "NORMAL";

        var precioCosto = "ADMINISTRADOR".equals(rolUsuario) ? p.getPrecioCosto() : null;

        return new InventarioProductoDTO(
                p.getId().toString(),
                p.getCodigoTecnico(),
                p.getNombre(),
                p.getStockActual(),
                p.getPrecioVenta(),
                estado,
                precioCosto
        );
    }

    private AlertaStockDTO toAlertaDTO(Producto p) {
        return new AlertaStockDTO(
                p.getId().toString(),
                p.getCodigoTecnico(),
                p.getNombre(),
                p.getCategoria() != null ? p.getCategoria().name() : null,
                p.getStockActual(),
                p.getStockMinimo()
        );
    }
}
