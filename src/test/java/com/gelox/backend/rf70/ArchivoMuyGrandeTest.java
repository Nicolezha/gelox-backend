package com.gelox.backend.rf70;

import com.gelox.backend.exceptions.GlobalExceptionHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CP-57 / CP-70-06b — un archivo que pasa el límite por archivo (spring.servlet.multipart.max-file-size)
 * se rechaza antes de revisar la cuota ALMACENAMIENTO_MB. Debe responder 400 con un mensaje claro,
 * no 500 por el manejador genérico.
 */
class ArchivoMuyGrandeTest {

    @Test
    @DisplayName("CP-57: archivo de más de 5 MB responde 400 ARCHIVO_MUY_GRANDE, no 500")
    void archivoMuyGrandeEs400() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();

        ResponseEntity<Map<String, String>> respuesta =
                handler.handleArchivoMuyGrande(new MaxUploadSizeExceededException(5L * 1024 * 1024));

        assertThat(respuesta.getStatusCode().value()).isEqualTo(400);
        assertThat(respuesta.getBody()).containsEntry("codigo", "ARCHIVO_MUY_GRANDE");
        assertThat(respuesta.getBody().get("error")).contains("5 MB").contains("por archivo");
    }
}
