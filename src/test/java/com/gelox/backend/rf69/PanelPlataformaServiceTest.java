package com.gelox.backend.rf69;

import com.gelox.backend.entities.Distribuidora;
import com.gelox.backend.entities.EstadoDistribuidora;
import com.gelox.backend.gobierno.CuotaService;
import com.gelox.backend.gobierno.CuotaService.UsoCuota;
import com.gelox.backend.gobierno.PanelPlataformaRepository;
import com.gelox.backend.gobierno.PanelPlataformaRepository.FilaConteo;
import com.gelox.backend.gobierno.PanelPlataformaRepository.FilaConteoValor;
import com.gelox.backend.gobierno.PanelPlataformaRepository.PuntoSerie;
import com.gelox.backend.gobierno.PanelPlataformaService;
import com.gelox.backend.gobierno.PlanSuscripcionRepository;
import com.gelox.backend.gobierno.dto.PanelPlataformaDTO;
import com.gelox.backend.repositories.DistribuidoraRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * RF69 — Ensamblado del DTO del panel: rango por defecto, validación de
 * fechas y período sin datos (todo en ceros, no errores).
 */
@ExtendWith(MockitoExtension.class)
class PanelPlataformaServiceTest {

    @Mock PanelPlataformaRepository panelRepository;
    @Mock DistribuidoraRepository distribuidoraRepository;
    @Mock PlanSuscripcionRepository planRepository;
    @Mock CuotaService cuotaService;

    @InjectMocks
    PanelPlataformaService panelPlataformaService;

    private void sinDatos() {
        when(distribuidoraRepository.findAll()).thenReturn(List.of());
        when(panelRepository.totalVentas(any(), any())).thenReturn(new FilaConteoValor(null, 0, BigDecimal.ZERO));
        when(panelRepository.totalPedidosProveedor(any(), any())).thenReturn(new FilaConteoValor(null, 0, BigDecimal.ZERO));
        when(panelRepository.totalPlanillasLiquidadas(any(), any())).thenReturn(0L);
        when(panelRepository.totalLlamadasIa(any(), any())).thenReturn(0L);
        when(panelRepository.totalComandosVoz(any(), any())).thenReturn(0L);
        when(panelRepository.totalRecomendaciones(any(), any())).thenReturn(0L);
        when(panelRepository.usuariosNuevos(any(), any())).thenReturn(0L);
        when(panelRepository.usuariosRegistrados()).thenReturn(0L);
        when(panelRepository.usuariosHabilitados()).thenReturn(0L);
        when(panelRepository.almacenamientoUsadoBytes()).thenReturn(0L);
        when(panelRepository.almacenamientoAsignadoMb()).thenReturn(0L);
        when(panelRepository.ventasPorDistribuidora(any(), any())).thenReturn(List.of());
        when(panelRepository.llamadasIaPorDistribuidora(any(), any())).thenReturn(List.of());
        when(panelRepository.seriePorDia(any(), any())).thenReturn(List.of());
    }

    @Test
    @DisplayName("período sin datos → todo en ceros, sin errores")
    void generar_periodoSinDatos_todoEnCeros() {
        sinDatos();

        PanelPlataformaDTO panel = panelPlataformaService.generar(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31));

        assertThat(panel.distribuidoras().total()).isZero();
        assertThat(panel.ventas().valor()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(panel.ventas().transacciones()).isZero();
        assertThat(panel.pedidosProveedor().cantidad()).isZero();
        assertThat(panel.planillasLiquidadas()).isZero();
        assertThat(panel.ia().llamadas()).isZero();
        assertThat(panel.porDistribuidora()).isEmpty();
    }

    @Test
    @DisplayName("sin desde/hasta usa los últimos 30 días hasta hoy")
    void generar_sinFechas_usaRangoPorDefecto() {
        sinDatos();

        PanelPlataformaDTO panel = panelPlataformaService.generar(null, null);

        LocalDate hoy = LocalDate.now();
        assertThat(panel.hasta()).isEqualTo(hoy);
        assertThat(panel.desde()).isEqualTo(hoy.minusDays(29));
    }

