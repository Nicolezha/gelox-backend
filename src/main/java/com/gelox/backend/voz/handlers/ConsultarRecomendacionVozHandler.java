package com.gelox.backend.voz.handlers;

import com.gelox.backend.entities.AjustePresupuesto;
import com.gelox.backend.entities.EstadoRecomendacion;
import com.gelox.backend.entities.OrigenRecomendacion;
import com.gelox.backend.entities.TipoIntencionVoz;
import com.gelox.backend.entities.UrgenciaRecomendacion;
import com.gelox.backend.entities.Usuario;
import com.gelox.backend.recomendacion.RecomendacionCompraService;
import com.gelox.backend.recomendacion.dto.ItemDTO;
import com.gelox.backend.recomendacion.dto.RecomendacionDTO;
import com.gelox.backend.recomendacion.dto.RecomendacionGeneradaDTO;
import com.gelox.backend.security.RequiereRol;
import com.gelox.backend.voz.FechaVoz;
import com.gelox.backend.voz.IntencionHandler;
import com.gelox.backend.voz.NormalizadorVoz;
import com.gelox.backend.voz.PreguntaVoz;
import com.gelox.backend.voz.VozContexto;
import com.gelox.backend.voz.VozPendiente;
import com.gelox.backend.voz.VozResultado;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * RF55 — CONSULTAR_RECOMENDACION por voz (FP-15 §2.11.5), sin confirmación: narra la
 * recomendación de hoy o, si no hay y se dijo el presupuesto, la genera con origen VOZ.
 * <p>
 * Es un handler aparte de {@link RecomendacionVozHandler} porque VozService indexa un handler
 * por intención y esta no pide confirmación.
 */
@Component
@RequiredArgsConstructor
public class ConsultarRecomendacionVozHandler implements IntencionHandler {

    private static final String[] ORDINALES = {"Primero", "Segundo", "Tercero"};

    private static final ZoneId ZONA_BOGOTA = ZoneId.of("America/Bogota");

    private static final List<EstadoRecomendacion> VISIBLES = List.of(EstadoRecomendacion.GENERADA,
            EstadoRecomendacion.EN_REVISION, EstadoRecomendacion.PEDIDO_GENERADO);

    private final RecomendacionCompraService recomendacionService;

    @Override
    public TipoIntencionVoz tipo() {
        return TipoIntencionVoz.CONSULTAR_RECOMENDACION;
    }

    @Override
    public boolean requiereConfirmacion() {
        return false;
    }

    @Override
    @RequiereRol({"ADMINISTRADOR", "ENCARGADO_INVENTARIO"})
    public VozResultado interpretar(VozContexto ctx) {
        LocalDate hoy = ctx.hoy() != null ? ctx.hoy() : LocalDate.now(ZONA_BOGOTA);
        Optional<LocalDate> fecha = FechaVoz.de(NormalizadorVoz.normalizar(ctx.texto()), hoy)
                .or(() -> FechaVoz.deSlot(ctx.slots().get("fecha")))
                .filter(f -> !f.equals(hoy));
        if (fecha.isPresent()) {
            return deOtroDia(fecha.get(), hoy, ctx.slots());
        }

        BigDecimal presupuesto = presupuesto(ctx.slots().get("presupuesto"));
        Optional<RecomendacionDTO> delDia = recomendacionService.recomendacionDelDia();
        // Sin presupuesto, o con el mismo de la recomendación de hoy, se narra esa; con otro se genera una nueva.
        if (delDia.isPresent() && (presupuesto == null || presupuesto.compareTo(delDia.get().presupuesto()) == 0)) {
            return narrar(delDia.get().id(), delDia.get().items(), delDia.get().costoTotal());
        }

        if (presupuesto == null) {
            return new VozResultado(false, RecomendacionVozHandler.SIN_RECOMENDACION, Map.of(), null);
        }
        // ponytail: con baja confianza (RNF-5) la recomendación ya queda generada antes de confirmar.
        RecomendacionGeneradaDTO generada = recomendacionService.generar(presupuesto, OrigenRecomendacion.VOZ, ctx.usuario());
        return narrar(generada.id(), generada.items(), generada.costoTotal());
    }

