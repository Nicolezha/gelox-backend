# Instrucciones: Backend RF05 y RF06 — Ajustes de Perfil y Cambio de Contraseña

## Contexto del proyecto

- **Framework:** Spring Boot (Java)
- **BD:** PostgreSQL en Supabase
- **Auth:** Firebase Authentication + Firebase Admin SDK
- **Paquetes existentes:** `auth`, `controllers`, `dto`, `entities`, `exceptions`, `repositories`, `services`

### Tabla `usuario` en BD (modelo lógico ya existente)

```sql
CREATE TABLE usuario (
    id           UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    firebase_uid VARCHAR(128) NOT NULL UNIQUE,
    nombre       VARCHAR(100) NOT NULL,
    correo       VARCHAR(150) NOT NULL UNIQUE,
    rol          rol_usuario  NOT NULL,
    foto_url     TEXT,
    activo       BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at   TIMESTAMP   NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMP   NOT NULL DEFAULT NOW()
);
```

> ⚠️ La tabla `usuario` **no tiene columna `telefono`**. Antes de implementar RF05 debes:
> 1. Verificar en Supabase si ya fue agregada con `SELECT column_name FROM information_schema.columns WHERE table_name = 'usuario';`
> 2. Si no existe, ejecutar: `ALTER TABLE usuario ADD COLUMN telefono VARCHAR(20);`
> 3. Agregar el campo `telefono` a la entidad `Usuario.java` si no está.

---

## Paso 1 — Migración de BD (si falta la columna telefono)

Ejecuta en Supabase SQL Editor:

```sql
ALTER TABLE usuario ADD COLUMN IF NOT EXISTS telefono VARCHAR(20);
```

---

## Paso 2 — Entidad `Usuario.java`

Verifica que la entidad tenga todos los campos. Si falta `telefono`, agrégalo:

```java
@Column(name = "telefono")
private String telefono;
// getter y setter correspondientes
```

---

## Paso 3 — Repositorio `UsuarioRepository.java`

Verifica que exista. Si no tiene `findByCorreo`, agrégalo:

```java
Optional<Usuario> findByCorreo(String correo);
Optional<Usuario> findByFirebaseUid(String firebaseUid);
```

---

## Paso 4 — DTO: `ActualizarPerfilDTO.java`

Crear en el paquete `dto`:

```java
package com.gelox.dto;

import jakarta.validation.constraints.*;

public class ActualizarPerfilDTO {

    @NotBlank(message = "El nombre no puede estar vacío")
    @Size(max = 100, message = "El nombre no puede superar 100 caracteres")
    private String nombre;

    @NotBlank(message = "El correo no puede estar vacío")
    @Email(message = "Formato de correo inválido")
    @Size(max = 150, message = "El correo no puede superar 150 caracteres")
    private String correo;

    @Pattern(regexp = "^[0-9+\\-\\s]{7,20}$", message = "Formato de teléfono inválido")
    private String telefono;

    private String fotoUrl;

    // Getters y setters
    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }

    public String getCorreo() { return correo; }
    public void setCorreo(String correo) { this.correo = correo; }

    public String getTelefono() { return telefono; }
    public void setTelefono(String telefono) { this.telefono = telefono; }

    public String getFotoUrl() { return fotoUrl; }
    public void setFotoUrl(String fotoUrl) { this.fotoUrl = fotoUrl; }
}
```

---

## Paso 5 — DTO: `CambioContrasenaDTO.java`

Crear en el paquete `dto`:

