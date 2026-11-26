package com.gelox.backend.services;

import com.google.firebase.auth.FirebaseAuthException;
import org.springframework.web.server.ResponseStatusException;

/**
 * RF70 — La imagen se sube antes de guardar el registro que la usa. Si el guardado se rechaza
 * (cuota, dato repetido, recurso inexistente, permiso o validación) la imagen se borra, para que no
 * quede huérfana ni ocupe la cuota de almacenamiento.
 * <p>
 * Ante un error inesperado se conserva: podría haber ocurrido después de guardar el registro.
 */
public final class ImagenSubida {

    private ImagenSubida() {
    }

    @FunctionalInterface
    public interface Operacion<T, E extends Exception> {
        T ejecutar() throws E;
    }

    /** Ejecuta la operación que usa la imagen {@code url} (null = no se subió ninguna). */
    public static <T, E extends Exception> T usar(SupabaseStorageService storage, String url,
                                                  Operacion<T, E> operacion) throws E {
        try {
            return operacion.ejecutar();
        } catch (Exception e) {
            if (url != null && esRechazo(e)) {
                storage.eliminarImagen(url);
            }
            throw e;
        }
    }

    private static boolean esRechazo(Exception e) {
        return e instanceof ResponseStatusException
                || e instanceof IllegalArgumentException
                || e instanceof FirebaseAuthException;
    }
}
