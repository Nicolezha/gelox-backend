package com.gelox.backend.exportacion;

import com.gelox.backend.dto.PuntoGraficaDTO;
import com.gelox.backend.dto.RentabilidadCanalDTO;
import com.gelox.backend.dto.ReporteFinancieroDTO;
import com.gelox.backend.dto.Top5ComerciantesDTO;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.Image;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfPageEventHelper;
import com.lowagie.text.pdf.PdfWriter;

import java.awt.Color;
import java.io.ByteArrayOutputStream;

/**
 * PDF del reporte financiero (FP-15 §2.18.3): encabezado con logo, nombre y línea del color
 * primario; una tabla por sección; pie con número de página y «Generado por GELOX».
 */
final class ReportePdfWriter {

    private static final Color COLOR_POR_DEFECTO = new Color(0x9e, 0x20, 0x16);
    private static final Color GRIS = new Color(0x59, 0x59, 0x59);

    private ReportePdfWriter() {
    }

    static byte[] generar(DatosReporte datos) throws DocumentException {
        Color primario = color(datos.colorPrimario());
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        Document documento = new Document(PageSize.A4, 40, 40, 40, 50);
        PdfWriter escritor = PdfWriter.getInstance(documento, salida);
        escritor.setPageEvent(new PieDePagina());
        documento.open();

        encabezado(documento, datos, primario);

        if (datos.incluye(SeccionReporte.RESUMEN)) seccionResumen(documento, datos, primario);
        if (datos.incluye(SeccionReporte.INVERSION_INGRESOS)) seccionInversionIngresos(documento, datos, primario);
        if (datos.incluye(SeccionReporte.RENTABILIDAD_CANAL)) seccionRentabilidad(documento, datos, primario);
        if (datos.incluye(SeccionReporte.TOP5_COMERCIANTES)) seccionTop5(documento, datos, primario);

        documento.close();
        return salida.toByteArray();
    }

    /** Logo (si hay), nombre comercial, línea de color, período y quién/cuándo generó el archivo. */
    private static void encabezado(Document documento, DatosReporte datos, Color primario) throws DocumentException {
        PdfPTable cabecera = new PdfPTable(new float[]{1f, 5f});
        cabecera.setWidthPercentage(100);

        PdfPCell celdaLogo = sinBorde();
        Image logo = cargarLogo(datos.logo());
        if (logo != null) {
            logo.scaleToFit(60, 60);
            celdaLogo.addElement(logo);
        }
        cabecera.addCell(celdaLogo);

        PdfPCell celdaTexto = sinBorde();
        celdaTexto.addElement(new Paragraph(datos.nombreComercial(), fuente(18, Font.BOLD, primario)));
        celdaTexto.addElement(new Paragraph(
                "Reporte financiero — " + datos.reporte().fechaInicio() + " a " + datos.reporte().fechaFin(),
                fuente(11, Font.NORMAL, Color.BLACK)));
        celdaTexto.addElement(new Paragraph(
                "Generado el " + FormatoReporte.fechaHora(datos.generadoEn()) + " por " + datos.generadoPor(),
                fuente(9, Font.NORMAL, GRIS)));
        cabecera.addCell(celdaTexto);
        documento.add(cabecera);

        PdfPTable linea = new PdfPTable(1);
        linea.setWidthPercentage(100);
        linea.setSpacingBefore(6);
        linea.setSpacingAfter(12);
        PdfPCell celdaLinea = new PdfPCell(new Phrase(" ", fuente(2, Font.NORMAL, Color.WHITE)));
        celdaLinea.setBorder(Rectangle.NO_BORDER);
        celdaLinea.setBackgroundColor(primario);
        celdaLinea.setFixedHeight(3);
        linea.addCell(celdaLinea);
        documento.add(linea);
    }

    private static void seccionResumen(Document documento, DatosReporte datos, Color primario) throws DocumentException {
        titulo(documento, "Resumen", primario);
        ReporteFinancieroDTO r = datos.reporte().resumen();
        PdfPTable tabla = tabla(new float[]{3f, 2f}, primario, "Indicador", "Valor");
        fila(tabla, "Inversión total", FormatoReporte.pesos(r.totalInversion()));
        fila(tabla, "Ingresos ventanilla", FormatoReporte.pesos(r.ingresosVentanilla()));
        fila(tabla, "Ingresos rural", FormatoReporte.pesos(r.ingresosRural()));
        fila(tabla, "Ingresos comerciantes", FormatoReporte.pesos(r.ingresosComerciantes()));
        fila(tabla, "Ingresos totales", FormatoReporte.pesos(r.ingresosTotales()));
        fila(tabla, "Utilidad neta", FormatoReporte.pesos(r.utilidadNeta()));
        fila(tabla, "Margen de ganancia", FormatoReporte.margen(r.margenGanancia()));
        documento.add(tabla);
    }

