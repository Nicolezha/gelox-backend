# Instrucciones: Casos de Prueba RF01–RF06 — GELOX

## Contexto del sistema
- **Frontend:** React
- **Backend:** Spring Boot (Java)
- **Autenticación:** Firebase Authentication — el frontend obtiene un Firebase ID Token con `getIdToken()` y lo envía en cada request: `Authorization: Bearer {idToken}`
- **Base de datos:** PostgreSQL en Supabase
- **Roles:** `ADMINISTRADOR`, `ENCARGADO_INVENTARIO`, `ENCARGADO_VENTAS`
- **Tabla clave:** `usuario` (campos: `id`, `firebase_uid`, `nombre`, `correo`, `rol`, `foto_url`, `activo`, `created_at`, `updated_at`)

---

## Tu tarea

Implementar los casos de prueba CP01–CP18 como tests automatizados. Usa el framework de testing que ya esté configurado en el proyecto (detecta si hay Jest/Vitest en el frontend y JUnit/Spring Boot Test en el backend). Si no hay nada configurado, pregunta antes de instalar dependencias.

**Antes de escribir cualquier test:**
1. Lee la estructura de carpetas del repo (frontend y backend).
2. Identifica dónde viven los tests existentes y sigue esa convención.
3. Conéctate a Supabase via MCP para consultar la tabla `usuario` y obtener datos reales de prueba (correos, UIDs, roles).

---

## Casos de prueba a implementar

### RF01 — Login

**CP01 — login-exitoso**
- Precondición: usuario activo con rol `ENCARGADO_INVENTARIO` existe en Firebase y en tabla `usuario`.
- Acción: POST al endpoint de login (o flujo Firebase SDK) con correo y contraseña correctos.
- Resultado esperado: HTTP 200, se recibe un Firebase ID Token válido, el frontend redirige al dashboard correspondiente al rol.
- Verificar: el token decodificado contiene el `firebase_uid` del usuario; el rol devuelto por el backend coincide con el de la tabla `usuario`.

**CP02 — login-rol-gerente**
- Igual que CP01 pero el usuario tiene rol `ADMINISTRADOR`.
- Resultado esperado: redirección al dashboard de Gerente (ruta diferente a la de roles operativos).
- Verificar: la ruta final del frontend corresponde al dashboard de administrador.

**CP03 — login-credenciales-invalidas**
- Acción: POST con contraseña incorrecta.
- Resultado esperado: Firebase devuelve error de credenciales inválidas; el backend (si recibe un token inválido) responde HTTP 401.
- Verificar: no se almacena sesión; el usuario permanece en la vista de login.

**CP04 — login-usuario-deshabilitado**
- Precondición: usuario con `activo = false` en tabla `usuario` (Firebase puede tenerlo activo).
- Acción: login con credenciales correctas de ese usuario.
- Resultado esperado: el backend, al verificar el token, consulta `usuario.activo` y responde HTTP 403.
- Verificar: mensaje de error claro; no se redirige al dashboard.

---

### RF02 — Recuperación de contraseña

**CP05 — recuperacion-correo-existente**
- Precondición: correo existe en Firebase Auth.
- Acción: POST a `/api/auth/password-reset` (o equivalente) con `{ "correo": "usuario@existente.com" }`.
- Resultado esperado: HTTP 200, Firebase envía el correo de recuperación.
- Verificar: respuesta no expone información sensible; el enlace generado por Firebase es único y temporal.

**CP06 — recuperacion-correo-inexistente**
- Acción: POST con correo que no existe en Firebase ni en tabla `usuario`.
- Resultado esperado: HTTP 404 (o según implementación, puede ser 200 genérico para no revelar existencia — verifica cuál es el comportamiento implementado y documenta).
- Verificar: no se envía correo; la respuesta no confirma si el correo existe o no (seguridad).

**CP07 — enlace-expirado**
- Precondición: obtener un enlace de recuperación válido de Firebase.
- Acción: esperar a que expire (Firebase los expira en ~1 hora) o invalidarlo manualmente; luego intentar usarlo.
- Resultado esperado: Firebase rechaza el enlace; el frontend muestra error en la vista de nueva contraseña.
- Verificar: HTTP 400 o error de Firebase; no se permite cambiar la contraseña.

---

### RF04 — Cierre de sesión

**CP08 — cierre-sesion-exitoso**
- Precondición: usuario autenticado con token válido.
- Acción: click en "Cerrar Sesión" en la barra lateral (o POST a `/api/auth/logout`).
- Resultado esperado: Firebase invalida el token local (`signOut()`); el frontend redirige a `/login`.
- Verificar: el token anterior ya no puede usarse para requests autenticados (ver CP09); no hay sesión en localStorage/sessionStorage.

**CP09 — acceso-con-token-revocado**
- Precondición: token obtenido antes del cierre de sesión del CP08.
- Acción: GET a cualquier endpoint protegido usando el token anterior en `Authorization: Bearer {tokenAnterior}`.
- Resultado esperado: HTTP 401.
- Verificar: el backend rechaza el token; la respuesta indica que la sesión no es válida.

