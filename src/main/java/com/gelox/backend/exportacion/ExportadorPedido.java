package com.gelox.backend.exportacion;

import com.gelox.backend.entities.CampoPlantilla;
import com.gelox.backend.entities.CodigoHomologacion;
import com.gelox.backend.entities.ItemPedidoProveedor;
import com.gelox.backend.entities.ModoPlantilla;
import com.gelox.backend.entities.PedidoProveedor;
import com.gelox.backend.entities.PlantillaColumna;
import com.gelox.backend.entities.PlantillaExportacion;
import com.gelox.backend.entities.Producto;
import com.gelox.backend.entities.Proveedor;
import com.gelox.backend.exportacion.dto.VistaPreviaDTO;
import com.gelox.backend.plataforma.IdentidadDistribuidoraService;
import com.gelox.backend.dto.IdentidadDTO;
import com.gelox.backend.repositories.CodigoHomologacionRepository;
import com.gelox.backend.repositories.PlantillaExportacionRepository;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * RF67 — arma el archivo de un pedido con la plantilla de su proveedor (FP-15 §2.18.1):
 * <ul>
 *   <li>RELLENAR_CATALOGO: abre el archivo base, ubica la fila de encabezados y escribe la cantidad
 *       en la fila de cada código (RF21; es la lógica que antes vivía en PedidoProveedorService).</li>
 *   <li>GENERAR_FILAS: libro nuevo con las columnas configuradas y una fila por ítem.</li>
 * </ul>
 * Un producto sin código para el proveedor no se omite en silencio: el archivo sale igual y la
 * respuesta trae la lista. Sin plantilla se usa la estándar de Nutresa.
 */
@Component
@RequiredArgsConstructor
public class ExportadorPedido {

    static final String TITULO_ESTANDAR = "Formato de pedido masivo — ";
    private static final int ARCHIVO_BASE_MAX_BYTES = 10 * 1024 * 1024;

    private final PlantillaExportacionRepository plantillaRepository;
    private final CodigoHomologacionRepository homologacionRepository;
    private final IdentidadDistribuidoraService identidadService;

    @Value("classpath:templates/catalogo.xlsx")
    private Resource plantillaExcel;

    /**
     * @param contenido          bytes del .xlsx
     * @param productosSinCodigo nombres de los productos del pedido sin código para el proveedor
     * @param productosNoUbicados nombres de productos con código que no tienen fila en el archivo base
     *                           (o cuya cantidad no cupo en ella)
     */
    public record Resultado(byte[] contenido, List<String> productosSinCodigo, List<String> productosNoUbicados) {
    }

    /** Un producto del pedido con sus cantidades ya sumadas. */
    public record Linea(Producto producto, int cajas, int unidades, BigDecimal precioUnitario) {
    }

    /** Plantilla estándar de Nutresa: catalogo.xlsx, encabezados en la fila 4. Nunca se guarda. */
    public static PlantillaExportacion plantillaEstandar() {
        PlantillaExportacion p = new PlantillaExportacion();
        p.setNombre("Estándar Nutresa");
        p.setModo(ModoPlantilla.RELLENAR_CATALOGO);
        p.setFilaEncabezado(4);
        p.setEncabezadoCodigo("Producto/SKU");
        p.setEncabezadoCantidad("Cantidad");
        p.setEncabezadoUnidad("Unidad de Medida");
        return p;
    }