```java
package com.gelox.dto;

import jakarta.validation.constraints.*;

public class CambioContrasenaDTO {

    @NotBlank(message = "La contraseña actual no puede estar vacía")
    private String contrasenaActual;

    @NotBlank(message = "La nueva contraseña no puede estar vacía")
    @Size(min = 6, message = "La nueva contraseña debe tener al menos 6 caracteres")
    private String nuevaContrasena;

    @NotBlank(message = "La confirmación no puede estar vacía")
    private String confirmacion;

    // Getters y setters
    public String getContrasenaActual() { return contrasenaActual; }
    public void setContrasenaActual(String contrasenaActual) { this.contrasenaActual = contrasenaActual; }

    public String getNuevaContrasena() { return nuevaContrasena; }
    public void setNuevaContrasena(String nuevaContrasena) { this.nuevaContrasena = nuevaContrasena; }

    public String getConfirmacion() { return confirmacion; }
    public void setConfirmacion(String confirmacion) { this.confirmacion = confirmacion; }
}
```

---

## Paso 6 — Excepciones personalizadas

Verifica si ya existen clases de excepción. Si no, crea en el paquete `exceptions`:

**`CorreoDuplicadoException.java`**
```java
package com.gelox.exceptions;

public class CorreoDuplicadoException extends RuntimeException {
    public CorreoDuplicadoException(String message) {
        super(message);
    }
}
```

**`ContrasenaNoCoincideException.java`**
```java
package com.gelox.exceptions;

public class ContrasenaNoCoincideException extends RuntimeException {
    public ContrasenaNoCoincideException(String message) {
        super(message);
    }
}
```

Si ya existe un `GlobalExceptionHandler` o `@ControllerAdvice`, agrégale los handlers para estas excepciones devolviendo HTTP 400 con el mensaje correspondiente. Si no existe, créalo:

```java
package com.gelox.exceptions;

import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(CorreoDuplicadoException.class)
    public ResponseEntity<Map<String, String>> handleCorreoDuplicado(CorreoDuplicadoException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(ContrasenaNoCoincideException.class)
    public ResponseEntity<Map<String, String>> handleContrasenaNoCoincide(ContrasenaNoCoincideException ex) {
        return ResponseEntity.badRequest()
                .body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleIllegalArgument(IllegalArgumentException ex) {
        return ResponseEntity.badRequest()
                .body(Map.of("error", ex.getMessage()));
    }
}
```

---

## Paso 7 — `PerfilService.java`

Crear en el paquete `services`. Lee el código de `UsuarioRepository` y los imports de Firebase Admin SDK que ya usa el proyecto antes de escribir:

```java
package com.gelox.services;

import com.gelox.dto.ActualizarPerfilDTO;
import com.gelox.dto.CambioContrasenaDTO;
import com.gelox.entities.Usuario;
import com.gelox.exceptions.ContrasenaNoCoincideException;
import com.gelox.exceptions.CorreoDuplicadoException;
import com.gelox.repositories.UsuarioRepository;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.UserRecord;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class PerfilService {

    private final UsuarioRepository usuarioRepository;

    public PerfilService(UsuarioRepository usuarioRepository) {
        this.usuarioRepository = usuarioRepository;
    }

    @Transactional
    public Usuario actualizarPerfil(UUID userId, ActualizarPerfilDTO dto) {
        Usuario usuario = usuarioRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado"));

        // Validar correo duplicado solo si cambió
        if (!usuario.getCorreo().equalsIgnoreCase(dto.getCorreo())) {
            usuarioRepository.findByCorreo(dto.getCorreo()).ifPresent(otro -> {
                if (!otro.getId().equals(userId)) {
                    throw new CorreoDuplicadoException("El correo ya está registrado por otro usuario");
                }
            });
        }

        usuario.setNombre(dto.getNombre());
        usuario.setCorreo(dto.getCorreo());
        usuario.setTelefono(dto.getTelefono());

        if (dto.getFotoUrl() != null && !dto.getFotoUrl().isBlank()) {
            usuario.setFotoUrl(dto.getFotoUrl());
        }

        return usuarioRepository.save(usuario);
    }

    public void cambiarContrasena(String firebaseUid, CambioContrasenaDTO dto) {
        // Validar que nueva contraseña y confirmación coincidan
        if (!dto.getNuevaContrasena().equals(dto.getConfirmacion())) {
            throw new ContrasenaNoCoincideException("La nueva contraseña y su confirmación no coinciden");
        }

        try {
            // Verificar que el usuario existe en Firebase
            UserRecord userRecord = FirebaseAuth.getInstance().getUser(firebaseUid);

            // Actualizar contraseña en Firebase
            UserRecord.UpdateRequest updateRequest = new UserRecord.UpdateRequest(firebaseUid)
                    .setPassword(dto.getNuevaContrasena());

            FirebaseAuth.getInstance().updateUser(updateRequest);

        } catch (com.google.firebase.auth.FirebaseAuthException e) {
            throw new IllegalArgumentException("Error al actualizar la contraseña: " + e.getMessage());
        }
    }
}
```

