package com.gelox.backend.exportacion;

import com.gelox.backend.dto.PeriodoFiltroDTO;
import com.gelox.backend.dto.ReporteFinancieroCompletoDTO;
import com.gelox.backend.dto.TipoPeriodo;
import com.gelox.backend.dto.Top5ComerciantesDTO;
import com.gelox.backend.entities.Distribuidora;
import com.gelox.backend.entities.Usuario;
import com.gelox.backend.repositories.DistribuidoraRepository;
import com.gelox.backend.security.RequiereRol;
import com.gelox.backend.services.DashboardService;
import com.gelox.backend.services.ReporteFinancieroService;
import com.gelox.backend.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;

/**
 * RF68 — Reportes financieros exportables (FP-15 §2.18.3). Usa los mismos datos de la pantalla
 * Reportes ({@link ReporteFinancieroService#generarReporteCompleto}), sin cálculos nuevos.
 */
@Service
@RequiredArgsConstructor
public class ReporteExportService {

    private static final ZoneId ZONA_BOGOTA = ZoneId.of("America/Bogota");
    private static final int LOGO_MAX_BYTES = 2 * 1024 * 1024;

    private final ReporteFinancieroService reporteFinancieroService;
    private final DashboardService dashboardService;
    private final DistribuidoraRepository distribuidoraRepository;

    /** Archivo listo para descargar: bytes y nombre sin extensión de ruta. */
    public record ArchivoExportado(byte[] contenido, String nombre) {
    }

    @RequiereRol("ADMINISTRADOR")
    @Transactional(readOnly = true)
    public ArchivoExportado exportarExcel(TipoPeriodo tipo, LocalDate fechaInicio, LocalDate fechaFin,
                                          Set<SeccionReporte> secciones, Usuario usuario) {
        DatosReporte datos = reunirDatos(tipo, fechaInicio, fechaFin, secciones, usuario, false);
        try {
            return new ArchivoExportado(ReporteExcelWriter.generar(datos), nombreArchivo(datos, "xlsx"));
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "No se pudo generar el Excel del reporte", e);
        }
    }

    @RequiereRol("ADMINISTRADOR")
    @Transactional(readOnly = true)
    public ArchivoExportado exportarPdf(TipoPeriodo tipo, LocalDate fechaInicio, LocalDate fechaFin,
                                        Set<SeccionReporte> secciones, Usuario usuario) {
        DatosReporte datos = reunirDatos(tipo, fechaInicio, fechaFin, secciones, usuario, true);
        try {
            return new ArchivoExportado(ReportePdfWriter.generar(datos), nombreArchivo(datos, "pdf"));
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "No se pudo generar el PDF del reporte", e);
        }
    }

    private DatosReporte reunirDatos(TipoPeriodo tipo, LocalDate fechaInicio, LocalDate fechaFin,
                                     Set<SeccionReporte> secciones, Usuario usuario, boolean conLogo) {
        if (secciones == null || secciones.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Marca al menos una sección para exportar");
        }
        if (tipo == TipoPeriodo.RANGO && (fechaInicio == null || fechaFin == null)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "RANGO requiere fechaInicio y fechaFin");
        }
        if (fechaInicio != null && fechaFin != null && fechaInicio.isAfter(fechaFin)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "La fecha de inicio no puede ser posterior a la fecha fin");
        }

        Distribuidora distribuidora = distribuidoraRepository.findById(TenantContext.requerido())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Distribuidora no encontrada"));

        // Período sin datos: los servicios devuelven ceros, así que el archivo sale con ceros.
        ReporteFinancieroCompletoDTO reporte =
                reporteFinancieroService.generarReporteCompleto(tipo, fechaInicio, fechaFin);

        List<Top5ComerciantesDTO.ComercianteIngresoDTO> top5 = List.of();
        if (secciones.contains(SeccionReporte.TOP5_COMERCIANTES)) {
            PeriodoFiltroDTO periodo = new PeriodoFiltroDTO(reporte.fechaInicio(), reporte.fechaFin());
            top5 = dashboardService.obtenerTop5Comerciantes(periodo.fechaInicio(), periodo.fechaFin()).comerciantes();
        }

        return new DatosReporte(
                distribuidora.getNombreComercial(),
                distribuidora.getColorPrimario(),
                conLogo ? descargarLogo(distribuidora.getLogoUrl()) : null,
                usuario != null ? usuario.getNombre() : "—",
                LocalDateTime.now(ZONA_BOGOTA),
                reporte,
                top5,
                secciones);
    }

    /** reporte-financiero-{subdominio}-{desde}-{hasta}.{extension} */
    private String nombreArchivo(DatosReporte datos, String extension) {
        String subdominio = distribuidoraRepository.findById(TenantContext.requerido())
                .map(Distribuidora::getSubdominio).orElse("gelox");
        return "reporte-financiero-" + subdominio + "-" + datos.reporte().fechaInicio()
                + "-" + datos.reporte().fechaFin() + "." + extension;
    }

    /**
     * Descarga el logo de la distribuidora. Si no hay URL, no responde a tiempo, supera el tamaño
     * máximo o falla de cualquier forma, devuelve null: el reporte sale sin logo, nunca con error 500.
     */
    private byte[] descargarLogo(String url) {
        if (url == null || url.isBlank()) return null;
        try {
            URI uri = URI.create(url.trim());
            String esquema = uri.getScheme();
            if (!"https".equalsIgnoreCase(esquema) && !"http".equalsIgnoreCase(esquema)) return null;

            HttpClient cliente = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(3))
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .build();
            HttpResponse<byte[]> respuesta = cliente.send(
                    HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(5)).GET().build(),
                    HttpResponse.BodyHandlers.ofByteArray());
            if (respuesta.statusCode() != 200 || respuesta.body().length > LOGO_MAX_BYTES) return null;
            return respuesta.body();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } catch (Exception e) {
            return null;
        }
    }
}
