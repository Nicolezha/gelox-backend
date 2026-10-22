package com.gelox.backend.recomendacion;

import com.gelox.backend.entities.AjustePresupuesto;
import com.gelox.backend.entities.EstadoRecomendacion;
import com.gelox.backend.entities.ItemRecomendacion;
import com.gelox.backend.entities.OrigenRecomendacion;
import com.gelox.backend.entities.PedidoProveedor;
import com.gelox.backend.entities.Producto;
import com.gelox.backend.entities.RecomendacionCompra;
import com.gelox.backend.entities.UrgenciaRecomendacion;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Datos de prueba compartidos por los tests de RF55, RF56 y RF57.
 */
public final class RecomendacionTestData {

    public static final UUID TENANT = UUID.randomUUID();

    private RecomendacionTestData() {
    }

    public static Producto producto(String nombre, int unidadesPorCaja) {
        Producto producto = new Producto();
        producto.setId(UUID.randomUUID());
        producto.setNombre(nombre);
        producto.setUnidadesPorCaja(unidadesPorCaja);
        producto.setTenantId(TENANT);
        return producto;
    }

    /** Ítem PENDIENTE; con {@code EXCLUIDO} las cajas sugeridas deben ser 0. */
    public static ItemRecomendacion item(Producto producto, int prioridad, int cajasSugeridas,
                                         String costoEstimado, AjustePresupuesto ajuste) {
        return ItemRecomendacion.builder()
                .id(UUID.randomUUID())
                .producto(producto)
                .prioridad(prioridad)
                .urgencia(UrgenciaRecomendacion.CRITICO)
                .cajasSugeridas(cajasSugeridas)
                .unidadesSugeridas(cajasSugeridas * producto.getUnidadesPorCaja())
                .ajustePresupuesto(ajuste)
                .costoEstimado(new BigDecimal(costoEstimado))
                .build();
    }

    public static ItemRecomendacion item(Producto producto, int prioridad, int cajasSugeridas, String costoEstimado) {
        return item(producto, prioridad, cajasSugeridas, costoEstimado, AjustePresupuesto.COMPLETO);
    }

    public static RecomendacionCompra recomendacion(UUID tenant, EstadoRecomendacion estado,
                                                    ItemRecomendacion... items) {
        return RecomendacionCompra.builder()
                .id(UUID.randomUUID())
                .tenantId(tenant)
                .estado(estado)
                .origen(OrigenRecomendacion.PANTALLA)
                .items(new ArrayList<>(List.of(items)))
                .build();
    }

    public static PedidoProveedor pedido(com.gelox.backend.entities.EstadoPedido estado) {
        return PedidoProveedor.builder()
                .id(UUID.randomUUID())
                .tenantId(TENANT)
                .estado(estado)
                .build();
    }
}
