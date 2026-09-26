package com.gelox.backend.rf46;

import com.gelox.backend.entities.Producto;
import com.gelox.backend.repositories.ProductoRepository;
import com.gelox.backend.voz.ResolvedorProducto;
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

/**
 * RF46 — el resolvedor empareja lo dicho a viva voz con el catálogo activo
 * (ProductoRepository mockeado, sin base de datos).
 */
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
    @DisplayName("\"festival\" resuelve a Festival con puntaje 1.0")
    void coincidenciaExacta() {
        when(productoRepository.findByActivoTrue()).thenReturn(List.of(festival, soloLack));

        List<ResolvedorProducto.ProductoCandidato> candidatos = resolvedor.resolver("festival");

        assertThat(candidatos).hasSize(1);
        assertThat(candidatos.get(0).nombre()).isEqualTo("Festival");
        assertThat(candidatos.get(0).id()).isEqualTo(festival.getId());
        assertThat(candidatos.get(0).unidadesPorCaja()).isEqualTo(24);
        assertThat(candidatos.get(0).score()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("error de escritura \"solo lac\" resuelve a Solo Lack")
    void errorDeEscrituraSoloLac() {
        when(productoRepository.findByActivoTrue()).thenReturn(List.of(festival, soloLack));

        List<ResolvedorProducto.ProductoCandidato> candidatos = resolvedor.resolver("solo lac");

        assertThat(candidatos).hasSize(1);
        assertThat(candidatos.get(0).nombre()).isEqualTo("Solo Lack");
        assertThat(candidatos.get(0).score()).isGreaterThanOrEqualTo(0.75);
    }

    @Test
    @DisplayName("letra cambiada (\"solo lak\", no contenida en el nombre) resuelve por Levenshtein")
    void coincidenciaDifusaPorLevenshtein() {
        when(productoRepository.findByActivoTrue()).thenReturn(List.of(festival, soloLack));

        List<ResolvedorProducto.ProductoCandidato> candidatos = resolvedor.resolver("solo lak");

        assertThat(candidatos).hasSize(1);
        assertThat(candidatos.get(0).nombre()).isEqualTo("Solo Lack");
        assertThat(candidatos.get(0).score()).isGreaterThanOrEqualTo(0.75).isLessThan(1.0);
    }

    @Test
    @DisplayName("caso ambiguo: dos candidatos a menos de 0.10 de diferencia")
    void candidatosAmbiguos() {
        Producto festivalMini = producto("Festival Mini", 24);
        when(productoRepository.findByActivoTrue()).thenReturn(List.of(festival, festivalMini));

        List<ResolvedorProducto.ProductoCandidato> candidatos = resolvedor.resolver("festival");

        assertThat(candidatos).hasSize(2);
        assertThat(candidatos.get(0).score() - candidatos.get(1).score()).isLessThan(0.10);
    }

    @Test
    @DisplayName("nombre parcial que cubre dos sabores (\"aloha paleta\") devuelve ambos")
    void variosSaboresDelMismoNombre() {
        when(productoRepository.findByActivoTrue()).thenReturn(List.of(
                producto("Aloha Paleta Fresa CM SP", 12), producto("Aloha Paleta Limon CM", 12), soloLack));

        List<ResolvedorProducto.ProductoCandidato> candidatos = resolvedor.resolver("aloha paleta");

        assertThat(candidatos).extracting(ResolvedorProducto.ProductoCandidato::nombre)
                .containsExactlyInAnyOrder("Aloha Paleta Fresa CM SP", "Aloha Paleta Limon CM");
    }

    @Test
    @DisplayName("producto inexistente devuelve lista vacía")
    void sinCoincidencia() {
        when(productoRepository.findByActivoTrue()).thenReturn(List.of(festival, soloLack));

        assertThat(resolvedor.resolver("panqueque")).isEmpty();
    }

    @Test
    @DisplayName("fragmento vacío devuelve lista vacía sin consultar el catálogo")
    void fragmentoVacio() {
        assertThat(resolvedor.resolver("  ")).isEmpty();
    }

    private static Producto producto(String nombre, int unidadesPorCaja) {
        Producto p = new Producto();
        p.setId(UUID.randomUUID());
        p.setNombre(nombre);
        p.setUnidadesPorCaja(unidadesPorCaja);
        return p;
    }
}
