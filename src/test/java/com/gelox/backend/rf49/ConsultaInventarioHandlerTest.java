package com.gelox.backend.rf49;

import com.gelox.backend.entities.Producto;
import com.gelox.backend.entities.RolUsuario;
import com.gelox.backend.entities.Usuario;
import com.gelox.backend.repositories.ProductoRepository;
import com.gelox.backend.voz.ResolvedorProducto;
import com.gelox.backend.voz.VozContexto;
import com.gelox.backend.voz.VozResultado;
import com.gelox.backend.voz.handlers.ConsultaInventarioHandler;
import com.gelox.backend.TestHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * RF49 — consulta de inventario por voz: cajas/unidades, unidadesPorCaja
 * nulo, alerta por debajo del mínimo, producto inexistente y listado de
 * stock bajo (sin producto nombrado).
 */
@ExtendWith(MockitoExtension.class)
class ConsultaInventarioHandlerTest {

    @Mock
    ResolvedorProducto resolvedorProducto;

    @Mock
    ProductoRepository productoRepository;

    ConsultaInventarioHandler handler;
    Usuario usuario;

    @BeforeEach
    void setUp() {
        handler = new ConsultaInventarioHandler(resolvedorProducto, productoRepository);
        usuario = TestHelper.buildUsuario("uid-1", "voz@gelox-test.com", RolUsuario.ENCARGADO_VENTAS, true);
    }

    private VozContexto ctx(String texto) {
        return new VozContexto(texto, Map.of(), 0.9, usuario, LocalDate.now());
    }

    private Producto producto(String nombre, int stockActual, int stockMinimo, Integer unidadesPorCaja) {
        Producto p = new Producto();
        p.setId(UUID.randomUUID());
        p.setCodigoTecnico(nombre.toUpperCase());
        p.setNombre(nombre);
        p.setPrecioVenta(BigDecimal.TEN);
        p.setPrecioCosto(BigDecimal.ONE);
        p.setStockActual(stockActual);
        p.setStockMinimo(stockMinimo);
        p.setUnidadesPorCaja(unidadesPorCaja);
        return p;
    }

    private ResolvedorProducto.ProductoCandidato candidato(Producto p) {
        return new ResolvedorProducto.ProductoCandidato(p.getId(), p.getNombre(), 1.0, p.getUnidadesPorCaja());
    }

    @Test
    @DisplayName("54 unidades con 12 por caja → 4 cajas y 6 unidades")
    void productoConUnidadesPorCaja_narraCajasYUnidades() {
        Producto festival = producto("Festival", 54, 10, 12);
        when(resolvedorProducto.resolver("festival")).thenReturn(List.of(candidato(festival)));
        when(productoRepository.findById(festival.getId())).thenReturn(Optional.of(festival));

        VozResultado resultado = handler.interpretar(ctx("¿Cuántas cajas de Festival me quedan?"));

        assertThat(resultado.ok()).isTrue();
        assertThat(resultado.textoRespuesta()).contains("4 cajas y 6 unidades");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> productos = (List<Map<String, Object>>) resultado.datos().get("productos");
        assertThat(productos.get(0)).containsEntry("cajas", 4).containsEntry("unidadesSueltas", 6);
    }

    @Test
    @DisplayName("unidadesPorCaja null → narra solo unidades")
    void productoSinUnidadesPorCaja_narraSoloUnidades() {
        Producto suelto = producto("Suelto", 54, 10, null);
        when(resolvedorProducto.resolver("suelto")).thenReturn(List.of(candidato(suelto)));
        when(productoRepository.findById(suelto.getId())).thenReturn(Optional.of(suelto));

        VozResultado resultado = handler.interpretar(ctx("cuántas unidades de suelto quedan"));

        assertThat(resultado.ok()).isTrue();
        assertThat(resultado.textoRespuesta()).contains("54 unidades de Suelto");
        assertThat(resultado.textoRespuesta()).doesNotContain("cajas");
    }

    @Test
    @DisplayName("producto bajo el mínimo agrega alerta")
    void productoBajoMinimo_agregaAlerta() {
        Producto bajo = producto("Bajo", 5, 10, 12);
        when(resolvedorProducto.resolver("bajo")).thenReturn(List.of(candidato(bajo)));
        when(productoRepository.findById(bajo.getId())).thenReturn(Optional.of(bajo));

        VozResultado resultado = handler.interpretar(ctx("cuántas cajas de bajo quedan"));

        assertThat(resultado.ok()).isTrue();
        assertThat(resultado.textoRespuesta()).contains("Atención").contains("por debajo del mínimo");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> alertas = (List<Map<String, Object>>) resultado.datos().get("alertas");
        assertThat(alertas).hasSize(1);
        assertThat(alertas.get(0)).containsEntry("nombre", "Bajo");
    }

    @Test
    @DisplayName("producto inexistente responde ok=false")
    void productoInexistente_retornaOkFalse() {
        when(resolvedorProducto.resolver("inexistente")).thenReturn(List.of());

        VozResultado resultado = handler.interpretar(ctx("cuántas cajas de inexistente quedan"));

        assertThat(resultado.ok()).isFalse();
        assertThat(resultado.textoRespuesta()).contains("No encontré ese producto");
    }

    @Test
    @DisplayName("sin producto nombrado → lista los productos bajo el mínimo")
    void sinProductoNombrado_listaStockBajo() {
        Producto bajo1 = producto("Uno", 2, 10, 12);
        Producto bajo2 = producto("Dos", 3, 10, 12);
        when(productoRepository.findProductosBajoStock()).thenReturn(List.of(bajo1, bajo2));

        VozResultado resultado = handler.interpretar(ctx("qué productos están por bajar del mínimo"));

        assertThat(resultado.ok()).isTrue();
        assertThat(resultado.textoRespuesta()).contains("Uno").contains("Dos");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> productos = (List<Map<String, Object>>) resultado.datos().get("productos");
        assertThat(productos).hasSize(2);
    }
}
