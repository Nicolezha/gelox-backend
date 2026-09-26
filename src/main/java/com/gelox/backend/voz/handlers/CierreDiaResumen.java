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
        datos.put("fecha", hoy);
        datos.put("ingresoVentanilla", reporte.ingresoVentanilla());
        datos.put("ingresoRural", reporte.ingresoRural());
        datos.put("ingresoComerciantes", reporte.ingresoComerciantes());
        datos.put("totalIngresos", reporte.totalIngresos());
        datos.put("totalTransacciones", reporte.totalTransacciones());

        Map<String, Object> conciliacion = new LinkedHashMap<>();
        String textoConciliacion;
        try {
            CierreCajaResponseDTO cierre = cierreCajaService.obtenerPorFecha(hoy);
            conciliacion.put("registrado", true);
            conciliacion.put("tieneDiferencias", cierre.tieneDiferencias());
            // Con signo: negativo = faltante. El .abs() es solo para el texto narrado.
            conciliacion.put("diferenciaTotal", cierre.diferenciaTotal());
            textoConciliacion = cierre.tieneDiferencias()
                    ? "diferencia de " + moneda.format(cierre.diferenciaTotal().abs())
                    : "sin diferencias";
        } catch (ResponseStatusException e) {
            // Solo el "no hay cierre" se convierte en mensaje; cualquier otro error sube tal cual.
            if (e.getStatusCode().value() != 404) throw e;
            conciliacion.put("registrado", false);
            textoConciliacion = "conciliación pendiente: aún no se ha registrado el dinero físico del día";
        }
        datos.put("conciliacion", conciliacion);

        String texto = "Cierre de hoy: ventanilla " + moneda.format(reporte.ingresoVentanilla())
                + ", rural " + moneda.format(reporte.ingresoRural())
                + ", comerciantes " + moneda.format(reporte.ingresoComerciantes())
                + "; total " + moneda.format(reporte.totalIngresos())
                + " en " + reporte.totalTransacciones() + " transacciones. Conciliación: "
                + textoConciliacion + ".";

        return new VozResultado(true, texto, datos, new Payload(hoy));
    }
}
