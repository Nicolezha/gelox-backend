package com.gelox.backend.catalogo;

import com.gelox.backend.catalogo.dto.CatalogoProductoDTO;
import com.gelox.backend.catalogo.dto.CrearProductoRequest;
import com.gelox.backend.catalogo.dto.EditarProductoRequest;
import com.gelox.backend.catalogo.dto.FactorConversionDTO;
import com.gelox.backend.catalogo.dto.FactorConversionRequest;
import com.gelox.backend.catalogo.dto.PagedResponse;
import com.gelox.backend.entities.Categoria;
import com.gelox.backend.entities.FactorConversion;
import com.gelox.backend.entities.Producto;
import com.gelox.backend.entities.Proveedor;
import com.gelox.backend.entities.RolUsuario;
import com.gelox.backend.entities.UnidadMedida;
import com.gelox.backend.entities.Usuario;
import com.gelox.backend.gobierno.Auditable;
import com.gelox.backend.gobierno.CuotaService;
import com.gelox.backend.gobierno.TipoCuota;
import com.gelox.backend.repositories.CategoriaRepository;
import com.gelox.backend.repositories.FactorConversionRepository;
import com.gelox.backend.repositories.ProveedorRepository;
import com.gelox.backend.repositories.UnidadMedidaRepository;
import com.gelox.backend.security.RequiereRol;
import com.gelox.backend.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class CatalogoService {

    /** Código de la unidad base por defecto (V10 asigna UN a todo producto existente). */
    private static final String UNIDAD_BASE_POR_DEFECTO = "UN";
    /** CJ → unidad base sale de unidades_por_caja, no de factor_conversion. */
    private static final String UNIDAD_CAJA = "CJ";
    private static final int TAMANO_MAXIMO_PAGINA = 500;
    /** Marcador para los filtros por id apagados (la consulta no recibe parámetros nulos). */
    private static final UUID SIN_FILTRO = new UUID(0, 0);
    private static final Set<String> FILTROS_STOCK = Set.of("", "BAJO", "MEDIO", "SIN");

    private final CatalogoRepository catalogoRepository;
    private final CuotaService cuotaService;   // RF70 — cuotas del plan
    private final ProveedorRepository proveedorRepository;          // RF63
    private final CategoriaRepository categoriaRepository;          // RF64
    private final UnidadMedidaRepository unidadMedidaRepository;    // RF64
    private final FactorConversionRepository factorRepository;      // RF64

    // ─────────────────────────── RF18 ────────────────────────────

    @RequiereRol({"ADMINISTRADOR", "ENCARGADO_INVENTARIO", "ENCARGADO_VENTAS"})
    @Transactional(readOnly = true)
    public PagedResponse<CatalogoProductoDTO> listarProductos(
            String categoria, UUID categoriaId, String q, UUID proveedorId, String stock,
            int page, int size, Usuario usuario) {

        // Limitar tamaño máximo
        int safeSize = Math.min(size, TAMANO_MAXIMO_PAGINA);
        Pageable pageable = PageRequest.of(page, safeSize);

        boolean porCategoria = categoriaId != null || (categoria != null && !categoria.isBlank());
        UUID idCategoria = porCategoria
                ? resolverCategoria(categoriaId, categoria, true).getId()   // filtrar por una inactiva es válido
                : SIN_FILTRO;
        if (proveedorId != null) {
            validarProveedorDelTenant(proveedorId);                         // filtrar por uno inactivo es válido
        }

        Page<Producto> resultPage = catalogoRepository.buscar(
                !porCategoria, idCategoria,
                proveedorId == null, proveedorId != null ? proveedorId : SIN_FILTRO,
                q == null ? "" : q.trim(),
                filtroStock(stock),
                pageable);

        boolean esAdmin = usuario.getRol() == RolUsuario.ADMINISTRADOR;

        // Una sola consulta de factores para toda la página
        List<UUID> ids = resultPage.getContent().stream().map(Producto::getId).toList();
        Map<UUID, List<FactorConversion>> factores = ids.isEmpty() ? Map.of()
                : factorRepository.findByProductoIdIn(ids).stream()
                        .collect(Collectors.groupingBy(f -> f.getProducto().getId()));

        return new PagedResponse<>(
                resultPage.getContent().stream()
                        .map(p -> toDTO(p, esAdmin, factores.getOrDefault(p.getId(), List.of())))
                        .toList(),
                resultPage.getNumber(),
                resultPage.getSize(),
                resultPage.getTotalElements(),
                resultPage.getTotalPages()
        );
    }

    // ─────────────────────────── RF19 ────────────────────────────

    @Auditable(accion = "PRODUCTO_CREAR", entidad = "Producto")
    @RequiereRol({"ADMINISTRADOR", "ENCARGADO_INVENTARIO"})
    public CatalogoProductoDTO crearProducto(CrearProductoRequest req, Usuario usuario) {

        // RF64 — categoría de la tabla (por id o por nombre)
        Categoria categoria = resolverCategoria(req.getCategoriaId(), req.getCategoria(), false);

        // RF19 — precio de venta obligatorio y > 0 (también aplica al endpoint multipart)
        validarPrecioVenta(req.getPrecioVenta(), true);
        // Utilidades reales — sin costo no se puede calcular la ganancia neta
        if (req.getPrecioCosto() == null || req.getPrecioCosto().compareTo(BigDecimal.ZERO) <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "El precio de costo es obligatorio y debe ser mayor a 0");
        }

        validarUnidadesPorCaja(req.getUnidadesPorCaja());

        // Verificar código duplicado entre productos activos
        if (catalogoRepository.existsByCodigoTecnicoAndActivoTrue(req.getCodigoTecnico())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "El código ya está registrado en el catálogo");
        }

        // RF63 — sku único por distribuidora; sin sku se usa el código técnico (como V10)
        String sku = limpiar(req.getSku());
        if (sku == null) sku = req.getCodigoTecnico();
        if (catalogoRepository.existsBySku(sku)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "El SKU ya está registrado en el catálogo");
        }

        Proveedor proveedor = resolverProveedor(req.getProveedorId());
        UnidadMedida unidadBase = req.getUnidadBaseId() != null
                ? buscarUnidad(req.getUnidadBaseId())
                : unidadMedidaRepository.findByCodigoIgnoreCase(UNIDAD_BASE_POR_DEFECTO).orElse(null);

        // RF70 — con la cuota de productos del plan alcanzada no se guarda nada
        cuotaService.validar(TipoCuota.PRODUCTOS);

        Producto producto = new Producto();
        producto.setCodigoTecnico(req.getCodigoTecnico());
        producto.setSku(sku);
        producto.setMarca(limpiar(req.getMarca()));
        producto.setProveedor(proveedor);
        producto.setUnidadBase(unidadBase);
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
        producto.setUnidadesPorCaja(req.getUnidadesPorCaja());
        producto.setPrecioComerciente(req.getPrecioComerciente());
        producto.setActivo(true);

        Producto guardado = catalogoRepository.save(producto);

        List<FactorConversion> factores = req.getFactores() != null
                ? reemplazarFactores(guardado, req.getFactores())
                : List.of();

        boolean esAdmin = usuario.getRol() == RolUsuario.ADMINISTRADOR;
        return toDTO(guardado, esAdmin, factores);
    }

    // ─────────────────────────── RF20 — PUT ──────────────────────

    @RequiereRol({"ADMINISTRADOR", "ENCARGADO_INVENTARIO"})
    public CatalogoProductoDTO editarProducto(UUID id, EditarProductoRequest req, Usuario usuario) {

        Producto producto = catalogoRepository.findByIdAndActivoTrue(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Producto no encontrado"));

        validarUnidadesPorCaja(req.getUnidadesPorCaja());

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

        // RF63 — sku repetido en otro producto → 409
        String sku = limpiar(req.getSku());
        if (sku != null) {
            if (catalogoRepository.existsBySkuAndIdNot(sku, id)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "El SKU ya está en uso por otro producto");
            }
            producto.setSku(sku);
        }
        if (req.getMarca() != null)         producto.setMarca(limpiar(req.getMarca()));
        if (req.getProveedorId() != null)   producto.setProveedor(resolverProveedor(req.getProveedorId()));
        if (req.getUnidadBaseId() != null)  producto.setUnidadBase(buscarUnidad(req.getUnidadBaseId()));

        if (req.getNombre() != null)        producto.setNombre(req.getNombre());
        if (req.getCategoriaId() != null || req.getCategoria() != null) {
            producto.setCategoria(resolverCategoria(req.getCategoriaId(), req.getCategoria(), false));
        }
        validarPrecioVenta(req.getPrecioVenta(), false);   // RF20
        if (req.getPrecioCosto() != null && req.getPrecioCosto().compareTo(BigDecimal.ZERO) <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "El precio de costo debe ser mayor a 0");
        }
        if (req.getPrecioVenta() != null)   producto.setPrecioVenta(req.getPrecioVenta());
        if (req.getPrecioCosto() != null)   producto.setPrecioCosto(req.getPrecioCosto());
        if (req.getDescripcion() != null)   producto.setDescripcion(req.getDescripcion());
        if (req.getStockMinimo() != null)   producto.setStockMinimo(req.getStockMinimo());
        if (req.getStockMedio()  != null)   producto.setStockMedio(req.getStockMedio());
        if (req.getImagenUrl() != null)     producto.setImagenUrl(req.getImagenUrl());
        if (req.getUnidadMedida()      != null)  producto.setUnidadMedida(req.getUnidadMedida());
        if (req.getUnidadesPorCaja()   != null)  producto.setUnidadesPorCaja(req.getUnidadesPorCaja());
        if (req.getPrecioComerciente() != null)  producto.setPrecioComerciente(req.getPrecioComerciente());

        Producto actualizado = catalogoRepository.save(producto);

        // factores: null = sin cambios; lista (aun vacía) = reemplaza los del producto
        List<FactorConversion> factores = req.getFactores() != null
                ? reemplazarFactores(actualizado, req.getFactores())
                : factorRepository.findByProductoId(actualizado.getId());

        boolean esAdmin = usuario.getRol() == RolUsuario.ADMINISTRADOR;
        return toDTO(actualizado, esAdmin, factores);
    }

    // ─────────────────────────── RF20 — ACTIVAR ──────────────────

    @RequiereRol({"ADMINISTRADOR", "ENCARGADO_INVENTARIO"})
    public CatalogoProductoDTO activarProducto(UUID id, Usuario usuario) {
        Producto producto = catalogoRepository.findById(id)
                .filter(TenantContext::pertenece)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Producto no encontrado"));
        // RF70 — reactivar un producto también ocupa un cupo del plan
        if (!Boolean.TRUE.equals(producto.getActivo())) {
            cuotaService.validar(TipoCuota.PRODUCTOS);
        }
        producto.setActivo(true);
        Producto actualizado = catalogoRepository.save(producto);
        boolean esAdmin = usuario.getRol() == RolUsuario.ADMINISTRADOR;
        return toDTO(actualizado, esAdmin, factorRepository.findByProductoId(actualizado.getId()));
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

    /** RF19/RF20 — el precio de venta debe ser > 0. En edición solo se valida si se envía. */
    private void validarPrecioVenta(BigDecimal precioVenta, boolean obligatorio) {
        if (precioVenta == null) {
            if (obligatorio) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "El precio de venta es obligatorio");
            }
            return;
        }
        if (precioVenta.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "El precio de venta debe ser mayor a 0");
        }
    }

    /** RF64 — 0 quita las unidades por caja (ver Producto); un negativo no es válido. */
    private void validarUnidadesPorCaja(Integer unidadesPorCaja) {
        if (unidadesPorCaja != null && unidadesPorCaja < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Las unidades por caja no pueden ser negativas");
        }
    }

    /**
     * RF64 — categoría por id (404 si no es de esta distribuidora) o por nombre (400 si no existe).
     * Para crear o editar un producto debe estar activa.
     */
    private Categoria resolverCategoria(UUID id, String nombre, boolean permitirInactiva) {
        Categoria categoria;
        if (id != null) {
            categoria = categoriaRepository.findById(id)
                    .filter(TenantContext::pertenece)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Categoría no encontrada"));
        } else if (nombre != null && !nombre.isBlank()) {
            categoria = categoriaRepository.findByNombreIgnoreCase(nombre.trim())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "Categoría inválida: " + nombre.trim()));
        } else {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La categoría es obligatoria");
        }
        if (!Boolean.TRUE.equals(categoria.getActivo()) && !permitirInactiva) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La categoría está inactiva");
        }
        return categoria;
    }

    /** Valor del parámetro stock del listado: vacío (todos), BAJO, MEDIO o SIN. */
    private static String filtroStock(String stock) {
        String valor = stock == null ? "" : stock.trim().toUpperCase(Locale.ROOT);
        if (!FILTROS_STOCK.contains(valor)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Filtro de stock inválido: usa bajo, medio o sin");
        }
        return valor;
    }

    private Proveedor validarProveedorDelTenant(UUID id) {
        return proveedorRepository.findById(id)
                .filter(TenantContext::pertenece)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Proveedor no encontrado"));
    }

    /** RF63 — el proveedor debe ser de esta distribuidora (404 si no) y estar activo (400 si no). */
    private Proveedor resolverProveedor(UUID id) {
        if (id == null) return null;
        Proveedor proveedor = validarProveedorDelTenant(id);
        if (!Boolean.TRUE.equals(proveedor.getActivo())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El proveedor está inactivo");
        }
        return proveedor;
    }

    private UnidadMedida buscarUnidad(UUID id) {
        return unidadMedidaRepository.findById(id)
                .filter(TenantContext::pertenece)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Unidad de medida no encontrada"));
    }

    /**
     * RF64 — reemplaza los factores del producto: cada uno dice cuántas unidades base vale
     * 1 unidad de origen. CJ no se acepta porque CJ → UN sale de unidades_por_caja.
     */
    private List<FactorConversion> reemplazarFactores(Producto producto, List<FactorConversionRequest> pedidos) {
        UnidadMedida base = producto.getUnidadBase();
        if (!pedidos.isEmpty() && base == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Defina la unidad base del producto antes de sus factores de conversión");
        }

        List<FactorConversion> nuevos = new ArrayList<>();
        Set<UUID> vistas = new HashSet<>();
        for (FactorConversionRequest pedido : pedidos) {
            // en multipart no corre @Valid sobre los factores
            if (pedido.unidadOrigenId() == null || pedido.factor() == null
                    || pedido.factor().compareTo(BigDecimal.ZERO) <= 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Cada factor necesita unidadOrigenId y un factor mayor a 0");
            }
            UnidadMedida origen = buscarUnidad(pedido.unidadOrigenId());
            if (origen.getId().equals(base.getId())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "La unidad base no necesita factor de conversión");
            }
            if (UNIDAD_CAJA.equalsIgnoreCase(origen.getCodigo())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "CJ se convierte con unidadesPorCaja, no con un factor");
            }
            if (!vistas.add(origen.getId())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Unidad repetida en los factores: " + origen.getCodigo());
            }
            FactorConversion f = new FactorConversion();
            f.setProducto(producto);
            f.setUnidadOrigen(origen);
            f.setUnidadDestino(base);
            f.setFactor(pedido.factor());
            nuevos.add(f);
        }

        factorRepository.borrarPorProducto(producto.getId());
        return nuevos.isEmpty() ? List.of() : factorRepository.saveAll(nuevos);
    }

    private static String limpiar(String texto) {
        return texto == null || texto.isBlank() ? null : texto.trim();
    }

    private CatalogoProductoDTO toDTO(Producto p, boolean esAdmin, List<FactorConversion> factores) {
        Proveedor proveedor = p.getProveedor();
        UnidadMedida base = p.getUnidadBase();
        Categoria categoria = p.getCategoria();
        return new CatalogoProductoDTO(
                p.getId().toString(),
                p.getCodigoTecnico(),
                p.getNombre(),
                categoria != null ? categoria.getNombre() : null,
                p.getPrecioVenta(),
                esAdmin ? p.getPrecioCosto() : null,   // solo ADMINISTRADOR ve precioCosto
                p.getDescripcion(),
                p.getStockMinimo(),
                p.getStockMedio(),
                p.getStockActual(),
                p.getImagenUrl(),
                p.getUnidadMedida(),
                p.getUnidadesPorCaja(),
                p.getPrecioComerciente(),
                p.getSku(),
                p.getMarca(),
                proveedor != null ? proveedor.getId() : null,
                proveedor != null ? proveedor.getNombre() : null,
                categoria != null ? categoria.getId() : null,
                base != null ? base.getId() : null,
                base != null ? base.getCodigo() : null,
                factores.stream()
                        .map(f -> new FactorConversionDTO(
                                f.getUnidadOrigen().getId(), f.getUnidadOrigen().getCodigo(), f.getFactor()))
                        .toList()
        );
    }
}
