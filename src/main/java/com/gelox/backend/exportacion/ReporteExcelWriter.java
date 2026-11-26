package com.gelox.backend.exportacion;

import com.gelox.backend.dto.PuntoGraficaDTO;
import com.gelox.backend.dto.RentabilidadCanalDTO;
import com.gelox.backend.dto.ReporteFinancieroDTO;
import com.gelox.backend.dto.Top5ComerciantesDTO;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataFormat;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;

/** Excel del reporte financiero: una hoja por sección, con las filas 1 a 3 de encabezado (FP-15 §2.18.3). */
final class ReporteExcelWriter {

    private ReporteExcelWriter() {
    }

    static byte[] generar(DatosReporte datos) throws IOException {
        try (Workbook libro = new XSSFWorkbook(); ByteArrayOutputStream salida = new ByteArrayOutputStream()) {
            Estilos estilos = new Estilos(libro);

            if (datos.incluye(SeccionReporte.RESUMEN)) hojaResumen(libro, estilos, datos);
            if (datos.incluye(SeccionReporte.INVERSION_INGRESOS)) hojaInversionIngresos(libro, estilos, datos);
            if (datos.incluye(SeccionReporte.RENTABILIDAD_CANAL)) hojaRentabilidad(libro, estilos, datos);
            if (datos.incluye(SeccionReporte.TOP5_COMERCIANTES)) hojaTop5(libro, estilos, datos);

            libro.write(salida);
            return salida.toByteArray();
        }
    }

    private static void hojaResumen(Workbook libro, Estilos e, DatosReporte datos) {
        Sheet hoja = nuevaHoja(libro, e, datos, "Resumen");
        ReporteFinancieroDTO r = datos.reporte().resumen();
        int fila = 4;
        fila = indicador(hoja, e, fila, "Inversión total", r.totalInversion());
        fila = indicador(hoja, e, fila, "Ingresos ventanilla", r.ingresosVentanilla());
        fila = indicador(hoja, e, fila, "Ingresos rural", r.ingresosRural());
        fila = indicador(hoja, e, fila, "Ingresos comerciantes", r.ingresosComerciantes());
        fila = indicador(hoja, e, fila, "Ingresos totales", r.ingresosTotales());
        fila = indicador(hoja, e, fila, "Utilidad neta", r.utilidadNeta());
        Row margen = hoja.createRow(fila);
        margen.createCell(0).setCellValue("Margen de ganancia");
        celdaMargen(margen.createCell(1), e, r.margenGanancia());
        ajustarAncho(hoja, 2);
    }

    private static void hojaInversionIngresos(Workbook libro, Estilos e, DatosReporte datos) {
        Sheet hoja = nuevaHoja(libro, e, datos, "Inversión vs ingresos");
        encabezadoTabla(hoja, e, 4, "Período", "Inversión", "Ingresos");
        int fila = 5;
        for (PuntoGraficaDTO p : datos.reporte().grafica()) {
            Row row = hoja.createRow(fila++);
            row.createCell(0).setCellValue(p.etiqueta());
            celdaPesos(row.createCell(1), e, p.inversion());
            celdaPesos(row.createCell(2), e, p.ingresos());
        }
        ajustarAncho(hoja, 3);
    }

    private static void hojaRentabilidad(Workbook libro, Estilos e, DatosReporte datos) {
        Sheet hoja = nuevaHoja(libro, e, datos, "Rentabilidad por canal");
        encabezadoTabla(hoja, e, 4, "Canal", "Ingresos", "Costos", "Margen");
        int fila = 5;
        for (RentabilidadCanalDTO c : datos.reporte().rentabilidad().canales()) {
            Row row = hoja.createRow(fila++);
            row.createCell(0).setCellValue(FormatoReporte.canal(c.canal()));
            celdaPesos(row.createCell(1), e, c.totalIngresos());
            celdaPesos(row.createCell(2), e, c.totalCostos());
            celdaMargen(row.createCell(3), e, c.margen());
        }
        ajustarAncho(hoja, 4);
    }