> **Nota sobre verificación de contraseña actual:** Firebase Admin SDK **no permite verificar la contraseña actual** desde el backend directamente (es una restricción de seguridad de Firebase). La verificación de `contrasenaActual` debe hacerse desde el **frontend** usando `signInWithEmailAndPassword` antes de llamar a este endpoint, o bien recibir un `idToken` reciente del usuario como prueba de que conoce su contraseña actual. Documenta esto en el código con un comentario. Si el equipo decide verificarla en backend, deberán usar la Firebase REST API con la API key del proyecto.

---

## Paso 8 — Lógica de subida de foto a Supabase Storage

Agrega en `PerfilService.java` un método separado para subir la foto. Necesitas las variables de entorno `SUPABASE_URL` y `SUPABASE_SERVICE_KEY` (verifica cómo se inyectan en el proyecto, busca `@Value` o `application.properties`):

```java
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;

// En la clase PerfilService, agregar:

@Value("${supabase.url}")
private String supabaseUrl;

@Value("${supabase.service-key}")
private String supabaseServiceKey;

public String subirFotoPerfil(UUID userId, MultipartFile foto) {
    try {
        String nombreArchivo = "perfil_" + userId + "_" + System.currentTimeMillis()
                + getExtension(foto.getOriginalFilename());
        String uploadUrl = supabaseUrl + "/storage/v1/object/fotos-perfil/" + nombreArchivo;

        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + supabaseServiceKey);
        headers.setContentType(MediaType.parseMediaType(foto.getContentType()));

        HttpEntity<byte[]> requestEntity = new HttpEntity<>(foto.getBytes(), headers);
        restTemplate.exchange(uploadUrl, HttpMethod.POST, requestEntity, String.class);

        return supabaseUrl + "/storage/v1/object/public/fotos-perfil/" + nombreArchivo;

    } catch (Exception e) {
        throw new IllegalArgumentException("Error al subir la foto: " + e.getMessage());
    }
}

private String getExtension(String filename) {
    if (filename == null || !filename.contains(".")) return ".jpg";
    return filename.substring(filename.lastIndexOf("."));
}
```

> **Requisito:** Verifica que el bucket `fotos-perfil` exista en Supabase Storage y tenga política de acceso público. Si no existe, créalo desde el dashboard de Supabase o con la API.

---

## Paso 9 — `PerfilController.java`

Crear en el paquete `controllers`. Antes de escribir, lee cómo los otros controllers manejan la autenticación (anotación de seguridad, extracción del usuario del token):

