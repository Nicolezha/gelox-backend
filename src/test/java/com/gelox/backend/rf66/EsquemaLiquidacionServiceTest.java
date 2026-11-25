package com.gelox.backend.rf66;

import com.gelox.backend.credito.EsquemaLiquidacionService;
import com.gelox.backend.credito.dto.EsquemaLiquidacionDTO;
import com.gelox.backend.credito.dto.EsquemaLiquidacionRequest;
import com.gelox.backend.entities.EsquemaLiquidacion;
import com.gelox.backend.entities.TipoEsquemaLiquidacion;
import com.gelox.backend.repositories.EsquemaLiquidacionRepository;
import com.gelox.backend.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/** RF66 — lectura y cambio del esquema de liquidación vigente de la distribuidora. */
@ExtendWith(MockitoExtension.class)
class EsquemaLiquidacionServiceTest {

    private static final UUID TENANT = UUID.randomUUID();

    @Mock EsquemaLiquidacionRepository repository;

    @InjectMocks
    EsquemaLiquidacionService service;

    @BeforeEach
    void setUp() {
        TenantContext.set(TENANT);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("obtenerActual devuelve el esquema más reciente de la distribuidora, con valor null para MARGEN")
    void obtenerActual_devuelveElVigente() {
        when(repository.findTopByTenantIdOrderByVigenteDesdeDesc(TENANT)).thenReturn(Optional.of(
                EsquemaLiquidacion.builder().tipo(TipoEsquemaLiquidacion.MARGEN).valor(BigDecimal.ZERO).build()));

        EsquemaLiquidacionDTO dto = service.obtenerActual();

        assertThat(dto.tipo()).isEqualTo(TipoEsquemaLiquidacion.MARGEN);
        assertThat(dto.valor()).isNull();
    }

    @Test
    @DisplayName("sin esquema configurado, lanza 500")
    void sinEsquema_lanza500() {
        when(repository.findTopByTenantIdOrderByVigenteDesdeDesc(TENANT)).thenReturn(Optional.empty());

        assertThatThrownBy(service::obtenerActual).isInstanceOf(ResponseStatusException.class);
    }

    @Test
    @DisplayName("actualizar crea una nueva fila de esquema (es un historial, no se sobrescribe)")
    void actualizar_creaNuevaFilaDeEsquema() {
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        EsquemaLiquidacionDTO dto = service.actualizar(
                new EsquemaLiquidacionRequest(TipoEsquemaLiquidacion.COMISION, BigDecimal.valueOf(15)));

        assertThat(dto.tipo()).isEqualTo(TipoEsquemaLiquidacion.COMISION);
        assertThat(dto.valor()).isEqualByComparingTo("15");
    }

    @Test
    @DisplayName("actualizar a MARGEN ignora el valor recibido y lo guarda en cero")
    void actualizar_margen_ignoraElValor() {
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        EsquemaLiquidacionDTO dto = service.actualizar(new EsquemaLiquidacionRequest(TipoEsquemaLiquidacion.MARGEN, null));

        assertThat(dto.valor()).isNull();
    }

    @Test
    @DisplayName("COMISION fuera de 0-100 % lanza 400")
    void actualizar_comisionFueraDeRango_lanza400() {
        assertThatThrownBy(() -> service.actualizar(new EsquemaLiquidacionRequest(TipoEsquemaLiquidacion.COMISION, BigDecimal.valueOf(150))))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> service.actualizar(new EsquemaLiquidacionRequest(TipoEsquemaLiquidacion.COMISION, BigDecimal.ZERO)))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    @DisplayName("TARIFA_FIJA negativa lanza 400")
    void actualizar_tarifaNegativa_lanza400() {
        assertThatThrownBy(() -> service.actualizar(new EsquemaLiquidacionRequest(TipoEsquemaLiquidacion.TARIFA_FIJA, BigDecimal.valueOf(-5))))
                .isInstanceOf(ResponseStatusException.class);
    }
}
