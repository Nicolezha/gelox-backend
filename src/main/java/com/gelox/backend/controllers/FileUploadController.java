package com.gelox.backend.controllers;

import com.gelox.backend.services.SupabaseStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/**
 * Endpoint dedicado para subir archivos a Supabase Storage.
 *
 * POST /api/upload?carpeta=usuarios   (o productos, comerciantes)
 * Content-Type: multipart/form-data
 * Body:  archivo (form-field "archivo")
 *
 * Response: { "url": "https://..." }
 */
@RestController
@RequestMapping("/api/upload")
@RequiredArgsConstructor
public class FileUploadController {

    private final SupabaseStorageService storageService;

    @PostMapping(value = "", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, String>> subir(
            @RequestParam("archivo") MultipartFile archivo,
            @RequestParam(defaultValue = "general") String carpeta) {

        String url = storageService.subirImagen(archivo, carpeta);
        return ResponseEntity.ok(Map.of("url", url));
    }
}