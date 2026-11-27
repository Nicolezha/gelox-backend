package com.gelox.backend.services;

import com.gelox.backend.gobierno.CuotaAlcanzadaException;
import com.gelox.backend.gobierno.TipoCuota;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/** RF70 — la imagen de un guardado rechazado no queda huérfana. */
class ImagenSubidaTest {

    private final SupabaseStorageService storage = mock(SupabaseStorageService.class);

    @Test
    @DisplayName("si el guardado funciona, la imagen se conserva")
    void guardadoCorrecto() {
        assertThat(ImagenSubida.usar(storage, "url", () -> "ok")).isEqualTo("ok");
        verifyNoInteractions(storage);
    }

    @Test
    @DisplayName("cuota alcanzada: borra la imagen y deja pasar el mismo error")
    void cuotaAlcanzada() {
        CuotaAlcanzadaException cuota = new CuotaAlcanzadaException(TipoCuota.PRODUCTOS, 5, 5L);

        assertThatThrownBy(() -> ImagenSubida.usar(storage, "url", () -> { throw cuota; })).isSameAs(cuota);
        verify(storage).eliminarImagen("url");
    }

    @Test
    @DisplayName("dato repetido (IllegalArgumentException): borra la imagen")
    void datoRepetido() {
        assertThatThrownBy(() -> ImagenSubida.usar(storage, "url", () -> {
            throw new IllegalArgumentException("Ya existe un usuario con ese correo.");
        })).isInstanceOf(IllegalArgumentException.class);
        verify(storage).eliminarImagen("url");
    }

    @Test
    @DisplayName("error inesperado: la imagen se conserva (el registro pudo haberse guardado)")
    void errorInesperado() {
        assertThatThrownBy(() -> ImagenSubida.usar(storage, "url", () -> {
            throw new IllegalStateException("conexión perdida");
        })).isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(storage);
    }

    @Test
    @DisplayName("sin imagen subida no se borra nada")
    void sinImagen() {
        assertThatThrownBy(() -> ImagenSubida.usar(storage, null, () -> {
            throw new IllegalArgumentException("x");
        })).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(storage);
    }
}
