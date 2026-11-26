package com.gelox.backend.proveedores;

import com.gelox.backend.entities.Proveedor;
import com.gelox.backend.gobierno.Auditable;
import com.gelox.backend.proveedores.dto.ProveedorDTO;
import com.gelox.backend.proveedores.dto.ProveedorRequest;
import com.gelox.backend.repositories.ProveedorRepository;
import com.gelox.backend.security.RequiereRol;
import com.gelox.backend.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * RF63 — proveedores de la distribuidora. Uno de otra distribuidora responde 404.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class ProveedorService {

    private final ProveedorRepository proveedorRepository;

    @RequiereRol({"ADMINISTRADOR", "ENCARGADO_INVENTARIO"})
    @Transactional(readOnly = true)
    public List<ProveedorDTO> listar(String q, Boolean activo) {
        String texto = q == null ? "" : q.trim();
        return aDTO(proveedorRepository.buscar(texto, activo));
    }

    @RequiereRol({"ADMINISTRADOR", "ENCARGADO_INVENTARIO"})
    @Transactional(readOnly = true)
    public ProveedorDTO obtener(UUID id) {
        return aDTO(buscar(id));
    }

    @Auditable(accion = "PROVEEDOR_CREAR", entidad = "Proveedor")
    @RequiereRol({"ADMINISTRADOR", "ENCARGADO_INVENTARIO"})
    public ProveedorDTO crear(ProveedorRequest req) {
        String nit = limpiar(req.nit());
        if (nit != null && proveedorRepository.existsByNit(nit)) {
            throw yaExisteNit();
        }
        Proveedor p = new Proveedor();
        aplicar(p, req, nit);
        return aDTO(proveedorRepository.save(p));
    }

    @Auditable(accion = "PROVEEDOR_EDITAR", entidad = "Proveedor")
    @RequiereRol({"ADMINISTRADOR", "ENCARGADO_INVENTARIO"})
    public ProveedorDTO editar(UUID id, ProveedorRequest req) {
        Proveedor p = buscar(id);
        String nit = limpiar(req.nit());
        if (nit != null && proveedorRepository.existsByNitAndIdNot(nit, id)) {
            throw yaExisteNit();
        }
        aplicar(p, req, nit);
        return aDTO(proveedorRepository.save(p));
    }

    /** Activa (o, con {@code activo=false}, inactiva) al proveedor. */
    @Auditable(accion = "PROVEEDOR_ACTIVAR", entidad = "Proveedor")
    @RequiereRol({"ADMINISTRADOR", "ENCARGADO_INVENTARIO"})
    public ProveedorDTO activar(UUID id, boolean activo) {
        Proveedor p = buscar(id);
        p.setActivo(activo);
        return aDTO(proveedorRepository.save(p));
    }

    /** findById no pasa por el filtro de tenant: el de otra distribuidora responde 404. */
    private Proveedor buscar(UUID id) {
        return proveedorRepository.findById(id)
                .filter(TenantContext::pertenece)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Proveedor no encontrado"));
    }

    private void aplicar(Proveedor p, ProveedorRequest req, String nit) {
        p.setNombre(req.nombre().trim());
        p.setNit(nit);
        p.setContactoNombre(limpiar(req.contactoNombre()));
        p.setTelefono(limpiar(req.telefono()));
        p.setCorreo(limpiar(req.correo()));
        p.setTiempoEntregaDias(req.tiempoEntregaDias());
        if (req.diasHabiles() != null) p.setDiasHabiles(req.diasHabiles());
        p.setMontoMinimoPedido(req.montoMinimoPedido());
    }

    private ProveedorDTO aDTO(Proveedor proveedor) {
        return aDTO(List.of(proveedor)).get(0);
    }

    /** Una consulta de productos y otra de plantillas para toda la lista. */
    private List<ProveedorDTO> aDTO(List<Proveedor> proveedores) {
        if (proveedores.isEmpty()) return List.of();
        List<UUID> ids = proveedores.stream().map(Proveedor::getId).toList();
        Map<UUID, Long> productos = proveedorRepository.contarProductosActivos(ids).stream()
                .collect(Collectors.toMap(f -> (UUID) f[0], f -> (Long) f[1]));
        Map<UUID, String> plantillas = proveedorRepository.nombresDePlantillas(ids).stream()
                .collect(Collectors.toMap(f -> (UUID) f[0], f -> (String) f[1]));
        return proveedores.stream()
                .map(p -> ProveedorDTO.de(p, productos.getOrDefault(p.getId(), 0L), plantillas.get(p.getId())))
                .toList();
    }

    private static String limpiar(String texto) {
        return texto == null || texto.isBlank() ? null : texto.trim();
    }

    private static ResponseStatusException yaExisteNit() {
        return new ResponseStatusException(HttpStatus.CONFLICT, "Ya existe un proveedor con ese NIT");
    }
}
