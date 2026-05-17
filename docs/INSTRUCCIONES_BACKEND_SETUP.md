# GELOX Backend — Setup inicial

Crea toda la estructura del proyecto Spring Boot para el backend de GELOX.
El humano ya tiene el repositorio de GitHub creado y va a hacer el commit/push manualmente.
Tu tarea es generar todos los archivos en el directorio actual.

---

## Contexto del sistema

- **Framework:** Spring Boot 3.x con Java 21
- **Base de datos:** PostgreSQL en Supabase
- **Autenticación:** Firebase Authentication (el backend valida el ID Token con Firebase Admin SDK)
- **Arquitectura:** Tres capas — presentación (React, separado), lógica (este backend), datos (PostgreSQL)

---

## Tarea

Genera el proyecto Spring Boot completo con la siguiente estructura y archivos:

### 1. `pom.xml`

Proyecto Maven con:
- `spring-boot-starter-web`
- `spring-boot-starter-data-jpa`
- `spring-boot-starter-security`
- `postgresql` (driver)
- `firebase-admin` versión `9.3.0`
- `lombok`
- `spring-boot-starter-validation`
- Java 21, Spring Boot 3.3.x

### 2. Estructura de paquetes

Raíz del paquete base: `com.gelox.backend`

```
src/main/java/com/gelox/backend/
├── GeloxBackendApplication.java
├── auth/
│   └── (vacío por ahora, solo package-info.java)
├── controllers/
│   └── (vacío por ahora, solo package-info.java)
├── dto/
│   └── (vacío por ahora, solo package-info.java)
├── entities/
│   ├── RolUsuario.java
│   └── Usuario.java
├── exceptions/
│   └── (vacío por ahora, solo package-info.java)
├── repositories/
│   └── UsuarioRepository.java
└── services/
    └── (vacío por ahora, solo package-info.java)
```

### 3. `src/main/resources/application.properties`

```properties
spring.application.name=gelox-backend

# PostgreSQL - Supabase
# REEMPLAZAR con los valores reales de Supabase
spring.datasource.url=jdbc:postgresql://TU_HOST_SUPABASE:5432/postgres
spring.datasource.username=postgres
spring.datasource.password=TU_PASSWORD_SUPABASE
spring.datasource.driver-class-name=org.postgresql.Driver

# JPA / Hibernate
spring.jpa.hibernate.ddl-auto=validate
spring.jpa.show-sql=false
spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect
spring.jpa.properties.hibernate.format_sql=true

# Firebase
# Ruta al archivo serviceAccountKey.json (relativa al classpath o absoluta)
firebase.service-account-path=classpath:serviceAccountKey.json

# Puerto
server.port=8080
```

> Nota: crear también `src/main/resources/serviceAccountKey.json.example` con la estructura vacía del JSON de Firebase para que el equipo sepa qué archivo colocar ahí. El archivo real `serviceAccountKey.json` debe estar en `.gitignore`.

### 4. `.gitignore`

Incluir las exclusiones estándar de Maven/Spring Boot más:
- `serviceAccountKey.json`
- `application-local.properties`
- archivos de IDE (`.idea/`, `*.iml`, `.vscode/`)

### 5. Archivos Java a generar

#### `GeloxBackendApplication.java`
Clase principal con `@SpringBootApplication`.

#### `entities/RolUsuario.java`
Enum con exactamente estos tres valores:
```
ADMINISTRADOR
ENCARGADO_INVENTARIO
ENCARGADO_VENTAS
```

#### `entities/Usuario.java`
Entidad JPA mapeada a la tabla `usuario` en PostgreSQL. Campos:

| Campo | Tipo Java | Columna SQL | Notas |
|---|---|---|---|
| id | UUID | id | PK, generado |
| firebaseUid | String | firebase_uid | NOT NULL, UNIQUE, max 128 |
| nombre | String | nombre | NOT NULL, max 100 |
| correo | String | correo | NOT NULL, UNIQUE, max 150 |
| rol | RolUsuario (enum) | rol | NOT NULL, mapeado como `@Enumerated(EnumType.STRING)` |
| fotoUrl | String | foto_url | nullable |
| activo | Boolean | activo | NOT NULL, default true |
| createdAt | LocalDateTime | created_at | NOT NULL, no updatable |
| updatedAt | LocalDateTime | updated_at | NOT NULL |

Usar Lombok `@Data`, `@Builder`, `@NoArgsConstructor`, `@AllArgsConstructor`.
Usar `@PrePersist` y `@PreUpdate` para manejar `createdAt` y `updatedAt`.
El enum `rol` debe persistirse como String en la BD.

#### `repositories/UsuarioRepository.java`
Interfaz que extiende `JpaRepository<Usuario, UUID>` con:
- `Optional<Usuario> findByFirebaseUid(String firebaseUid)`
- `Optional<Usuario> findByCorreo(String correo)`
- `boolean existsByCorreo(String correo)`

#### `package-info.java` en cada paquete vacío
Solo con la declaración del paquete, para que los directorios existan en el repositorio.

---

## Lo que NO debes hacer

- No crear ningún controlador todavía.
- No configurar Spring Security todavía (se hará en la siguiente tarea).
- No crear ningún servicio todavía.
- No tocar la base de datos (el DDL ya existe en Supabase).

---

## Verificación final

Después de generar todos los archivos, ejecuta:
```bash
./mvnw compile
```
Debe compilar sin errores. Si hay errores de dependencias de Firebase Admin SDK o de conexión a la BD, es esperado en este punto porque `application.properties` tiene placeholders. Lo importante es que no haya errores de compilación Java.
