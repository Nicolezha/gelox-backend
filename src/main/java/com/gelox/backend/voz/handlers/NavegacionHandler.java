package com.gelox.backend.voz.handlers;

import com.gelox.backend.entities.TipoIntencionVoz;
import com.gelox.backend.entities.Usuario;
import com.gelox.backend.voz.IntencionHandler;
import com.gelox.backend.voz.NormalizadorVoz;
import com.gelox.backend.voz.VozContexto;
import com.gelox.backend.voz.VozPendiente;
import com.gelox.backend.voz.VozResultado;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Manejador de navegación por voz ("ve a la pestaña de ventas", "abre inventarios").
 * Resuelve la ruta frontend destino a partir de los términos dictados y retorna la acción
 * para que el cliente web navegue automáticamente.
 */
@Component
public class NavegacionHandler implements IntencionHandler {

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

    @Override
    public VozResultado ejecutar(VozPendiente pendiente, Usuario usuario) {
        return new VozResultado(true, "Navegación realizada.", Map.of(), null);
    }
}
