package com.gelox.backend.voz.handlers;

import com.gelox.backend.entities.EstadoRecomendacion;
import com.gelox.backend.entities.TipoIntencionVoz;
import com.gelox.backend.entities.Usuario;
import com.gelox.backend.recomendacion.RecomendacionCompraService;
import com.gelox.backend.recomendacion.dto.RecomendacionDTO;
import com.gelox.backend.voz.FechaVoz;
import com.gelox.backend.voz.IntencionHandler;
import com.gelox.backend.voz.NormalizadorVoz;
import com.gelox.backend.voz.VozPendienteStore;
import com.gelox.backend.voz.VozContexto;
import com.gelox.backend.voz.VozPendiente;
import com.gelox.backend.voz.VozResultado;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Manejador de navegación por voz ("ve a la pestaña de ventas", "abre inventarios").
 * Resuelve la ruta frontend destino a partir de los términos dictados y retorna la acción
 * para que el cliente web navegue automáticamente.
 */
@Component
@RequiredArgsConstructor
public class NavegacionHandler implements IntencionHandler {

    private static final ZoneId ZONA_BOGOTA = ZoneId.of("America/Bogota");
    private static final String RUTA_RECOMENDACIONES = "/inventarios/recomendaciones";

    /** "muéstrame la recomendación completa", "ábreme la recomendación en pantalla", "ver la recomendación del 15". */
    private static final Pattern MOSTRAR = Pattern.compile(
            "\\b(muestra\\w*|ensena\\w*|abre\\w*|ver|veamos|llevame|lleva\\w*|pon\\w*|despliega\\w*|dirigete|ir|ve|vamos)\\b.*\\brecomendacion"
                    + "|\\brecomendacion\\w*\\b.*\\b(completa|en pantalla|pantalla|entera|detalle)\\b");
    /** "cuántas recomendaciones…", "aprueba…", "genera el pedido…": son otra cosa. */
    private static final Pattern NO_ES_MOSTRAR = Pattern.compile(
            "\\b(cuant\\w*|aprueb\\w*|aprobar|rechaz\\w*|cambi\\w*|modific\\w*|genera\\w*|recomiendas|recomiendame)\\b");
    private static final Pattern RECOMENDACION = Pattern.compile("\\brecomendacion");
    private static final Pattern ULTIMA = Pattern.compile("\\b(ultima|mas reciente)\\b");

    private final RecomendacionCompraService recomendacionService;
    private final VozPendienteStore pendienteStore;

    /**
     * "déjame modificar (la recomendación)", "quiero editarla", "modifícala": abre la recomendación
     * para cambiarla en pantalla. Anclado al final: "quiero cambiar el mango biche a 3 cajas" es una decisión.
     */
    private static final Pattern EDITAR = Pattern.compile(
            "^(?:(?:no|si|bueno|mejor|espera)\\s+)*(?:(?:dejame|quiero|voy a|vamos a|necesito|prefiero|mejor)\\s+)?"
                    + "(?:modificar|editar|cambiar|revisar|ajustar)(?:la|lo|las|los)?"
                    + "(?:\\s+(?:la recomendacion|esa recomendacion|esa|eso|algo|algunas cosas|unas cosas|un poco|primero|en pantalla))?$"
                    + "|^(?:modificala|editala|cambiala|modifiquemosla|editemosla)$");

    public static boolean esEditarRecomendacion(String normalizado) {
        return EDITAR.matcher(normalizado).find();
    }

    /** Lo usa VozService para mandar aquí "muéstrame la recomendación…" aunque se clasifique como consulta. */
    public static boolean esMostrarRecomendacion(String normalizado) {
        return MOSTRAR.matcher(normalizado).find() && !NO_ES_MOSTRAR.matcher(normalizado).find();
    }

    @Override
    public TipoIntencionVoz tipo() {
        return TipoIntencionVoz.NAVEGACION;
    }

    @Override
    public boolean requiereConfirmacion() {
        return false;
    }