    /**
     * @param proveedor proveedor del pedido; nulo = pedido anterior a los proveedores: plantilla estándar
     *                  y código tomado de {@code producto.codigo_tecnico}
     */
    public Resultado generar(PedidoProveedor pedido, Proveedor proveedor) {
        PlantillaExportacion plantilla = proveedor == null ? null
                : plantillaRepository.findByProveedorId(proveedor.getId()).orElse(null);
        if (plantilla == null) plantilla = plantillaEstandar();

        List<Linea> lineas = sumarLineas(pedido);
        Map<UUID, String> codigos = codigosDe(proveedor, lineas);

        List<String> sinCodigo = new ArrayList<>();
        for (Linea l : lineas) {
            if (codigos.get(l.producto().getId()) == null) sinCodigo.add(l.producto().getNombre());
        }

        try {
            if (plantilla.getModo() == ModoPlantilla.GENERAR_FILAS) {
                return new Resultado(generarFilas(plantilla, pedido.getFecha(), lineas, codigos), sinCodigo, List.of());
            }
            return rellenarCatalogo(plantilla, lineas, codigos, sinCodigo);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "No se pudo generar el archivo del pedido: " + e.getMessage(), e);
        }
    }

    /** Primeras filas con la plantilla indicada (sin guardarla) y las líneas de ejemplo. */
    public VistaPreviaDTO previsualizar(PlantillaExportacion plantilla, List<Linea> lineas, Map<UUID, String> codigos) {
        List<String> sinCodigo = new ArrayList<>();
        for (Linea l : lineas) {
            if (codigos.get(l.producto().getId()) == null) sinCodigo.add(l.producto().getNombre());
        }
        List<String> encabezados = new ArrayList<>();
        List<List<String>> filas = new ArrayList<>();

        if (plantilla.getModo() == ModoPlantilla.GENERAR_FILAS) {
            for (PlantillaColumna c : plantilla.getColumnas()) encabezados.add(c.getEncabezado());
            for (Linea l : lineas) {
                List<String> fila = new ArrayList<>();
                for (PlantillaColumna c : plantilla.getColumnas()) {
                    Object v = valorDe(c, l, codigos.get(l.producto().getId()), LocalDate.now());
                    fila.add(v == null ? "" : String.valueOf(v));
                }
                filas.add(fila);
            }
        } else {
            encabezados.add(plantilla.getEncabezadoCodigo());
            encabezados.add("Descripción");
            if (plantilla.getEncabezadoUnidad() != null && !plantilla.getEncabezadoUnidad().isBlank()) {
                encabezados.add(plantilla.getEncabezadoUnidad());
            }
            encabezados.add(plantilla.getEncabezadoCantidad());
            for (Linea l : lineas) {
                List<String> fila = new ArrayList<>();
                fila.add(codigos.getOrDefault(l.producto().getId(), ""));
                fila.add(l.producto().getNombre());
                if (encabezados.size() == 4) fila.add(unidadDeFila(l));
                fila.add(String.valueOf(cantidadDeFila(l)));
                filas.add(fila);
            }
        }
        return new VistaPreviaDTO(encabezados, filas, sinCodigo);
    }

    /* ── RELLENAR_CATALOGO ─────────────────────────────────────────────── */

    private Resultado rellenarCatalogo(PlantillaExportacion plantilla, List<Linea> lineas,
                                       Map<UUID, String> codigos, List<String> sinCodigo) throws IOException {
        // Por código, lo que falta escribir: {cajas, unidades}. El archivo se sube al proveedor tal
        // cual: solo se llena la columna de cantidad, nunca se agregan filas.
        Map<String, int[]> pendientes = new LinkedHashMap<>();
        Map<String, Producto> productos = new HashMap<>();
        for (Linea l : lineas) {
            String codigo = codigos.get(l.producto().getId());
            if (codigo == null) continue; // sin código no se puede ubicar en el archivo
            int[] cant = pendientes.computeIfAbsent(codigo, k -> new int[2]);
            cant[0] += l.cajas();
            cant[1] += l.unidades();
            productos.put(codigo, l.producto());
        }

        try (InputStream base = abrirArchivoBase(plantilla);
             Workbook wb = new XSSFWorkbook(base);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            Sheet sheet = wb.getSheetAt(0);

            // T119 — nombre de la distribuidora en el título (A1). Solo cambia el texto de esa celda:
            // sin imágenes ni filas o columnas nuevas, porque el portal del proveedor lee el formato.
            IdentidadDTO identidad = identidadService.obtener();
            Row filaTitulo = sheet.getRow(0);
            Cell celdaTitulo = filaTitulo != null ? filaTitulo.getCell(0) : null;
            if (identidad != null && identidad.nombreComercial() != null && celdaTitulo != null
                    && celdaTitulo.getCellType() == CellType.STRING) {
                celdaTitulo.setCellValue(TITULO_ESTANDAR + identidad.nombreComercial());
            }

            int filaEncabezado = plantilla.getFilaEncabezado() - 1;
            Row encabezados = sheet.getRow(filaEncabezado);
            int colCodigo = columnaDe(encabezados, plantilla.getEncabezadoCodigo());
            int colCantidad = columnaDe(encabezados, plantilla.getEncabezadoCantidad());
            int colUnidad = columnaDe(encabezados, plantilla.getEncabezadoUnidad());
            if (colCodigo < 0 || colCantidad < 0) {
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                        "El archivo base no tiene los encabezados «" + plantilla.getEncabezadoCodigo()
                                + "» y «" + plantilla.getEncabezadoCantidad() + "» en la fila "
                                + plantilla.getFilaEncabezado());
            }

            for (int i = filaEncabezado + 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;

                String codigo = textoDe(row.getCell(colCodigo)).trim();
                if (codigo.isBlank()) continue;

                String um = colUnidad >= 0 ? textoDe(row.getCell(colUnidad)).trim().toUpperCase() : "";
                int qty = tomarCantidad(pendientes.get(codigo), productos.get(codigo),
                        um.startsWith("CAJ") || um.equals("CJ"));

                Cell celdaCantidad = row.getCell(colCantidad);
                if (celdaCantidad == null) celdaCantidad = row.createCell(colCantidad, CellType.NUMERIC);
                if (qty > 0) celdaCantidad.setCellValue(qty); else celdaCantidad.setBlank();
            }

            // Lo que no encontró fila (o cuya cantidad no cupo) se avisa en lugar de omitirse.
            List<String> noUbicados = new ArrayList<>();
            pendientes.forEach((codigo, cant) -> {
                if (cant[0] > 0 || cant[1] > 0) noUbicados.add(productos.get(codigo).getNombre());
            });

            wb.write(out);
            return new Resultado(out.toByteArray(), sinCodigo, noUbicados);
        }
    }

    /**
     * Saca de {@code cant} lo que cabe en una fila del archivo, convertido a su unidad: en una fila CJ
     * van las cajas (y las sueltas solo si completan cajas); en una fila UN va todo en unidades.
     */
    static int tomarCantidad(int[] cant, Producto producto, boolean filaEnCajas) {
        if (cant == null) return 0;
        Integer upc = producto.getUnidadesPorCaja();
        boolean conCaja = upc != null && upc > 0;
        int qty;
        if (filaEnCajas) {
            qty = cant[0];
            cant[0] = 0;
            if (conCaja && cant[1] % upc == 0) {
                qty += cant[1] / upc;
                cant[1] = 0;
            }
        } else {
            qty = cant[1];
            cant[1] = 0;
            if (conCaja) {
                qty += cant[0] * upc;
                cant[0] = 0;
            }
        }
        return qty;
    }

    private InputStream abrirArchivoBase(PlantillaExportacion plantilla) throws IOException {
        String url = plantilla.getArchivoBaseUrl();
        if (url == null || url.isBlank()) return plantillaExcel.getInputStream();
        try {
            URI uri = URI.create(url.trim());
            String esquema = uri.getScheme();
            if (!"https".equalsIgnoreCase(esquema) && !"http".equalsIgnoreCase(esquema)) {
                throw new IllegalArgumentException("esquema no permitido");
            }
            HttpClient cliente = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))
                    .followRedirects(HttpClient.Redirect.NORMAL).build();
            HttpResponse<byte[]> r = cliente.send(
                    HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(10)).GET().build(),
                    HttpResponse.BodyHandlers.ofByteArray());
            if (r.statusCode() != 200 || r.body().length > ARCHIVO_BASE_MAX_BYTES) {
                throw new IllegalStateException("respuesta " + r.statusCode());
            }
            return new java.io.ByteArrayInputStream(r.body());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "No se pudo descargar el archivo base");
        } catch (RuntimeException | IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "No se pudo abrir el archivo base de la plantilla (" + e.getMessage() + ")");
        }
    }

    private static int columnaDe(Row fila, String encabezado) {
        if (fila == null || encabezado == null || encabezado.isBlank()) return -1;
        for (Cell cell : fila) {
            if (textoDe(cell).trim().equalsIgnoreCase(encabezado.trim())) return cell.getColumnIndex();
        }
        return -1;
    }

    /* ── GENERAR_FILAS ─────────────────────────────────────────────────── */

    private byte[] generarFilas(PlantillaExportacion plantilla, LocalDate fecha, List<Linea> lineas,
                                Map<UUID, String> codigos) throws IOException {
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("Pedido");
            List<PlantillaColumna> columnas = plantilla.getColumnas();

            Row encabezados = sheet.createRow(0);
            for (int c = 0; c < columnas.size(); c++) {
                encabezados.createCell(c).setCellValue(columnas.get(c).getEncabezado());
            }

            int fila = 1;
            for (Linea l : lineas) {
                Row row = sheet.createRow(fila++);
                String codigo = codigos.get(l.producto().getId());
                for (int c = 0; c < columnas.size(); c++) {
                    Object v = valorDe(columnas.get(c), l, codigo, fecha);
                    Cell cell = row.createCell(c);
                    if (v instanceof Number n) cell.setCellValue(n.doubleValue());
                    else if (v != null) cell.setCellValue(String.valueOf(v));
                }
            }
            for (int c = 0; c < columnas.size(); c++) sheet.autoSizeColumn(c);

            wb.write(out);
            return out.toByteArray();
        }
    }

    /** Valor de una celda según el campo de la columna (FP-15 §2.18.1). */
    private static Object valorDe(PlantillaColumna columna, Linea l, String codigo, LocalDate fecha) {
        Producto p = l.producto();
        CampoPlantilla campo = columna.getCampo();
        return switch (campo) {
            case CODIGO_PROVEEDOR -> codigo == null ? "" : codigo;
            case SKU -> p.getSku() != null && !p.getSku().isBlank() ? p.getSku() : p.getCodigoTecnico();
            case NOMBRE_PRODUCTO -> p.getNombre();
            case UNIDAD -> unidadDeFila(l);
            case CANTIDAD -> cantidadDeFila(l);
            case CANTIDAD_CAJAS -> l.cajas();
            case CANTIDAD_UNIDADES -> unidadesTotales(l);
            case PRECIO_UNITARIO -> l.precioUnitario();
            case SUBTOTAL -> l.precioUnitario() == null ? BigDecimal.ZERO
                    : l.precioUnitario().multiply(BigDecimal.valueOf(unidadesTotales(l)));
            case FECHA_PEDIDO -> fecha == null ? "" : fecha.toString();
            case TEXTO_FIJO -> columna.getValorFijo() == null ? "" : columna.getValorFijo();
        };
    }

    private static int unidadesPorCaja(Producto p) {
        return p.getUnidadesPorCaja() != null && p.getUnidadesPorCaja() > 0 ? p.getUnidadesPorCaja() : 0;
    }

    /** Todo en unidades; si el producto no tiene caja, las cajas se cuentan tal cual. */
    private static int unidadesTotales(Linea l) {
        int upc = unidadesPorCaja(l.producto());
        return upc > 0 ? l.cajas() * upc + l.unidades() : l.cajas() + l.unidades();
    }

    /** La fila va en cajas si el producto tiene caja y las sueltas completan cajas; si no, en unidades. */
    private static boolean filaEnCajas(Linea l) {
        int upc = unidadesPorCaja(l.producto());
        return upc > 0 && l.unidades() % upc == 0;
    }

    private static String unidadDeFila(Linea l) {
        return filaEnCajas(l) ? "CJ" : "UN";
    }

    private static int cantidadDeFila(Linea l) {
        return filaEnCajas(l) ? l.cajas() + l.unidades() / unidadesPorCaja(l.producto()) : unidadesTotales(l);
    }

    /* ── Datos del pedido ──────────────────────────────────────────────── */

    private static List<Linea> sumarLineas(PedidoProveedor pedido) {
        Map<UUID, int[]> cantidades = new LinkedHashMap<>();
        Map<UUID, Producto> productos = new HashMap<>();
        Map<UUID, BigDecimal> precios = new HashMap<>();
        for (ItemPedidoProveedor item : pedido.getItems()) {
            Producto p = item.getProducto();
            UUID id = p.getId();
            int[] c = cantidades.computeIfAbsent(id, k -> new int[2]);
            c[0] += item.getCantidadCajas() == null ? 0 : item.getCantidadCajas();
            c[1] += item.getCantidadUnidades() == null ? 0 : item.getCantidadUnidades();
            productos.put(id, p);
            precios.put(id, item.getPrecioUnitario());
        }
        List<Linea> lineas = new ArrayList<>();
        cantidades.forEach((id, c) -> lineas.add(new Linea(productos.get(id), c[0], c[1], precios.get(id))));
        lineas.sort(Comparator.comparing(l -> String.valueOf(l.producto().getNombre()), String.CASE_INSENSITIVE_ORDER));
        return lineas;
    }

    /** productoId → código del proveedor; ausente si no tiene. Sin proveedor se usa codigo_tecnico. */
    private Map<UUID, String> codigosDe(Proveedor proveedor, List<Linea> lineas) {
        Map<UUID, String> codigos = new HashMap<>();
        if (proveedor == null) {
            for (Linea l : lineas) {
                String raw = l.producto().getCodigoTecnico();
                if (raw != null && !raw.isBlank()) codigos.put(l.producto().getId(), raw.trim());
            }
            return codigos;
        }
        for (CodigoHomologacion h : homologacionRepository.listarPorProveedor(proveedor.getId())) {
            if (h.getCodigo() != null && !h.getCodigo().isBlank()) {
                codigos.put(h.getProducto().getId(), h.getCodigo().trim());
            }
        }
        return codigos;
    }

    private static String textoDe(Cell cell) {
        if (cell == null) return "";
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue();
            case NUMERIC -> String.valueOf((long) cell.getNumericCellValue());
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            default -> "";
        };
    }
}