```java
package com.gelox.controllers;

import com.gelox.dto.ActualizarPerfilDTO;
import com.gelox.dto.CambioContrasenaDTO;
import com.gelox.entities.Usuario;
import com.gelox.services.PerfilService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/perfil")
public class PerfilController {

    private final PerfilService perfilService;

    public PerfilController(PerfilService perfilService) {
        this.perfilService = perfilService;
    }

    /**
     * PUT /api/perfil/{userId}
     * Actualiza nombre, correo, teléfono y opcionalmente fotoUrl del usuario.
     * Requiere autenticación (Firebase ID Token en header Authorization).
     */
    @PutMapping("/{userId}")
    public ResponseEntity<Usuario> actualizarPerfil(
            @PathVariable UUID userId,
            @Valid @RequestBody ActualizarPerfilDTO dto) {
        Usuario actualizado = perfilService.actualizarPerfil(userId, dto);
        return ResponseEntity.ok(actualizado);
    }

    /**
     * POST /api/perfil/{userId}/foto
     * Sube foto de perfil a Supabase Storage y actualiza foto_url en BD.
     * Requiere autenticación.
     */
    @PostMapping("/{userId}/foto")
    public ResponseEntity<Map<String, String>> subirFoto(
            @PathVariable UUID userId,
            @RequestParam("foto") MultipartFile foto) {
        String fotoUrl = perfilService.subirFotoPerfil(userId, foto);

        // Actualizar foto_url en BD reutilizando actualizarPerfil con solo ese campo
        perfilService.actualizarFotoUrl(userId, fotoUrl);

        return ResponseEntity.ok(Map.of("fotoUrl", fotoUrl));
    }

    /**
     * PUT /api/perfil/{firebaseUid}/contrasena
     * Cambia la contraseña del usuario en Firebase Auth.
     * Requiere autenticación.
     */
    @PutMapping("/{firebaseUid}/contrasena")
    public ResponseEntity<Map<String, String>> cambiarContrasena(
            @PathVariable String firebaseUid,
            @Valid @RequestBody CambioContrasenaDTO dto) {
        perfilService.cambiarContrasena(firebaseUid, dto);
        return ResponseEntity.ok(Map.of("mensaje", "Contraseña actualizada correctamente"));
    }
}
```

Agrega en `PerfilService` el método auxiliar `actualizarFotoUrl`:

```java
@Transactional
public void actualizarFotoUrl(UUID userId, String fotoUrl) {
    Usuario usuario = usuarioRepository.findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado"));
    usuario.setFotoUrl(fotoUrl);
    usuarioRepository.save(usuario);
}
```

---

## Paso 10 — Verificaciones finales

1. **`application.properties` / `application.yml`:** Confirma que `supabase.url` y `supabase.service-key` están definidos. Si no, agrégalos (usa variables de entorno, no hardcodees la clave).

2. **Seguridad:** Verifica que el filtro de autenticación existente (`auth` package) proteja las rutas `/api/perfil/**`. Si usa un `SecurityFilterChain`, agrégalas a las rutas protegidas.

3. **Multipart:** Confirma que `spring.servlet.multipart.enabled=true` esté en `application.properties` (generalmente está por defecto en Spring Boot).

4. **Compilación:** Corre `./mvnw compile` (o `./gradlew compileJava`) y verifica que no haya errores de importación o de tipos antes de hacer commit.

5. **Prueba rápida:** Con la app corriendo, prueba con curl o Postman:
   - `PUT /api/perfil/{uuid}` con body JSON válido → espera 200
   - `PUT /api/perfil/{uuid}` con correo de otro usuario → espera 409
   - `PUT /api/perfil/{firebaseUid}/contrasena` con confirmación incorrecta → espera 400

---

## Resumen de archivos a crear/modificar

| Acción | Archivo |
|--------|---------|
| CREAR | `dto/ActualizarPerfilDTO.java` |
| CREAR | `dto/CambioContrasenaDTO.java` |
| CREAR | `exceptions/CorreoDuplicadoException.java` |
| CREAR | `exceptions/ContrasenaNoCoincideException.java` |
| CREAR o MODIFICAR | `exceptions/GlobalExceptionHandler.java` |
| CREAR | `services/PerfilService.java` |
| CREAR | `controllers/PerfilController.java` |
| MODIFICAR | `entities/Usuario.java` (agregar campo `telefono` si no existe) |
| MODIFICAR | `repositories/UsuarioRepository.java` (agregar `findByCorreo` si no existe) |
| MODIFICAR | `application.properties` (agregar `supabase.url` y `supabase.service-key` si no existen) |
| EJECUTAR EN SUPABASE | `ALTER TABLE usuario ADD COLUMN IF NOT EXISTS telefono VARCHAR(20);` |
