package com.gelox.backend.plataforma;

import com.gelox.backend.dto.ActualizarIdentidadRequest;
import com.gelox.backend.dto.IdentidadDTO;
import com.gelox.backend.entities.Distribuidora;
import com.gelox.backend.entities.TipoEvento;
import com.gelox.backend.entities.Usuario;
import com.gelox.backend.repositories.DistribuidoraRepository;
import com.gelox.backend.security.RequiereRol;
import com.gelox.backend.services.EventoSistemaService;
import com.gelox.backend.services.SupabaseStorageService;
import com.gelox.backend.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;
import java.util.Map;
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
    private static final long MAX_BYTES_LOGO = 2L * 1024 * 1024;
    private static final long MAX_BYTES_FAVICON = 512L * 1024;

    /** Tipo MIME permitido → extensión con la que se guarda el archivo. */
    private static final Map<String, String> TIPOS_LOGO = Map.of(
            "image/png", "png",
            "image/jpeg", "jpg",
            "image/jpg", "jpg",
            "image/svg+xml", "svg"
    );
    private static final Map<String, String> TIPOS_FAVICON = Map.of(
            "image/png", "png",
            "image/x-icon", "ico",
            "image/vnd.microsoft.icon", "ico"
    );

    private final DistribuidoraRepository distribuidoraRepository;
    private final SupabaseStorageService storageService;
    private final EventoSistemaService eventoSistemaService;

    /** Cualquier usuario autenticado de la distribuidora: el frontend la usa para pintar la interfaz. */
    public IdentidadDTO obtener() {
        return IdentidadDTO.from(distribuidoraActual());
    }

    /**
     * Cambia nombre y colores; logo y favicon son opcionales (si no llegan se conservan).
     * Todo se valida antes de subir archivos, para no dejar imágenes huérfanas en el storage.
     * Sin @Transactional: la subida a Supabase no debe retener una conexión de la base.
     */
    @RequiereRol("ADMINISTRADOR")
    public IdentidadDTO actualizar(ActualizarIdentidadRequest datos, MultipartFile logo, MultipartFile favicon) {
        String nombre = datos == null || datos.nombreComercial() == null ? "" : datos.nombreComercial().trim();
        if (nombre.isEmpty() || nombre.length() > MAX_NOMBRE_COMERCIAL) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "El nombre comercial es obligatorio y admite hasta " + MAX_NOMBRE_COMERCIAL + " caracteres.");
        }
        validarColor(datos.colorPrimario(), "primario");
        validarColor(datos.colorSecundario(), "secundario");

        boolean hayLogo = logo != null && !logo.isEmpty();
        boolean hayFavicon = favicon != null && !favicon.isEmpty();
        String extLogo = hayLogo
                ? validarImagen(logo, TIPOS_LOGO, MAX_BYTES_LOGO, "El logo debe ser PNG, JPG o SVG de máximo 2 MB.")
                : null;
        String extFavicon = hayFavicon
                ? validarImagen(favicon, TIPOS_FAVICON, MAX_BYTES_FAVICON, "El favicon debe ser PNG o ICO de máximo 512 KB.")
                : null;

        Distribuidora distribuidora = distribuidoraActual();
        String carpeta = "distribuidoras/" + distribuidora.getId();
        long marcaDeTiempo = System.currentTimeMillis();
        if (hayLogo) {
            distribuidora.setLogoUrl(storageService.subirImagen(logo, carpeta, TIPOS_LOGO.keySet(),
                    "logo-" + marcaDeTiempo + "." + extLogo));
        }
        if (hayFavicon) {
            distribuidora.setFaviconUrl(storageService.subirImagen(favicon, carpeta, TIPOS_FAVICON.keySet(),
                    "favicon-" + marcaDeTiempo + "." + extFavicon));
        }
        distribuidora.setNombreComercial(nombre);
        distribuidora.setColorPrimario(datos.colorPrimario());
        distribuidora.setColorSecundario(datos.colorSecundario());

        Distribuidora guardada = distribuidoraRepository.save(distribuidora);
        eventoSistemaService.registrarEvento(TipoEvento.ACTUALIZACION_IDENTIDAD,
                "Identidad visual actualizada: " + guardada.getNombreComercial(), usuarioActualId());
        return IdentidadDTO.from(guardada);
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

    /** Valida tipo, tamaño y (si es SVG) contenido; devuelve la extensión con la que se guarda. */
    private String validarImagen(MultipartFile archivo, Map<String, String> tipos, long maxBytes, String mensaje) {
        String mime = archivo.getContentType() == null ? "" : archivo.getContentType().toLowerCase();
        String extension = tipos.get(mime);
        if (extension == null || archivo.getSize() > maxBytes) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, mensaje);
        }
        if ("svg".equals(extension)) {
            validarSvg(archivo);
        }
        return extension;
    }

    /** Un SVG se sirve tal cual desde el storage: no puede traer elementos script ni atributos on*. */
    private void validarSvg(MultipartFile archivo) {
        Element raiz;
        try (InputStream entrada = archivo.getInputStream()) {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            raiz = factory.newDocumentBuilder().parse(entrada).getDocumentElement();
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El logo SVG no es un archivo válido.");
        }
        if (contieneCodigo(raiz)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "El logo SVG no puede contener scripts ni atributos de eventos (on*).");
        }
    }

    private boolean contieneCodigo(Element elemento) {
        if ("script".equalsIgnoreCase(nombreLocal(elemento))) {
            return true;
        }
        NamedNodeMap atributos = elemento.getAttributes();
        for (int i = 0; i < atributos.getLength(); i++) {
            if (nombreLocal(atributos.item(i)).toLowerCase().startsWith("on")) {
                return true;
            }
        }
        NodeList hijos = elemento.getChildNodes();
        for (int i = 0; i < hijos.getLength(); i++) {
            if (hijos.item(i) instanceof Element hijo && contieneCodigo(hijo)) {
                return true;
            }
        }
        return false;
    }

    private static String nombreLocal(Node nodo) {
        return nodo.getLocalName() != null ? nodo.getLocalName() : nodo.getNodeName();
    }

    private static UUID usuarioActualId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getPrincipal() instanceof Usuario usuario ? usuario.getId() : null;
    }
}
