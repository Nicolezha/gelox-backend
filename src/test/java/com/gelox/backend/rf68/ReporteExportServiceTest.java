package com.gelox.backend.rf68;

import com.gelox.backend.dto.PuntoGraficaDTO;
import com.gelox.backend.dto.RentabilidadCanalDTO;
import com.gelox.backend.dto.ReporteFinancieroCompletoDTO;
import com.gelox.backend.dto.ReporteFinancieroDTO;
import com.gelox.backend.dto.ReporteRentabilidadDTO;
import com.gelox.backend.dto.TipoPeriodo;
import com.gelox.backend.dto.Top5ComerciantesDTO;
import com.gelox.backend.entities.Distribuidora;
import com.gelox.backend.entities.Usuario;
import com.gelox.backend.exportacion.ReporteExportService;
import com.gelox.backend.exportacion.SeccionReporte;
import com.gelox.backend.repositories.DistribuidoraRepository;
import com.gelox.backend.services.DashboardService;
import com.gelox.backend.services.ReporteFinancieroService;
import com.gelox.backend.tenant.TenantContext;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * RF68 — Reportes financieros exportables en Excel y PDF (FP-15 §2.18.3): una hoja por sección,
 * encabezado de la distribuidora, nombre de archivo reporte-financiero-{subdominio}-{desde}-{hasta},
 * período sin datos con ceros, logo que no descarga sin error y validaciones con 400.
 */
@ExtendWith(MockitoExtension.class)
class ReporteExportServiceTest {

    private static final UUID TENANT = UUID.fromString("00000000-0000-0000-0000-0000000000aa");
    private static final LocalDate INICIO = LocalDate.of(2026, 11, 1);
    private static final LocalDate FIN = LocalDate.of(2026, 11, 24);

    @Mock private ReporteFinancieroService reporteFinancieroService;
    @Mock private DashboardService dashboardService;
    @Mock private DistribuidoraRepository distribuidoraRepository;

    @InjectMocks private ReporteExportService service;

    private final Usuario gerente = Usuario.builder().nombre("Carmen Gerente").build();

    @BeforeEach
    void preparar() {
        TenantContext.set(TENANT);
    }

    @AfterEach
    void limpiar() {
        TenantContext.clear();
    }

    private void distribuidora(String logoUrl) {
        Distribuidora d = Distribuidora.builder()
                .nombreComercial("Mágico Sabor")
                .subdominio("magicosabor")
                .colorPrimario("#9e2016")
                .logoUrl(logoUrl)
                .build();
        when(distribuidoraRepository.findById(TENANT)).thenReturn(Optional.of(d));
    }

    private ReporteFinancieroCompletoDTO reporte(BigDecimal cero) {
        BigDecimal margen = cero.signum() == 0 ? null : new BigDecimal("25.5");
        return new ReporteFinancieroCompletoDTO(
                TipoPeriodo.RANGO, INICIO, FIN,
                new ReporteFinancieroDTO(cero, cero, cero, cero, cero, cero, margen),
                List.of(new PuntoGraficaDTO("Sem 1", cero, cero)),
                new ReporteRentabilidadDTO(List.of(
                        new RentabilidadCanalDTO("VENTANILLA", cero, cero, BigDecimal.ZERO),
                        new RentabilidadCanalDTO("RURAL", cero, cero, BigDecimal.ZERO),
                        new RentabilidadCanalDTO("COMERCIANTES", cero, cero, BigDecimal.ZERO))));
    }

    private void datos(BigDecimal valor) {
        when(reporteFinancieroService.generarReporteCompleto(TipoPeriodo.RANGO, INICIO, FIN))
                .thenReturn(reporte(valor));
    }

    private static Workbook abrir(byte[] bytes) throws Exception {
        return new XSSFWorkbook(new ByteArrayInputStream(bytes));
    }

    @Test
    @DisplayName("CP-68-01: el Excel trae una hoja por sección marcada y nada más")
    void excelConUnaHojaPorSeccion() throws Exception {
        distribuidora(null);
        datos(new BigDecimal("1500000"));
        when(dashboardService.obtenerTop5Comerciantes(INICIO, FIN)).thenReturn(new Top5ComerciantesDTO(List.of(
                new Top5ComerciantesDTO.ComercianteIngresoDTO(UUID.randomUUID(), "Don Pepe", new BigDecimal("900000"), 1))));

        ReporteExportService.ArchivoExportado archivo = service.exportarExcel(
                TipoPeriodo.RANGO, INICIO, FIN, EnumSet.allOf(SeccionReporte.class), gerente);

        try (Workbook libro = abrir(archivo.contenido())) {
            assertThat(libro.getNumberOfSheets()).isEqualTo(4);
            assertThat(libro.getSheetAt(0).getSheetName()).isEqualTo("Resumen");
            assertThat(libro.getSheet("Inversión vs ingresos")).isNotNull();
            assertThat(libro.getSheet("Rentabilidad por canal")).isNotNull();
            Sheet top5 = libro.getSheet("Top 5 comerciantes");
            assertThat(top5.getRow(5).getCell(1).getStringCellValue()).isEqualTo("Don Pepe");
        }
    }

