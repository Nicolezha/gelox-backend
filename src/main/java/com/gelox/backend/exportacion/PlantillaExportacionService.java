package com.gelox.backend.exportacion;

import com.gelox.backend.entities.CodigoHomologacion;
import com.gelox.backend.entities.ModoPlantilla;
import com.gelox.backend.entities.PlantillaColumna;
import com.gelox.backend.entities.PlantillaExportacion;
import com.gelox.backend.entities.Producto;
import com.gelox.backend.entities.Proveedor;
import com.gelox.backend.exportacion.dto.ColumnaDTO;
import com.gelox.backend.exportacion.dto.HomologacionDTO;
import com.gelox.backend.exportacion.dto.HomologacionRequest;
import com.gelox.backend.exportacion.dto.PlantillaDTO;
import com.gelox.backend.exportacion.dto.PlantillaRequest;
import com.gelox.backend.exportacion.dto.VistaPreviaDTO;
import com.gelox.backend.gobierno.Auditable;
import com.gelox.backend.gobierno.AuditoriaDetalle;
import com.gelox.backend.repositories.CodigoHomologacionRepository;
import com.gelox.backend.repositories.PlantillaExportacionRepository;
import com.gelox.backend.repositories.ProductoRepository;
import com.gelox.backend.repositories.ProveedorRepository;
import com.gelox.backend.security.RequiereRol;
import com.gelox.backend.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * RF67 — plantilla de exportación de pedidos por proveedor y códigos de homologación (FP-15 §2.18.1).
 * Un proveedor de otra distribuidora responde 404. Roles: ADMINISTRADOR y ENCARGADO_INVENTARIO.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class PlantillaExportacionService {

    private static final int VISTA_PREVIA_FILAS = 3;

    private final ProveedorRepository proveedorRepository;
    private final PlantillaExportacionRepository plantillaRepository;
    private final CodigoHomologacionRepository homologacionRepository;
    private final ProductoRepository productoRepository;
    private final ExportadorPedido exportadorPedido;

    @RequiereRol({"ADMINISTRADOR", "ENCARGADO_INVENTARIO"})
    @Transactional(readOnly = true)
    public PlantillaDTO obtener(UUID proveedorId) {
        Proveedor proveedor = buscarProveedor(proveedorId);
        return aDTO(proveedor, plantillaRepository.findByProveedorId(proveedorId).orElse(null));
    }

    /** Crea o reemplaza la plantilla del proveedor y, si vienen, sus códigos de homologación. */
    @Auditable(accion = "PLANTILLA_EXPORTACION_CAMBIAR", entidad = "Proveedor")
    @RequiereRol({"ADMINISTRADOR", "ENCARGADO_INVENTARIO"})
    public PlantillaDTO guardar(UUID proveedorId, PlantillaRequest req) {
        Proveedor proveedor = buscarProveedor(proveedorId);
        validar(req);

        PlantillaExportacion plantilla = plantillaRepository.findByProveedorId(proveedorId).orElse(null);
        boolean nueva = plantilla == null;
        ModoPlantilla modoAnterior = nueva ? null : plantilla.getModo();
        if (nueva) {
            plantilla = new PlantillaExportacion();
            plantilla.setProveedor(proveedor);
        }
        plantilla.setNombre("Pedido " + proveedor.getNombre());
        aplicar(plantilla, req);

        // UNIQUE (plantilla_id, orden): se borran las columnas anteriores antes de insertar las nuevas.
        if (!nueva && !plantilla.getColumnas().isEmpty()) {
            plantilla.getColumnas().clear();
            plantilla = plantillaRepository.saveAndFlush(plantilla);
        }
        if (req.modo() == ModoPlantilla.GENERAR_FILAS) {
            int orden = 1;
            for (ColumnaDTO c : req.columnas()) {
                PlantillaColumna columna = new PlantillaColumna();
                columna.setPlantilla(plantilla);
                columna.setOrden(orden++);
                columna.setCampo(c.campo());
                columna.setEncabezado(c.encabezado().trim());
                columna.setValorFijo(c.campo() == com.gelox.backend.entities.CampoPlantilla.TEXTO_FIJO
                        ? c.valorFijo().trim() : null);
                plantilla.getColumnas().add(columna);
            }
        }
        plantilla = plantillaRepository.save(plantilla);

        int cambiosCodigos = req.homologaciones() == null ? 0 : guardarCodigos(proveedor, req.homologaciones());

        Map<String, Object> detalle = new LinkedHashMap<>();
        detalle.put("proveedor", proveedor.getNombre());
        detalle.put("accion", nueva ? "CREAR" : "EDITAR");
        detalle.put("modo", plantilla.getModo().name());
        if (modoAnterior != null && modoAnterior != plantilla.getModo()) detalle.put("modoAnterior", modoAnterior.name());
        detalle.put("columnas", plantilla.getColumnas().size());
        detalle.put("homologacionesActualizadas", cambiosCodigos);
        AuditoriaDetalle.poner(detalle);
        AuditoriaDetalle.entidadId(proveedorId);

        return aDTO(proveedor, plantilla);
    }

    /** Solo los códigos de homologación (productoId → código); un código vacío quita el del producto. */
    @Auditable(accion = "PLANTILLA_EXPORTACION_CAMBIAR", entidad = "Proveedor")
    @RequiereRol({"ADMINISTRADOR", "ENCARGADO_INVENTARIO"})
    public PlantillaDTO guardarHomologaciones(UUID proveedorId, List<HomologacionRequest> homologaciones) {
        Proveedor proveedor = buscarProveedor(proveedorId);
        if (homologaciones == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Envía la lista de homologaciones");
        }
        int cambios = guardarCodigos(proveedor, homologaciones);

        Map<String, Object> detalle = new LinkedHashMap<>();
        detalle.put("proveedor", proveedor.getNombre());
        detalle.put("accion", "HOMOLOGAR");
        detalle.put("homologacionesActualizadas", cambios);
        AuditoriaDetalle.poner(detalle);
        AuditoriaDetalle.entidadId(proveedorId);

        return aDTO(proveedor, plantillaRepository.findByProveedorId(proveedorId).orElse(null));
    }

    /**
     * Primeras filas del archivo con la plantilla recibida (sin guardarla) y productos reales del
     * proveedor con cantidades de ejemplo (1, 2 y 3 cajas).
     */
    @RequiereRol({"ADMINISTRADOR", "ENCARGADO_INVENTARIO"})
    @Transactional(readOnly = true)
    public VistaPreviaDTO vistaPrevia(UUID proveedorId, PlantillaRequest req) {
        Proveedor proveedor = buscarProveedor(proveedorId);
        validar(req);

        PlantillaExportacion borrador = new PlantillaExportacion();
        borrador.setProveedor(proveedor);
        aplicar(borrador, req);
        if (req.modo() == ModoPlantilla.GENERAR_FILAS) {
            int orden = 1;
            for (ColumnaDTO c : req.columnas()) {
                PlantillaColumna columna = new PlantillaColumna();
                columna.setOrden(orden++);
                columna.setCampo(c.campo());
                columna.setEncabezado(c.encabezado().trim());
                columna.setValorFijo(c.valorFijo());
                borrador.getColumnas().add(columna);
            }
        }

        // Los códigos en pantalla (aún sin guardar) mandan sobre los guardados.
        Map<UUID, String> codigos = new HashMap<>();
        for (CodigoHomologacion h : homologacionRepository.listarPorProveedor(proveedorId)) {
            codigos.put(h.getProducto().getId(), h.getCodigo());
        }
        if (req.homologaciones() != null) {
            for (HomologacionRequest h : req.homologaciones()) {
                if (h.productoId() == null) continue;
                if (h.codigo() == null || h.codigo().isBlank()) codigos.remove(h.productoId());
                else codigos.put(h.productoId(), h.codigo().trim());
            }
        }

        List<ExportadorPedido.Linea> lineas = new ArrayList<>();
        List<Producto> productos = productoRepository.findByProveedorIdAndActivoTrueOrderByNombreAsc(proveedorId);
        for (int i = 0; i < Math.min(VISTA_PREVIA_FILAS, productos.size()); i++) {
            Producto p = productos.get(i);
            lineas.add(new ExportadorPedido.Linea(p, i + 1, 0, p.getPrecioCosto() == null ? BigDecimal.ZERO : p.getPrecioCosto()));
        }
        return exportadorPedido.previsualizar(borrador, lineas, codigos);
    }

    /* ── Validación y conversión ───────────────────────────────────────── */

    /** findById no pasa por el filtro de tenant: el de otra distribuidora responde 404. */
    private Proveedor buscarProveedor(UUID id) {
        return proveedorRepository.findById(id)
                .filter(TenantContext::pertenece)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Proveedor no encontrado"));
    }

    private static void validar(PlantillaRequest req) {
        if (req == null || req.modo() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Indica el modo de la plantilla");
        }
        if (req.modo() == ModoPlantilla.GENERAR_FILAS) {
            if (req.columnas() == null || req.columnas().isEmpty()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Agrega al menos una columna a la plantilla");
            }
            for (ColumnaDTO c : req.columnas()) {
                if (c == null || c.campo() == null) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cada columna necesita un campo conocido");
                }
                if (c.encabezado() == null || c.encabezado().isBlank()) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cada columna necesita un encabezado");
                }
                if (c.campo() == com.gelox.backend.entities.CampoPlantilla.TEXTO_FIJO
                        && (c.valorFijo() == null || c.valorFijo().isBlank())) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Una columna de texto fijo necesita su valor");
                }
            }
        } else {
            if (req.filaEncabezado() == null || req.filaEncabezado() < 1) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La fila de encabezados debe ser 1 o mayor");
            }
            if (vacio(req.encabezadoCodigo()) || vacio(req.encabezadoCantidad())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Indica los encabezados de código y de cantidad del archivo base");
            }
        }
    }

    private static void aplicar(PlantillaExportacion p, PlantillaRequest req) {
        p.setModo(req.modo());
        boolean catalogo = req.modo() == ModoPlantilla.RELLENAR_CATALOGO;
        p.setArchivoBaseUrl(catalogo && !vacio(req.archivoBaseUrl()) ? req.archivoBaseUrl().trim() : null);
        p.setFilaEncabezado(catalogo ? req.filaEncabezado() : 1);
        p.setEncabezadoCodigo(catalogo ? req.encabezadoCodigo().trim() : null);
        p.setEncabezadoCantidad(catalogo ? req.encabezadoCantidad().trim() : null);
        p.setEncabezadoUnidad(catalogo && !vacio(req.encabezadoUnidad()) ? req.encabezadoUnidad().trim() : null);
    }

    /**
     * Guarda, cambia o quita códigos. Un código repetido dentro del mismo proveedor → 409
     * (UNIQUE proveedor_id + codigo). Devuelve cuántos productos cambiaron.
     */
    private int guardarCodigos(Proveedor proveedor, List<HomologacionRequest> pedidos) {
        Set<String> vistos = new HashSet<>();
        Set<UUID> productosVistos = new HashSet<>();
        for (HomologacionRequest h : pedidos) {
            if (h == null || h.productoId() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cada código necesita su producto");
            }
            if (!productosVistos.add(h.productoId())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Un producto aparece dos veces");
            }
            String codigo = limpio(h.codigo());
            if (codigo != null && !vistos.add(codigo.toLowerCase())) {
                throw conflicto(codigo);
            }
        }

        int cambios = 0;
        List<CodigoHomologacion> existentes = homologacionRepository.listarPorProveedor(proveedor.getId());
        Map<UUID, CodigoHomologacion> porProducto = new HashMap<>();
        Map<String, CodigoHomologacion> porCodigo = new HashMap<>();
        for (CodigoHomologacion e : existentes) {
            porProducto.put(e.getProducto().getId(), e);
            porCodigo.put(e.getCodigo().toLowerCase(), e);
        }

        // Primero se quitan y se cambian los códigos; así un código puede pasar de un producto a otro en la misma petición.
        List<HomologacionRequest> aGuardar = new ArrayList<>();
        for (HomologacionRequest h : pedidos) {
            String codigo = limpio(h.codigo());
            CodigoHomologacion actual = porProducto.get(h.productoId());
            if (codigo == null) {
                if (actual != null) {
                    homologacionRepository.delete(actual);
                    porCodigo.remove(actual.getCodigo().toLowerCase());
                    cambios++;
                }
            } else if (actual == null || !actual.getCodigo().equals(codigo)) {
                aGuardar.add(h);
            }
        }
        homologacionRepository.flush();

        for (HomologacionRequest h : aGuardar) {
            String codigo = limpio(h.codigo());
            CodigoHomologacion otro = porCodigo.get(codigo.toLowerCase());
            if (otro != null && !otro.getProducto().getId().equals(h.productoId())
                    && pedidos.stream().noneMatch(x -> x.productoId().equals(otro.getProducto().getId()))) {
                throw conflicto(codigo);
            }
            Producto producto = productoRepository.findById(h.productoId())
                    .filter(TenantContext::pertenece)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Producto no encontrado"));
            CodigoHomologacion fila = porProducto.get(h.productoId());
            if (fila == null) {
                fila = new CodigoHomologacion();
                fila.setProveedor(proveedor);
                fila.setProducto(producto);
            }
            fila.setCodigo(codigo);
            homologacionRepository.saveAndFlush(fila);
            cambios++;
        }
        return cambios;
    }

    private static ResponseStatusException conflicto(String codigo) {
        return new ResponseStatusException(HttpStatus.CONFLICT,
                "El código «" + codigo + "» ya está asignado a otro producto de este proveedor");
    }

    private PlantillaDTO aDTO(Proveedor proveedor, PlantillaExportacion guardada) {
        boolean personalizada = guardada != null;
        PlantillaExportacion p = personalizada ? guardada : ExportadorPedido.plantillaEstandar();

        List<ColumnaDTO> columnas = p.getColumnas().stream()
                .map(c -> new ColumnaDTO(c.getOrden(), c.getCampo(), c.getEncabezado(), c.getValorFijo()))
                .toList();

        Map<UUID, String> codigos = new HashMap<>();
        for (CodigoHomologacion h : homologacionRepository.listarPorProveedor(proveedor.getId())) {
            codigos.put(h.getProducto().getId(), h.getCodigo());
        }
        List<HomologacionDTO> homologaciones = productoRepository
                .findByProveedorIdAndActivoTrueOrderByNombreAsc(proveedor.getId()).stream()
                .map(prod -> new HomologacionDTO(prod.getId(),
                        prod.getSku() != null && !prod.getSku().isBlank() ? prod.getSku() : prod.getCodigoTecnico(),
                        prod.getNombre(), codigos.getOrDefault(prod.getId(), "")))
                .toList();

        return new PlantillaDTO(proveedor.getId(), personalizada, p.getModo(), p.getArchivoBaseUrl(),
                p.getFilaEncabezado(), p.getEncabezadoCodigo(), p.getEncabezadoCantidad(), p.getEncabezadoUnidad(),
                columnas, homologaciones, personalizada ? p.getUpdatedAt() : null);
    }

    private static boolean vacio(String s) {
        return s == null || s.isBlank();
    }

    private static String limpio(String s) {
        return vacio(s) ? null : s.trim();
    }
}
