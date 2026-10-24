package com.gelox.backend.recomendacion;

import com.gelox.backend.entities.AjustePresupuesto;
import com.gelox.backend.entities.Producto;
import com.gelox.backend.entities.UrgenciaRecomendacion;
import com.gelox.backend.recomendacion.DemandaRepository.DemandaDia;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Motor de recomendación de compras (RF54). Implementa tal cual las fórmulas del FP-15,
 * sección 2.10: demanda, estacionalidad, stock de seguridad, punto de reorden, urgencia,
 * score, cantidad sugerida, costo, margen y ajuste al presupuesto.
 *
 * <p>No accede a la base: recibe los productos y la demanda ya consultados, así que se prueba
 * con JUnit sin Spring.</p>
 */
@Component
public class MotorRecomendacionCompra {

    /** Días de la ventana de demanda. */
    public static final int N = 90;
    private static final int DIAS_RECIENTES = 28;
    private static final double Z = 1.65;
    private static final double F_MIN = 0.7;
    private static final double F_MAX = 1.5;
    private static final ZoneId ZONA_BOGOTA = ZoneId.of("America/Bogota");

    private static final DecimalFormat NUMERO =
            new DecimalFormat("#,##0.#", DecimalFormatSymbols.getInstance(Locale.forLanguageTag("es-CO")));

    private final int tiempoEntrega;    // L
    private final int periodoRevision;  // R

    public MotorRecomendacionCompra(
            @Value("${recomendacion.tiempo-entrega-dias:2}") int tiempoEntrega,
            @Value("${recomendacion.periodo-revision-dias:7}") int periodoRevision) {
        this.tiempoEntrega = tiempoEntrega;
        this.periodoRevision = periodoRevision;
    }

    /** Producto que no entra al cálculo por datos incompletos o por estar inactivo. */
    public record ProductoExcluido(Producto producto, String motivo) {}

    /** Resultado del cálculo para un producto sugerido, ya con prioridad y ajuste. */
    public record ItemCalculado(
            Producto producto,
            int prioridad,
            UrgenciaRecomendacion urgencia,
            BigDecimal score,
            BigDecimal demandaDiaria,
            BigDecimal factorEstacionalidad,
            BigDecimal stockSeguridad,
            BigDecimal puntoReorden,
            int stockActual,
            BigDecimal coberturaDias,
            int unidadesSugeridas,
            int cajasSugeridas,
            AjustePresupuesto ajustePresupuesto,
            BigDecimal costoEstimado,
            BigDecimal margenEsperado,
            String justificacion
    ) {}

    /** costoTotal = suma de los ítems COMPLETO y REDUCIDO. */
    public record Resultado(List<ItemCalculado> items, List<ProductoExcluido> productosExcluidos,
                            BigDecimal costoTotal) {}

    /** Variables intermedias de un producto antes de priorizar. */
    private record Calculo(Producto producto, double d, double f, double demandaAjustada,
                           double ss, double rop, Double cobertura, UrgenciaRecomendacion urgencia,
                           double score, double q, int cajas, double margenPct) {}

    public Resultado calcular(List<Producto> productos, List<DemandaDia> demanda, BigDecimal presupuesto) {
        return calcular(productos, demanda, presupuesto, LocalDate.now(ZONA_BOGOTA));
    }

