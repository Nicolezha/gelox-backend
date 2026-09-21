package com.gelox.backend.voz;

import com.gelox.backend.entities.Producto;
import com.gelox.backend.repositories.ProductoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ResolvedorProductoTest {

    @Mock
    ProductoRepository productoRepository;

    ResolvedorProducto resolvedor;

    private Producto festival;
    private Producto soloLack;

    @BeforeEach
    void setUp() {
        resolvedor = new ResolvedorProducto(productoRepository);

        festival = producto("Festival", 24);
        soloLack = producto("Solo Lack", 12);
    }

    @Test
    @DisplayName("coincidencia exacta por contención devuelve puntaje 1.0")
    void coincidenciaExacta() {
        when(productoRepository.findByActivoTrue()).thenReturn(List.of(festival, soloLack));

        List<ResolvedorProducto.ProductoCandidato> candidatos = resolvedor.resolver("festival");

        assertThat(candidatos).hasSize(1);
        assertThat(candidatos.get(0).nombre()).isEqualTo("Festival");
        assertThat(candidatos.get(0).score()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("pronunciación imprecisa resuelve por Levenshtein")
    void coincidenciaDifusa() {
        when(productoRepository.findByActivoTrue()).thenReturn(List.of(festival, soloLack));

        List<ResolvedorProducto.ProductoCandidato> candidatos = resolvedor.resolver("solo lac");

        assertThat(candidatos).hasSize(1);
        assertThat(candidatos.get(0).nombre()).isEqualTo("Solo Lack");
        assertThat(candidatos.get(0).score()).isGreaterThanOrEqualTo(0.75);
    }

    @Test
    @DisplayName("nombre inexistente no devuelve candidatos")
    void sinCoincidencia() {
        when(productoRepository.findByActivoTrue()).thenReturn(List.of(festival, soloLack));

        List<ResolvedorProducto.ProductoCandidato> candidatos = resolvedor.resolver("panqueque");

        assertThat(candidatos).isEmpty();
    }

    @Test
    @DisplayName("candidatos ambiguos quedan a menos de 0.10 de diferencia")
    void candidatosAmbiguos() {
        Producto festivalMini = producto("Festival Mini", 24);
        when(productoRepository.findByActivoTrue()).thenReturn(List.of(festival, festivalMini));

        List<ResolvedorProducto.ProductoCandidato> candidatos = resolvedor.resolver("festival");

        assertThat(candidatos).hasSizeGreaterThanOrEqualTo(2);
        double diferencia = candidatos.get(0).score() - candidatos.get(1).score();
        assertThat(diferencia).isLessThan(0.10);
    }

    private static Producto producto(String nombre, int unidadesPorCaja) {
        Producto p = new Producto();
        p.setId(UUID.randomUUID());
        p.setNombre(nombre);
        p.setUnidadesPorCaja(unidadesPorCaja);
        return p;
    }
}
