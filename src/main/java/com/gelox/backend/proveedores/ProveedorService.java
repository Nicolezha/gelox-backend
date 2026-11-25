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

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

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
        return proveedorRepository.buscar(texto, activo).stream().map(ProveedorDTO::de).toList();
    }

    @RequiereRol({"ADMINISTRADOR", "ENCARGADO_INVENTARIO"})
    @Transactional(readOnly = true)
    public ProveedorDTO obtener(UUID id) {
        return ProveedorDTO.de(buscar(id));
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
        return ProveedorDTO.de(proveedorRepository.save(p));
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
        return ProveedorDTO.de(proveedorRepository.save(p));
    }

    /** Activa (o, con {@code activo=false}, inactiva) al proveedor. */
    @Auditable(accion = "PROVEEDOR_ACTIVAR", entidad = "Proveedor")
    @RequiereRol({"ADMINISTRADOR", "ENCARGADO_INVENTARIO"})
    public ProveedorDTO activar(UUID id, boolean activo) {
        Proveedor p = buscar(id);
        p.setActivo(activo);
        return ProveedorDTO.de(proveedorRepository.save(p));
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
        if (req.tiempoEntregaDias() != null) p.setTiempoEntregaDias(req.tiempoEntregaDias());
        if (req.diasHabiles() != null)       p.setDiasHabiles(req.diasHabiles());
        if (req.montoMinimoPedido() != null) p.setMontoMinimoPedido(req.montoMinimoPedido());
        if (p.getMontoMinimoPedido() == null) p.setMontoMinimoPedido(BigDecimal.ZERO);
    }

    private static String limpiar(String texto) {
        return texto == null || texto.isBlank() ? null : texto.trim();
    }

    private static ResponseStatusException yaExisteNit() {
        return new ResponseStatusException(HttpStatus.CONFLICT, "Ya existe un proveedor con ese NIT");
    }
}