    private static void seccionInversionIngresos(Document documento, DatosReporte datos, Color primario)
            throws DocumentException {
        titulo(documento, "Inversión vs ingresos", primario);
        PdfPTable tabla = tabla(new float[]{2f, 2f, 2f}, primario, "Período", "Inversión", "Ingresos");
        for (PuntoGraficaDTO p : datos.reporte().grafica()) {
            fila(tabla, p.etiqueta(), FormatoReporte.pesos(p.inversion()), FormatoReporte.pesos(p.ingresos()));
        }
        documento.add(tabla);
    }

    private static void seccionRentabilidad(Document documento, DatosReporte datos, Color primario)
            throws DocumentException {
        titulo(documento, "Rentabilidad por canal", primario);
        PdfPTable tabla = tabla(new float[]{2f, 2f, 2f, 1.5f}, primario, "Canal", "Ingresos", "Costos", "Margen");
        for (RentabilidadCanalDTO c : datos.reporte().rentabilidad().canales()) {
            fila(tabla, FormatoReporte.canal(c.canal()), FormatoReporte.pesos(c.totalIngresos()),
                    FormatoReporte.pesos(c.totalCostos()), FormatoReporte.margen(c.margen()));
        }
        documento.add(tabla);
    }

    private static void seccionTop5(Document documento, DatosReporte datos, Color primario) throws DocumentException {
        titulo(documento, "Top 5 comerciantes", primario);
        PdfPTable tabla = tabla(new float[]{1f, 3f, 2f}, primario, "Posición", "Comerciante", "Ingreso del período");
        for (Top5ComerciantesDTO.ComercianteIngresoDTO c : datos.top5()) {
            fila(tabla, String.valueOf(c.posicion()), c.nombre(), FormatoReporte.pesos(c.totalIngreso()));
        }
        documento.add(tabla);
    }

    /* ── Utilidades ── */

    private static void titulo(Document documento, String texto, Color primario) throws DocumentException {
        Paragraph p = new Paragraph(texto, fuente(13, Font.BOLD, primario));
        p.setSpacingBefore(10);
        p.setSpacingAfter(6);
        documento.add(p);
    }

    private static PdfPTable tabla(float[] anchos, Color primario, String... titulos) {
        PdfPTable tabla = new PdfPTable(anchos);
        tabla.setWidthPercentage(100);
        tabla.setHeaderRows(1);
        for (String t : titulos) {
            PdfPCell c = new PdfPCell(new Phrase(t, fuente(10, Font.BOLD, Color.WHITE)));
            c.setBackgroundColor(primario);
            c.setPadding(5);
            tabla.addCell(c);
        }
        return tabla;
    }

    private static void fila(PdfPTable tabla, String... valores) {
        for (int i = 0; i < valores.length; i++) {
            PdfPCell c = new PdfPCell(new Phrase(valores[i], fuente(10, Font.NORMAL, Color.BLACK)));
            c.setPadding(5);
            c.setBorderColor(new Color(0xdd, 0xdd, 0xdd));
            if (i > 0) c.setHorizontalAlignment(Element.ALIGN_RIGHT);
            tabla.addCell(c);
        }
    }

    private static PdfPCell sinBorde() {
        PdfPCell c = new PdfPCell();
        c.setBorder(Rectangle.NO_BORDER);
        return c;
    }

    private static Font fuente(float tamano, int estilo, Color color) {
        return FontFactory.getFont(FontFactory.HELVETICA, tamano, estilo, color);
    }

    /** Si el color guardado no es un #RRGGBB válido, se usa el rojo de GELOX. */
    private static Color color(String hex) {
        try {
            return Color.decode(hex);
        } catch (RuntimeException e) {
            return COLOR_POR_DEFECTO;
        }
    }

    /** Un logo ilegible (formato no soportado, SVG, archivo dañado) se omite: nunca rompe el PDF. */
    private static Image cargarLogo(byte[] bytes) {
        if (bytes == null || bytes.length == 0) return null;
        try {
            return Image.getInstance(bytes);
        } catch (Exception e) {
            return null;
        }
    }

    /** Pie de cada página: «Generado por GELOX» a la izquierda y «Página N» a la derecha. */
    private static final class PieDePagina extends PdfPageEventHelper {
        @Override
        public void onEndPage(PdfWriter escritor, Document documento) {
            PdfContentByte lienzo = escritor.getDirectContent();
            Font fuente = fuente(9, Font.NORMAL, GRIS);
            float y = documento.bottom() - 20;
            com.lowagie.text.pdf.ColumnText.showTextAligned(
                    lienzo, Element.ALIGN_LEFT, new Phrase("Generado por GELOX", fuente), documento.left(), y, 0);
            com.lowagie.text.pdf.ColumnText.showTextAligned(
                    lienzo, Element.ALIGN_RIGHT, new Phrase("Página " + escritor.getPageNumber(), fuente),
                    documento.right(), y, 0);
        }
    }
}
