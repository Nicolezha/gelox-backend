package com.gelox.backend.services;

import com.gelox.backend.dto.IdentidadDTO;
import com.gelox.backend.entities.Distribuidora;
import com.gelox.backend.repositories.DistribuidoraRepository;
import com.gelox.backend.security.RequiereRol;
import com.gelox.backend.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * RF60 — Identidad visual de la distribuidora de la petición (nombre, logo, favicon y colores).
 */
@Service
@RequiredArgsConstructor
public class IdentidadDistribuidoraService {

    private static final Pattern COLOR_HEX = Pattern.compile("^#[0-9A-Fa-f]{6}$");
    private static final int MAX_NOMBRE_COMERCIAL = 100;
    private static final long MAX_BYTES_IMAGEN = 2L * 1024 * 1024;
    private static final Set<String> MIME_PERMITIDOS = Set.of(
            "image/png", "image/jpeg", "image/jpg", "image/svg+xml"
    );

    private final DistribuidoraRepository distribuidoraRepository;
    private final SupabaseStorageService storageService;

    /** Cualquier usuario autenticado de la distribuidora: el frontend la usa para pintar la interfaz. */
    public IdentidadDTO obtenerIdentidad() {
        return IdentidadDTO.from(distribuidoraActual());
    }

    /**
     * Cambia nombre y colores; logo y favicon son opcionales (si no llegan se conservan).
     * Todo se valida antes de subir archivos, para no dejar imágenes huérfanas en el storage.
     * Sin @Transactional: la subida a Supabase no debe retener una conexión de la base.
     */
    @RequiereRol("ADMINISTRADOR")
    public IdentidadDTO actualizarIdentidad(String nombreComercial, String colorPrimario,
                                            String colorSecundario, MultipartFile logo,
                                            MultipartFile favicon) {
        String nombre = nombreComercial == null ? "" : nombreComercial.trim();
        if (nombre.isEmpty() || nombre.length() > MAX_NOMBRE_COMERCIAL) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "El nombre comercial es obligatorio y admite hasta " + MAX_NOMBRE_COMERCIAL + " caracteres.");
        }
        validarColor(colorPrimario, "primario");
        validarColor(colorSecundario, "secundario");

        boolean hayLogo = logo != null && !logo.isEmpty();
        boolean hayFavicon = favicon != null && !favicon.isEmpty();
        if (hayLogo) validarImagen(logo, "logo");
        if (hayFavicon) validarImagen(favicon, "favicon");

        Distribuidora distribuidora = distribuidoraActual();
        String carpeta = "distribuidoras/" + distribuidora.getId();
        if (hayLogo) {
            distribuidora.setLogoUrl(storageService.subirImagen(logo, carpeta, MIME_PERMITIDOS));
        }
        if (hayFavicon) {
            distribuidora.setFaviconUrl(storageService.subirImagen(favicon, carpeta, MIME_PERMITIDOS));
        }
        distribuidora.setNombreComercial(nombre);
        distribuidora.setColorPrimario(colorPrimario);
        distribuidora.setColorSecundario(colorSecundario);

        return IdentidadDTO.from(distribuidoraRepository.save(distribuidora));
    }

    private Distribuidora distribuidoraActual() {
        UUID tenantId = TenantContext.requerido();
        return distribuidoraRepository.findById(tenantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Distribuidora no encontrada"));
    }

    private void validarColor(String color, String cual) {
        if (color == null || !COLOR_HEX.matcher(color).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "El color " + cual + " debe tener el formato #RRGGBB.");
        }
    }

    private void validarImagen(MultipartFile archivo, String campo) {
        String mime = archivo.getContentType();
        if (mime == null || !MIME_PERMITIDOS.contains(mime.toLowerCase())) {
            throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                    "El " + campo + " debe ser PNG, JPG o SVG.");
        }
        if (archivo.getSize() > MAX_BYTES_IMAGEN) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE,
                    "El " + campo + " no puede superar los 2 MB.");
        }
    }
}