    /** {@code hoy} es el último día de la ventana de N días. */
    public Resultado calcular(List<Producto> productos, List<DemandaDia> demanda,
                              BigDecimal presupuesto, LocalDate hoy) {
        Map<UUID, double[]> series = series(demanda, hoy);

        List<ProductoExcluido> excluidos = new ArrayList<>();
        List<Calculo> candidatos = new ArrayList<>();
        for (Producto p : productos) {
            String motivo = motivoExclusion(p);
            if (motivo != null) {
                excluidos.add(new ProductoExcluido(p, motivo));
                continue;
            }
            Calculo c = calcularProducto(p, series.getOrDefault(p.getId(), new double[N]));
            if (c.cajas() > 0) {
                candidatos.add(c);
            }
        }

        // Urgencia (CRITICO, ALERTA, ESTABLE), margen % descendente y score descendente.
        candidatos.sort(Comparator.comparing(Calculo::urgencia)
                .thenComparing(Calculo::margenPct, Comparator.reverseOrder())
                .thenComparing(Calculo::score, Comparator.reverseOrder()));

        List<ItemCalculado> items = new ArrayList<>();
        BigDecimal disponible = presupuesto;
        BigDecimal costoTotal = BigDecimal.ZERO;
        int prioridad = 1;
        for (Calculo c : candidatos) {
            BigDecimal precioCaja = c.producto().getPrecioCosto();
            BigDecimal costoCompleto = precioCaja.multiply(BigDecimal.valueOf(c.cajas()));

            AjustePresupuesto ajuste;
            int cajas;
            if (costoCompleto.compareTo(disponible) <= 0) {
                ajuste = AjustePresupuesto.COMPLETO;
                cajas = c.cajas();
            } else {
                cajas = disponible.divide(precioCaja, 0, RoundingMode.FLOOR).intValue();
                ajuste = cajas >= 1 ? AjustePresupuesto.REDUCIDO : AjustePresupuesto.EXCLUIDO;
                cajas = Math.max(cajas, 0);
            }
            BigDecimal costo = precioCaja.multiply(BigDecimal.valueOf(cajas));
            disponible = disponible.subtract(costo);
            costoTotal = costoTotal.add(costo);

            items.add(new ItemCalculado(
                    c.producto(), prioridad++, c.urgencia(),
                    redondear(c.score(), 2),
                    redondear(c.d(), 3),
                    redondear(c.f(), 2),
                    redondear(c.ss(), 2),
                    redondear(c.rop(), 2),
                    stockActual(c.producto()),
                    c.cobertura() == null ? null : redondear(c.cobertura(), 2),
                    (int) Math.ceil(redondearDouble(c.q())),
                    cajas, ajuste,
                    costo.setScale(2, RoundingMode.HALF_UP),
                    margen(c.producto(), cajas),
                    justificacion(c, ajuste, cajas)));
        }

        return new Resultado(items, excluidos, costoTotal.setScale(2, RoundingMode.HALF_UP));
    }

    // ══════════════════════════════════════════════════════════════════════
    // Fórmulas por producto (2.10.2 y 2.10.3)
    // ══════════════════════════════════════════════════════════════════════

    private Calculo calcularProducto(Producto p, double[] serie) {
        int stock = stockActual(p);
        int minimo = p.getStockMinimo() == null ? 0 : p.getStockMinimo();
        int unidadesCaja = p.getUnidadesPorCaja();
        int L = tiempoEntrega;
        int R = periodoRevision;

        double suma = 0;
        double sumaReciente = 0;
        for (int i = 0; i < N; i++) {
            suma += serie[i];
            if (i >= N - DIAS_RECIENTES) sumaReciente += serie[i];
        }
        double d = suma / N;
        double varianza = 0;
        for (double v : serie) varianza += (v - d) * (v - d);
        double sigma = Math.sqrt(varianza / N);           // desviación poblacional
        double d28 = sumaReciente / DIAS_RECIENTES;
        double f = d == 0 ? 1 : Math.min(F_MAX, Math.max(F_MIN, d28 / d));
        double demanda = d * f;                            // D

        double ss = Math.max(Z * sigma * Math.sqrt(L), minimo);
        double rop = demanda * L + ss;
        Double cobertura = demanda > 0 ? stock / demanda : null;

        UrgenciaRecomendacion urgencia;
        if (stock <= ss || (cobertura != null && cobertura < L)) {
            urgencia = UrgenciaRecomendacion.CRITICO;
        } else if (stock <= rop) {
            urgencia = UrgenciaRecomendacion.ALERTA;
        } else {
            urgencia = UrgenciaRecomendacion.ESTABLE;
        }

        double base = switch (urgencia) {
            case CRITICO -> 67;
            case ALERTA -> 34;
            case ESTABLE -> 0;
        };
        double presion;
        if (demanda > 0) {
            presion = 1 - Math.min(cobertura / (L + R), 1);
        } else if (minimo > 0) {
            presion = 1 - Math.min((double) stock / minimo, 1);
        } else {
            presion = stock <= 0 ? 1 : 0;
        }
        double score = base + 33 * presion;

        double q = Math.max(0, demanda * (L + R) + ss - stock);
        int cajas = (int) Math.ceil(redondearDouble(q / unidadesCaja));

        return new Calculo(p, d, f, demanda, ss, rop, cobertura, urgencia, score, q, cajas,
                margenPorcentaje(p));
    }

    /** m% = (precio_venta − costo unitario) ÷ precio_venta; 0 si precio_venta = 0. */
    private static double margenPorcentaje(Producto p) {
        double precioVenta = p.getPrecioVenta() == null ? 0 : p.getPrecioVenta().doubleValue();
        if (precioVenta <= 0) return 0;
        double costoUnitario = p.getPrecioCosto().doubleValue() / p.getUnidadesPorCaja();
        return (precioVenta - costoUnitario) / precioVenta;
    }

