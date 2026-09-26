package com.gelox.backend.voz.handlers;

import com.gelox.backend.dto.CierreCajaResponseDTO;
import com.gelox.backend.dto.ReporteDiarioDTO;
import com.gelox.backend.security.RequiereRol;
import com.gelox.backend.services.CierreCajaService;
import com.gelox.backend.services.ReporteDiarioService;
import com.gelox.backend.voz.VozResultado;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * RF51 — "cierra el día". Lo invoca {@link ConsultaFinancieraHandler}; narra los
 * ingresos del día por canal ({@link ReporteDiarioService}) y el estado de la
 * conciliación ({@link CierreCajaService#obtenerPorFecha}). Solo lee: el dinero
 * físico lo sigue registrando el frontend (CierreCaja.jsx).
 */
@Component
@RequiredArgsConstructor
public class CierreDiaResumen {

    private static final DateTimeFormatter FORMATO_FECHA_LARGA =
            DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy", Locale.of("es", "CO"));

    private final ReporteDiarioService reporteDiarioService;
    private final CierreCajaService cierreCajaService;

    /** Público para que {@link ConsultaFinancieraHandler#ejecutar} pueda enrutarlo. */
    public record Payload(LocalDate hoy) {}

    /** {@code hoy} ya viene resuelto en America/Bogota (VozContexto#hoy). */
    @RequiereRol("ADMINISTRADOR")
    public VozResultado generarResumen(LocalDate hoy) {
        ReporteDiarioDTO reporte = reporteDiarioService.generarReporteDiario(hoy);
        NumberFormat moneda = NumberFormat.getCurrencyInstance(Locale.of("es", "CO"));

        Map<String, Object> datos = new LinkedHashMap<>();
        datos.put("tipo", "cierre"); // VistaRespuesta.jsx decide la vista con esto
        datos.put("fecha", hoy); // ISO cruda, para comparar contra GET /api/reportes/diario
        datos.put("periodo", "Hoy · " + hoy.format(FORMATO_FECHA_LARGA));
        // ReporteDiarioDTO trae los campos en singular; el frontend los lee en plural.
        datos.put("ingresosVentanilla", reporte.ingresoVentanilla());
        datos.put("ingresosRural", reporte.ingresoRural());
        datos.put("ingresosComerciantes", reporte.ingresoComerciantes());
        datos.put("totalIngresos", reporte.totalIngresos());
        datos.put("totalTransacciones", reporte.totalTransacciones());
        datos.put("variacionVentanilla", reporte.variacionVentanilla());
        datos.put("variacionRural", reporte.variacionRural());
        datos.put("variacionComerciantes", reporte.variacionComerciantes());

        String textoConciliacion;
        try {
            CierreCajaResponseDTO cierre = cierreCajaService.obtenerPorFecha(hoy);
            Map<String, Object> conciliacion = new LinkedHashMap<>();
            conciliacion.put("montoCalculadoTotal", cierre.montoCalculadoTotal());
            conciliacion.put("montoFisicoTotal", cierre.montoFisicoTotal());
            // Con signo: negativo = faltante. El .abs() es solo para el texto narrado.
            conciliacion.put("diferenciaTotal", cierre.diferenciaTotal());
            conciliacion.put("tieneDiferencias", cierre.tieneDiferencias());
            datos.put("conciliacion", conciliacion);
            textoConciliacion = cierre.tieneDiferencias()
                    ? "con una diferencia de " + moneda.format(cierre.diferenciaTotal().abs()) + " en caja"
                    : "sin diferencias en caja";
        } catch (ResponseStatusException e) {
            // Solo el "no hay cierre" se convierte en mensaje; cualquier otro error sube tal cual.
            if (e.getStatusCode().value() != 404) throw e;
            // La clave "conciliacion" queda ausente a propósito: VistaCierreDia.jsx
            // hace `!conciliacion` para pintar el estado "Pendiente".
            textoConciliacion = "la conciliación está pendiente: aún no se ha registrado el dinero físico del día";
        }

        String texto = "El cierre de hoy suma " + moneda.format(reporte.totalIngresos())
                + " en " + reporte.totalTransacciones() + " transacciones, " + textoConciliacion + ".";

        return new VozResultado(true, texto, datos, new Payload(hoy));
    }
}