---

### RF03 — Gestión de usuarios (solo ADMINISTRADOR)

**CP10 — crear-usuario-como-gerente**
- Precondición: token de usuario con rol `ADMINISTRADOR`.
- Acción: POST a `/api/usuarios` con body `{ "nombre": "...", "correo": "...", "contrasenaTemp": "...", "rol": "ENCARGADO_VENTAS" }`.
- Resultado esperado: HTTP 201; el usuario aparece en tabla `usuario` con `activo = true`; Firebase crea la cuenta.
- Verificar: `firebase_uid` en la tabla corresponde al UID creado en Firebase; el correo es único.

**CP11 — crear-usuario-como-no-gerente**
- Precondición: token de usuario con rol `ENCARGADO_INVENTARIO` o `ENCARGADO_VENTAS`.
- Acción: POST a `/api/usuarios` con el mismo body del CP10.
- Resultado esperado: HTTP 403.
- Verificar: no se crea ningún registro en tabla `usuario`; no se crea cuenta en Firebase.

**CP12 — editar-usuario**
- Precondición: token `ADMINISTRADOR`; usuario objetivo existe.
- Acción: PUT a `/api/usuarios/{id}` con campos a modificar (`nombre`, `correo`, `rol`).
- Resultado esperado: HTTP 200; los cambios se reflejan en tabla `usuario` y en Firebase si aplica.
- Verificar: `updated_at` se actualiza; los campos modificados son correctos.

**CP13 — deshabilitar-usuario**
- Precondición: token `ADMINISTRADOR`; usuario objetivo con `activo = true`.
- Acción: PATCH a `/api/usuarios/{id}/deshabilitar` (o PUT con `{ "activo": false }`).
- Resultado esperado: HTTP 200; `activo = false` en tabla `usuario`.
- Verificar: el usuario deshabilitado no puede autenticarse (CP04 aplica tras este CP); no se elimina el registro.

---

### RF05 — Actualización de perfil

**CP14 — actualizar-perfil-exitoso**
- Precondición: cualquier usuario autenticado.
- Acción: PUT a `/api/usuarios/perfil` con `{ "nombre": "Nuevo Nombre", "correo": "nuevo@correo.com", "telefono": "3001234567", "fotoUrl": "..." }`.
- Resultado esperado: HTTP 200; los campos se actualizan en tabla `usuario`.
- Verificar: `updated_at` se actualiza; los valores devueltos coinciden con los enviados.

**CP15 — correo-duplicado**
- Precondición: el correo enviado ya existe en tabla `usuario` para otro usuario.
- Acción: PUT a `/api/usuarios/perfil` con un correo ya registrado.
- Resultado esperado: HTTP 409 (Conflict).
- Verificar: el correo del usuario no cambia; se devuelve mensaje de error descriptivo.

---

### RF06 — Cambio de contraseña

**CP16 — cambio-contrasena-exitoso**
- Precondición: usuario autenticado que conoce su contraseña actual.
- Acción: POST a `/api/usuarios/cambiar-contrasena` con `{ "contrasenaActual": "...", "nuevaContrasena": "...", "confirmacion": "..." }`.
- Resultado esperado: HTTP 200; Firebase actualiza la contraseña.
- Verificar: el usuario puede hacer login con la nueva contraseña; la antigua ya no funciona.

**CP17 — contrasena-actual-incorrecta**
- Acción: mismo endpoint con `contrasenaActual` incorrecta.
- Resultado esperado: HTTP 400 o 401.
- Verificar: la contraseña no cambia; Firebase no se modifica.

**CP18 — confirmacion-no-coincide**
- Acción: POST con `nuevaContrasena` ≠ `confirmacion`.
- Resultado esperado: HTTP 400 con error de validación.
- Verificar: la validación ocurre en el backend (no solo en frontend); la contraseña no cambia.

---

## Pasos para ejecutar

1. **Conectarte a Supabase (MCP):** consulta `SELECT id, firebase_uid, correo, rol, activo FROM usuario LIMIT 20;` para obtener datos reales de prueba.
2. **Datos de Firebase:** para los tests de login, necesitarás credenciales reales o un emulador de Firebase configurado. Verifica si el proyecto tiene `firebase-emulator` configurado en `package.json` o `firebase.json`.
3. **Variables de entorno:** los tests deben leer URLs del backend y credenciales de prueba desde variables de entorno (`.env.test`), nunca hardcodeadas.
4. **Orden de ejecución:** CP01→CP02→CP03→CP04 (RF01), luego CP08→CP09 (RF04), luego CP10→CP13 (RF03), luego CP14→CP18 (RF05/RF06). RF02 puede ejecutarse en paralelo.

---

## Convenciones de nomenclatura de archivos

Sigue la estructura existente del proyecto. Si no hay tests, crea:
- Backend: `src/test/java/com/gelox/{modulo}/{CasoPruebaTest}.java`
- Frontend: `src/__tests__/{modulo}/{caso-prueba}.test.{ts|js}`

Nombra cada test con el ID del caso: `describe('CP01 - login exitoso', ...)`.