    /** "¿Cuál fue la recomendación del 15 de noviembre?": si ese día hubo varias, pregunta cuál. */
    private VozResultado deOtroDia(LocalDate fecha, LocalDate hoy, Map<String, String> slotsOriginales) {
        Map<String, String> slots = new LinkedHashMap<>(slotsOriginales);
        String respuesta = slots.remove(PreguntaVoz.SLOT_RESPUESTA);
        slots.put("fecha", fecha.toString());
        List<RecomendacionDTO> delDia = recomendacionService.recomendacionesDelDia(fecha, VISIBLES);
        if (delDia.isEmpty()) {
            return new VozResultado(false, "No hay recomendaciones " + SelectorRecomendacion.deFecha(fecha, hoy) + ".",
                    Map.of(), null);
        }
        List<RecomendacionDTO> opciones = SelectorRecomendacion.opciones(delDia, slots.get(SelectorRecomendacion.SLOT_OPCIONES));
        boolean filtradas = opciones.size() != delDia.size();
        RecomendacionDTO elegida;
        if (delDia.size() == 1) {
            elegida = delDia.get(0);
        } else if (respuesta == null) {
            return SelectorRecomendacion.preguntarCual(null, opciones, fecha, hoy, "ver", slots, filtradas);
        } else {
            SelectorRecomendacion.Resultado seleccion = SelectorRecomendacion.elegir(respuesta, opciones);
            if (seleccion.coincidencias().size() > 1) {
                return SelectorRecomendacion.preguntarCual(null, seleccion.coincidencias(), fecha, hoy, "ver", slots, true);
            }
            if (seleccion.coincidencias().isEmpty()) {
                return SelectorRecomendacion.preguntarCual("No identifiqué cuál. ", opciones, fecha, hoy, "ver", slots, filtradas);
            }
            elegida = seleccion.coincidencias().get(0);
        }
        return narrar(elegida.id(), elegida.items(), elegida.costoTotal());
    }

    /** Solo llega aquí tras confirmar por baja confianza: la respuesta ya se calculó al interpretar. */
    @Override
    @RequiereRol({"ADMINISTRADOR", "ENCARGADO_INVENTARIO"})
    public VozResultado ejecutar(VozPendiente pendiente, Usuario usuario) {
        return (VozResultado) pendiente.payload();
    }

    /** «Te recomiendo N productos por $X. Primero, crítico: Aloha Paleta Limón, 1 caja. …» */
    static VozResultado narrar(UUID recomendacionId, List<ItemDTO> items, BigDecimal costoTotal) {
        List<ItemDTO> conCajas = items.stream()
                .filter(i -> i.ajustePresupuesto() != AjustePresupuesto.EXCLUIDO
                        && i.cajasSugeridas() != null && i.cajasSugeridas() > 0)
                .toList();
        String texto;
        if (conCajas.isEmpty()) {
            texto = "Hoy no hay productos que convenga pedir.";
        } else {
            StringBuilder sb = new StringBuilder("Te recomiendo ")
                    .append(conCajas.size()).append(conCajas.size() == 1 ? " producto" : " productos")
                    .append(" por ").append(RecomendacionVozHandler.pesos(costoTotal)).append('.');
            for (int i = 0; i < Math.min(ORDINALES.length, conCajas.size()); i++) {
                ItemDTO item = conCajas.get(i);
                sb.append(' ').append(ORDINALES[i]).append(", ").append(urgencia(item.urgencia())).append(": ")
                        .append(item.productoNombre()).append(", ")
                        .append(RecomendacionVozHandler.cajas(item.cajasSugeridas())).append('.');
            }
            texto = sb.toString();
        }
        Map<String, Object> datos = Map.of("recomendacionId", recomendacionId);
        VozResultado resultado = new VozResultado(true, texto, datos, null);
        return new VozResultado(true, texto, datos, resultado);
    }

    private static String urgencia(UrgenciaRecomendacion urgencia) {
        if (urgencia == null) return "estable";
        return switch (urgencia) {
            case CRITICO -> "crítico";
            case ALERTA -> "alerta";
            case ESTABLE -> "estable";
        };
    }

    private static BigDecimal presupuesto(String slot) {
        if (slot == null || slot.isBlank()) return null;
        try {
            BigDecimal valor = new BigDecimal(slot.trim());
            return valor.signum() > 0 ? valor : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