    @Test
    @DisplayName("desde posterior a hasta → 400")
    void generar_desdePosteriorAHasta_retorna400() {
        assertThatThrownBy(() -> panelPlataformaService.generar(LocalDate.of(2026, 2, 1), LocalDate.of(2026, 1, 1)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("fecha inicial");
    }

    @Test
    @DisplayName("cuenta las distribuidoras por estado, incluidos los estados sin ninguna (en cero)")
    void generar_contarPorEstado_incluyeEstadosEnCero() {
        sinDatos();
        Distribuidora activa = distribuidoraDe(EstadoDistribuidora.ACTIVO);
        Distribuidora prueba = distribuidoraDe(EstadoDistribuidora.PRUEBA);
        when(distribuidoraRepository.findAll()).thenReturn(List.of(activa, prueba));
        when(cuotaService.uso(any(), any())).thenReturn(java.util.Map.of());

        PanelPlataformaDTO panel = panelPlataformaService.generar(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31));

        assertThat(panel.distribuidoras().total()).isEqualTo(2);
        assertThat(panel.distribuidoras().porEstado().get("ACTIVO")).isEqualTo(1L);
        assertThat(panel.distribuidoras().porEstado().get("PRUEBA")).isEqualTo(1L);
        assertThat(panel.distribuidoras().porEstado().get("SUSPENDIDO")).isEqualTo(0L);
        assertThat(panel.distribuidoras().porEstado().get("INACTIVO")).isEqualTo(0L);
    }

    @Test
    @DisplayName("porDistribuidora cruza ventas e IA por tenant con el detalle de cada distribuidora")
    void generar_porDistribuidora_cruzaVentasEIa() {
        sinDatos();
        UUID tenantId = UUID.randomUUID();
        Distribuidora d = distribuidoraDe(EstadoDistribuidora.ACTIVO);
        d.setId(tenantId);
        d.setNombreComercial("Helados Prueba");
        when(distribuidoraRepository.findAll()).thenReturn(List.of(d));
        when(panelRepository.ventasPorDistribuidora(any(), any()))
                .thenReturn(List.of(new FilaConteoValor(tenantId, 3, BigDecimal.valueOf(9000))));
        when(panelRepository.llamadasIaPorDistribuidora(any(), any()))
                .thenReturn(List.of(new FilaConteo(tenantId, 5)));
        when(cuotaService.uso(any(), any())).thenReturn(java.util.Map.of(
                "usuarios", new UsoCuota(2, 10)));

        PanelPlataformaDTO panel = panelPlataformaService.generar(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31));

        assertThat(panel.porDistribuidora()).hasSize(1);
        PanelPlataformaDTO.PorDistribuidora fila = panel.porDistribuidora().get(0);
        assertThat(fila.distribuidoraId()).isEqualTo(tenantId);
        assertThat(fila.nombreComercial()).isEqualTo("Helados Prueba");
        assertThat(fila.ventas().valor()).isEqualByComparingTo("9000");
        assertThat(fila.ventas().transacciones()).isEqualTo(3);
        assertThat(fila.llamadasIa()).isEqualTo(5);
        assertThat(fila.uso()).containsKey("usuarios");
    }

    @Test
    @DisplayName("la serie cubre al menos 6 meses terminando en 'hasta'")
    void generar_serieCubreSeisMeses() {
        sinDatos();

        LocalDate hasta = LocalDate.of(2026, 6, 15);
        panelPlataformaService.generar(LocalDate.of(2026, 6, 1), hasta);

        org.mockito.ArgumentCaptor<LocalDate> inicioCaptor = org.mockito.ArgumentCaptor.forClass(LocalDate.class);
        org.mockito.Mockito.verify(panelRepository).seriePorDia(inicioCaptor.capture(), org.mockito.Mockito.eq(hasta));
        // Debe retroceder al día 1 de 5 meses antes de junio = 1 de enero de 2026.
        assertThat(inicioCaptor.getValue()).isEqualTo(LocalDate.of(2026, 1, 1));
    }

    private static Distribuidora distribuidoraDe(EstadoDistribuidora estado) {
        Distribuidora d = new Distribuidora();
        d.setId(UUID.randomUUID());
        d.setEstado(estado);
        d.setNombreComercial("Distribuidora");
        d.setRazonSocial("Razon Social");
        return d;
    }
}