    @Override
    public VozResultado interpretar(VozContexto ctx) {
        String texto = NormalizadorVoz.normalizar(ctx.texto());
        String ruta = "/ventas/pedidos-ventanilla";
        String nombre = "Pedidos Ventanilla";

        if (esEditarRecomendacion(texto)) {
            VozResultado r = mostrarRecomendacion(texto, ctx);
            return new VozResultado(true, r.datos().containsKey("recomendacionId")
                    ? "Te abro la recomendación para que la modifiques." : r.textoRespuesta(), r.datos(), null);
        }
        if (RECOMENDACION.matcher(texto).find()) {
            return mostrarRecomendacion(texto, ctx);
        }
        if (texto.contains("inventario")) {
            ruta = "/inventarios/gestion";
            nombre = "Gestión de Inventarios";
        } else if (texto.contains("rural")) {
            ruta = "/ventas/pedidos-rurales";
            nombre = "Pedidos Rurales";
        } else if (texto.contains("comerciante") || texto.contains("cliente")) {
            ruta = "/ventas/comerciantes";
            nombre = "Mis Comerciantes";
        } else if (texto.contains("catalogo")) {
            ruta = "/inventarios/catalogo";
            nombre = "Catálogo de Productos";
        } else if (texto.contains("merma")) {
            ruta = "/inventarios/merma";
            nombre = "Registro de Merma";
        } else if (texto.contains("entrada")) {
            ruta = "/inventarios/entrada";
            nombre = "Registro de Entrada";
        } else if (texto.contains("generar pedido") || texto.contains("nuevo pedido")) {
            ruta = "/inventarios/generar-pedido";
            nombre = "Generar Pedido al Proveedor";
        } else if (texto.contains("pedido")) {
            ruta = "/inventarios/reporte-pedido";
            nombre = "Reporte de Pedidos";
        } else if (texto.contains("cierre") || texto.contains("caja")) {
            ruta = "/cierres-caja";
            nombre = "Historial de Cierres de Caja";
        } else if (texto.contains("usuario")) {
            ruta = "/usuarios";
            nombre = "Gestión de Usuarios";
        } else if (texto.contains("reporte") || texto.contains("finanza") || texto.contains("indicador")) {
            ruta = "/reportes";
            nombre = "Reportes e Indicadores";
        } else if (texto.contains("dashboard") || texto.contains("inicio")) {
            ruta = "/dashboard/gerente";
            nombre = "Dashboard Principal";
        } else if (texto.contains("ajuste")) {
            ruta = "/ajustes";
            nombre = "Ajustes del Sistema";
        } else if (texto.contains("ventanilla") || texto.contains("venta")) {
            ruta = "/ventas/pedidos-ventanilla";
            nombre = "Pedidos Ventanilla";
        }

        return new VozResultado(
                true,
                "Navegando a " + nombre + "...",
                Map.of("ruta", ruta, "pantalla", nombre),
                null
        );
    }

    /**
     * La recomendación a mostrar: la del día dicho, "la última" que se hizo, la última de la que se
     * habló por voz o, si no, la más reciente.
     */
    private VozResultado mostrarRecomendacion(String texto, VozContexto ctx) {
        LocalDate hoy = ctx.hoy() != null ? ctx.hoy() : LocalDate.now(ZONA_BOGOTA);
        List<EstadoRecomendacion> todas = List.of(EstadoRecomendacion.values());
        Optional<LocalDate> fecha = FechaVoz.de(texto, hoy);
        Optional<RecomendacionDTO> rec;
        if (fecha.isPresent()) {
            List<RecomendacionDTO> delDia = recomendacionService.recomendacionesDelDia(fecha.get(), todas);
            rec = delDia.isEmpty() ? Optional.empty() : Optional.of(delDia.get(delDia.size() - 1));
        } else if (ULTIMA.matcher(texto).find()) {
            rec = recomendacionService.ultimaRecomendacion(todas);
        } else {
            Optional<UUID> recordada = ctx.usuario() == null ? Optional.empty()
                    : pendienteStore.ultimaRecomendacion(ctx.usuario().getId());
            rec = recordada.map(id -> new RecomendacionDTO(id, null, null, null, null, null, null, null, null,
                            null, null, null, List.of()))
                    .or(() -> recomendacionService.ultimaRecomendacion(todas));
        }

        Map<String, Object> datos = new LinkedHashMap<>();
        datos.put("pantalla", "Recomendación de Compras");
        if (rec.isEmpty()) {
            datos.put("ruta", RUTA_RECOMENDACIONES);
            String msg = fecha.map(f -> "No hay recomendaciones " + SelectorRecomendacion.deFecha(f, hoy)
                    + ". Abro la pantalla de recomendaciones.").orElse("Abro la pantalla de recomendaciones.");
            return new VozResultado(true, msg, datos, null);
        }
        datos.put("ruta", RUTA_RECOMENDACIONES + "?id=" + rec.get().id());
        datos.put("recomendacionId", rec.get().id());
        return new VozResultado(true, "Te muestro la recomendación completa en pantalla.", datos, null);
    }

    @Override
    public VozResultado ejecutar(VozPendiente pendiente, Usuario usuario) {
        return new VozResultado(true, "Navegación realizada.", Map.of(), null);
    }
}