    @Test
    @DisplayName("CP-68-02: filas 1 a 3 con distribuidora, período y quién genera; nombre con subdominio y fechas")
    void encabezadoYNombreDeArchivo() throws Exception {
        distribuidora(null);
        datos(new BigDecimal("1500000"));

        ReporteExportService.ArchivoExportado archivo = service.exportarExcel(
                TipoPeriodo.RANGO, INICIO, FIN, Set.of(SeccionReporte.RESUMEN), gerente);

        assertThat(archivo.nombre()).isEqualTo("reporte-financiero-magicosabor-2026-11-01-2026-11-24.xlsx");
        try (Workbook libro = abrir(archivo.contenido())) {
            Sheet hoja = libro.getSheetAt(0);
            assertThat(hoja.getRow(0).getCell(0).getStringCellValue()).isEqualTo("Mágico Sabor");
            assertThat(hoja.getRow(1).getCell(0).getStringCellValue()).contains("2026-11-01").contains("2026-11-24");
            assertThat(hoja.getRow(2).getCell(0).getStringCellValue()).contains("Carmen Gerente");
        }
        verify(dashboardService, never()).obtenerTop5Comerciantes(any(), any());
    }

    @Test
    @DisplayName("CP-68-03: período sin datos exporta ceros y el margen nulo se escribe «—»")
    void periodoSinDatosExportaCeros() throws Exception {
        distribuidora(null);
        datos(BigDecimal.ZERO);

        ReporteExportService.ArchivoExportado archivo = service.exportarExcel(
                TipoPeriodo.RANGO, INICIO, FIN, Set.of(SeccionReporte.RESUMEN), gerente);

        try (Workbook libro = abrir(archivo.contenido())) {
            Sheet hoja = libro.getSheetAt(0);
            assertThat(hoja.getRow(4).getCell(1).getNumericCellValue()).isZero();
            assertThat(hoja.getRow(10).getCell(1).getStringCellValue()).isEqualTo("—");
        }
    }

    @Test
    @DisplayName("CP-68-04: el PDF es un PDF válido con nombre .pdf")
    void pdfValido() {
        distribuidora(null);
        datos(new BigDecimal("1500000"));

        ReporteExportService.ArchivoExportado archivo = service.exportarPdf(
                TipoPeriodo.RANGO, INICIO, FIN, EnumSet.complementOf(EnumSet.of(SeccionReporte.TOP5_COMERCIANTES)), gerente);

        assertThat(new String(archivo.contenido(), 0, 5, StandardCharsets.ISO_8859_1)).isEqualTo("%PDF-");
        assertThat(archivo.nombre()).endsWith("-2026-11-24.pdf");
    }

    @Test
    @DisplayName("CP-68-05: un logo que no descarga no rompe el PDF (sin logo, nunca 500)")
    void logoQueNoDescargaSaleSinLogo() {
        distribuidora("http://localhost:1/logo-que-no-existe.png");
        datos(new BigDecimal("1500000"));

        ReporteExportService.ArchivoExportado archivo = service.exportarPdf(
                TipoPeriodo.RANGO, INICIO, FIN, Set.of(SeccionReporte.RESUMEN), gerente);

        assertThat(archivo.contenido()).isNotEmpty();
    }

    @Test
    @DisplayName("CP-68-06: sin secciones marcadas responde 400")
    void sinSeccionesEs400() {
        assertThatThrownBy(() -> service.exportarExcel(TipoPeriodo.RANGO, INICIO, FIN, Set.of(), gerente))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
        verifyNoInteractions(reporteFinancieroService);
    }

    @Test
    @DisplayName("CP-68-07: fecha inicial posterior a la final responde 400")
    void inicioMayorQueFinEs400() {
        assertThatThrownBy(() -> service.exportarPdf(
                TipoPeriodo.RANGO, FIN, INICIO, Set.of(SeccionReporte.RESUMEN), gerente))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
        verifyNoInteractions(reporteFinancieroService);
    }

    @Test
    @DisplayName("CP-68-08: RANGO sin fechas responde 400")
    void rangoSinFechasEs400() {
        assertThatThrownBy(() -> service.exportarExcel(
                TipoPeriodo.RANGO, null, null, Set.of(SeccionReporte.RESUMEN), gerente))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }
}