    /**
     * margen = (precio_venta − precio_costo ÷ unidades_por_caja) × cajas × unidades_por_caja,
     * escrito como precio_venta × unidades − precio_costo × cajas para no perder centavos.
     */
    private static BigDecimal margen(Producto p, int cajas) {
        BigDecimal precioVenta = p.getPrecioVenta() == null ? BigDecimal.ZERO : p.getPrecioVenta();
        if (precioVenta.signum() <= 0 || cajas == 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        BigDecimal unidades = BigDecimal.valueOf((long) cajas * p.getUnidadesPorCaja());
        return precioVenta.multiply(unidades)
                .subtract(p.getPrecioCosto().multiply(BigDecimal.valueOf(cajas)))
                .setScale(2, RoundingMode.HALF_UP);
    }

    // ══════════════════════════════════════════════════════════════════════
    // Justificación (plantillas de 2.10.4)
    // ══════════════════════════════════════════════════════════════════════

    private String justificacion(Calculo c, AjustePresupuesto ajuste, int cajas) {
        Producto p = c.producto();
        int stock = stockActual(p);
        StringBuilder texto = new StringBuilder();

        switch (c.urgencia()) {
            case CRITICO -> {
                if (c.demandaAjustada() == 0) {
                    texto.append("Crítico: ").append(stock).append(" unidades, por debajo del mínimo configurado (")
                            .append(p.getStockMinimo()).append("). Sin ventas en los últimos ").append(N).append(" días.");
                } else if (c.cobertura() < tiempoEntrega) {
                    texto.append("Crítico: quedan ").append(numero(c.cobertura())).append(" días de inventario y el proveedor tarda ")
                            .append(tiempoEntrega).append(" días. Promedio de ").append(numero(c.d()))
                            .append(" unidades/día en ").append(N).append(" días.");
                } else {
                    texto.append("Crítico: ").append(stock).append(" unidades, por debajo del stock de seguridad (")
                            .append(numero(c.ss())).append("). Promedio de ").append(numero(c.d()))
                            .append(" unidades/día en ").append(N).append(" días.");
                }
            }
            case ALERTA -> texto.append("Alerta: ").append(stock).append(" unidades, por debajo del punto de reorden (")
                    .append(numero(c.rop())).append("). Alcanza para ").append(numero(c.cobertura())).append(" días.");
            case ESTABLE -> texto.append("Estable: cubre ").append(numero(c.cobertura()))
                    .append(" días; se sugiere completar hasta el próximo pedido (")
                    .append(tiempoEntrega).append("+").append(periodoRevision).append(" días).");
        }

        if (c.f() != 1) {
            texto.append(" Las ventas del último mes van ").append(numero(c.f())).append("× ")
                    .append(c.f() > 1 ? "por encima" : "por debajo").append(" del promedio.");
        }

        if (ajuste == AjustePresupuesto.REDUCIDO) {
            texto.append(" Reducido a ").append(cajas).append(cajas == 1 ? " caja" : " cajas").append(" por presupuesto.");
        } else if (ajuste == AjustePresupuesto.EXCLUIDO) {
            texto.append(" Excede el presupuesto.");
        }
        return texto.toString();
    }

    // ══════════════════════════════════════════════════════════════════════
    // Utilidades
    // ══════════════════════════════════════════════════════════════════════

    /** Inactivo, sin unidades_por_caja o con precio_costo nulo o 0 → no entra al cálculo. */
    private static String motivoExclusion(Producto p) {
        if (!Boolean.TRUE.equals(p.getActivo())) return "Producto inactivo";
        if (p.getUnidadesPorCaja() == null || p.getUnidadesPorCaja() <= 0) return "Sin unidades por caja";
        if (p.getPrecioCosto() == null || p.getPrecioCosto().signum() <= 0) return "Sin precio de costo";
        return null;
    }

    /** Serie de N días por producto, con ceros en los días sin venta. */
    private static Map<UUID, double[]> series(List<DemandaDia> demanda, LocalDate hoy) {
        LocalDate desde = hoy.minusDays(N - 1);
        Map<UUID, double[]> series = new HashMap<>();
        for (DemandaDia dia : demanda) {
            long i = ChronoUnit.DAYS.between(desde, dia.fecha());
            if (i < 0 || i >= N) continue;
            series.computeIfAbsent(dia.productoId(), k -> new double[N])[(int) i] += dia.unidades();
        }
        return series;
    }

    private static int stockActual(Producto p) {
        return p.getStockActual() == null ? 0 : p.getStockActual();
    }

    private static BigDecimal redondear(double valor, int decimales) {
        return BigDecimal.valueOf(valor).setScale(decimales, RoundingMode.HALF_UP);
    }

    /** Evita que un error de coma flotante (p. ej. 2,0000000001) sume una caja de más. */
    private static double redondearDouble(double valor) {
        return BigDecimal.valueOf(valor).setScale(6, RoundingMode.HALF_UP).doubleValue();
    }

    private static String numero(double valor) {
        synchronized (NUMERO) {
            return NUMERO.format(valor);
        }
    }
}
