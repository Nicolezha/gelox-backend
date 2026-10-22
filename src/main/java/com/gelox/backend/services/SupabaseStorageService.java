package com.gelox.backend.services;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Set;
import java.util.UUID;

/**
 * Sube archivos a Supabase Storage y devuelve la URL pública.
 *
 * Configuración requerida en application.properties:
 *   supabase.url       — ej. https://xyz.supabase.co
 *   supabase.service-key — service_role key de Supabase
 *   supabase.bucket    — nombre del bucket (ej. "fotos")
 */
@Service
public class SupabaseStorageService {

    private static final Set<String> MIME_PERMITIDOS = Set.of(
            "image/jpeg", "image/jpg", "image/png", "image/webp", "image/gif"
    );

    private final String supabaseUrl;
    private final String serviceKey;
    private final String bucket;
    private final HttpClient httpClient;

    public SupabaseStorageService(
            @Value("${supabase.url}")         String supabaseUrl,
            @Value("${supabase.service-key}") String serviceKey,
            @Value("${supabase.bucket:fotos}") String bucket) {
        this.supabaseUrl = supabaseUrl;
        this.serviceKey  = serviceKey;
        this.bucket      = bucket;
        this.httpClient  = HttpClient.newHttpClient();
    }

    /**
     * Sube un archivo de imagen a Supabase Storage.
     *
     * @param archivo    archivo recibido del frontend
     * @param carpeta    subcarpeta dentro del bucket (ej. "usuarios", "productos")
     * @return URL pública del archivo subido
     */
    public String subirImagen(MultipartFile archivo, String carpeta) {
        return subirImagen(archivo, carpeta, MIME_PERMITIDOS);
    }

    /**
     * Igual que {@link #subirImagen(MultipartFile, String)} pero con otros tipos permitidos
     * (la identidad de la distribuidora admite SVG). El llamador valida su propio límite de tamaño.
     */
    public String subirImagen(MultipartFile archivo, String carpeta, Set<String> mimePermitidos) {
        return subirImagen(archivo, carpeta, mimePermitidos,
                UUID.randomUUID() + "." + obtenerExtension(archivo != null ? archivo.getOriginalFilename() : null));
    }

    /**
     * Igual que {@link #subirImagen(MultipartFile, String, Set)} pero con el nombre del archivo
     * fijado por el llamador (ej. "logo-1729630000000.png").
     */
    public String subirImagen(MultipartFile archivo, String carpeta, Set<String> mimePermitidos,
                              String nombreArchivo) {
        validarArchivo(archivo, mimePermitidos);

        String nombreObjeto = carpeta + "/" + nombreArchivo;
        String uploadUrl    = supabaseUrl + "/storage/v1/object/" + bucket + "/" + nombreObjeto;

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(uploadUrl))
                    .header("Authorization", "Bearer " + serviceKey)
                    .header("Content-Type", archivo.getContentType())
                    .header("x-upsert", "true")
                    .POST(HttpRequest.BodyPublishers.ofByteArray(archivo.getBytes()))
                    .build();

            HttpResponse<String> response = httpClient.send(
                    request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200 && response.statusCode() != 201) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                        "Error al subir imagen a Supabase Storage: " + response.body());
            }

        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "No se pudo conectar con Supabase Storage: " + e.getMessage());
        }

        return supabaseUrl + "/storage/v1/object/public/" + bucket + "/" + nombreObjeto;
    }

    // ──────────────────────────────────────────────────────────────────────────

    private void validarArchivo(MultipartFile archivo, Set<String> mimePermitidos) {
        if (archivo == null || archivo.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "El archivo está vacío.");
        }
        String mime = archivo.getContentType();
        if (mime == null || !mimePermitidos.contains(mime.toLowerCase())) {
            throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                    mimePermitidos == MIME_PERMITIDOS
                            ? "Solo se permiten imágenes JPEG, PNG, WebP o GIF."
                            : "Tipo de imagen no permitido.");
        }
        if (archivo.getSize() > 5 * 1024 * 1024) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE,
                    "La imagen no puede superar los 5 MB.");
        }
    }

    private String obtenerExtension(String nombreArchivo) {
        if (nombreArchivo != null && nombreArchivo.contains(".")) {
            return nombreArchivo.substring(nombreArchivo.lastIndexOf('.') + 1).toLowerCase();
        }
        return "jpg";
    }
}