    private static void hojaTop5(Workbook libro, Estilos e, DatosReporte datos) {
        Sheet hoja = nuevaHoja(libro, e, datos, "Top 5 comerciantes");
        encabezadoTabla(hoja, e, 4, "Posición", "Comerciante", "Ingreso del período");
        int fila = 5;
        for (Top5ComerciantesDTO.ComercianteIngresoDTO c : datos.top5()) {
            Row row = hoja.createRow(fila++);
            row.createCell(0).setCellValue(c.posicion());
            row.createCell(1).setCellValue(c.nombre());
            celdaPesos(row.createCell(2), e, c.totalIngreso());
        }
        ajustarAncho(hoja, 3);
    }

    /** Crea la hoja con las filas 1 a 3: nombre comercial, período y quién/cuándo generó el archivo. */
    private static Sheet nuevaHoja(Workbook libro, Estilos e, DatosReporte datos, String nombre) {
        Sheet hoja = libro.createSheet(nombre);
        Cell titulo = hoja.createRow(0).createCell(0);
        titulo.setCellValue(datos.nombreComercial());
        titulo.setCellStyle(e.titulo);
        hoja.createRow(1).createCell(0).setCellValue(
                "Reporte financiero — " + datos.reporte().fechaInicio() + " a " + datos.reporte().fechaFin());
        hoja.createRow(2).createCell(0).setCellValue(
                "Generado el " + FormatoReporte.fechaHora(datos.generadoEn()) + " por " + datos.generadoPor());
        return hoja;
    }

    private static void encabezadoTabla(Sheet hoja, Estilos e, int fila, String... titulos) {
        Row row = hoja.createRow(fila);
        for (int i = 0; i < titulos.length; i++) {
            Cell celda = row.createCell(i);
            celda.setCellValue(titulos[i]);
            celda.setCellStyle(e.encabezado);
        }
    }

    private static int indicador(Sheet hoja, Estilos e, int fila, String nombre, BigDecimal valor) {
        Row row = hoja.createRow(fila);
        row.createCell(0).setCellValue(nombre);
        celdaPesos(row.createCell(1), e, valor);
        return fila + 1;
    }

    private static void celdaPesos(Cell celda, Estilos e, BigDecimal valor) {
        celda.setCellValue((valor == null ? BigDecimal.ZERO : valor).doubleValue());
        celda.setCellStyle(e.pesos);
    }

    /** Margen con un decimal y signo %; nulo (sin inversión) se escribe «—». */
    private static void celdaMargen(Cell celda, Estilos e, BigDecimal valor) {
        if (valor == null) {
            celda.setCellValue(FormatoReporte.SIN_DATO);
            return;
        }
        celda.setCellValue(valor.doubleValue());
        celda.setCellStyle(e.margen);
    }

    private static void ajustarAncho(Sheet hoja, int columnas) {
        for (int i = 0; i < columnas; i++) hoja.autoSizeColumn(i);
    }

    private static final class Estilos {
        final CellStyle titulo;
        final CellStyle encabezado;
        final CellStyle pesos;
        final CellStyle margen;

        Estilos(Workbook libro) {
            DataFormat formato = libro.createDataFormat();
            Font negrita = libro.createFont();
            negrita.setBold(true);
            Font grande = libro.createFont();
            grande.setBold(true);
            grande.setFontHeightInPoints((short) 14);

            titulo = libro.createCellStyle();
            titulo.setFont(grande);

            encabezado = libro.createCellStyle();
            encabezado.setFont(negrita);
            encabezado.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            encabezado.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            pesos = libro.createCellStyle();
            pesos.setDataFormat(formato.getFormat("\"$\" #,##0"));

            margen = libro.createCellStyle();
            margen.setDataFormat(formato.getFormat("0.0\"%\""));
        }
    }
}
