# Plan de Pruebas — Iteración 1 GELOX

## Alcance
RF01, RF02, RF04 (HU-01) — Autenticación y sesión  
RF12, RF13, RF14 (HU-05) — Reportes financieros

---

## HU-01: Autenticación, Recuperación y Cierre de Sesión

### RF01 — Login
| ID   | Caso de prueba                  | Precondición                        | Pasos                                              | Resultado esperado                              |
|------|---------------------------------|-------------------------------------|----------------------------------------------------|-------------------------------------------------|
| TC01 | Login válido                    | Usuario activo en BD                | POST /api/auth/login con credenciales correctas    | 200 OK, token JWT devuelto                      |
| TC02 | Login credenciales incorrectas  | Usuario existe, contraseña errónea  | POST /api/auth/login con password incorrecto       | 401 Unauthorized, mensaje de error              |
| TC03 | Login usuario inactivo          | Usuario con activo=false            | POST /api/auth/login                               | 401 o 403, acceso denegado                      |
| TC04 | Login sin campos requeridos     | —                                   | POST /api/auth/login con body vacío                | 400 Bad Request, validación de campos           |

### RF02 — Recuperación de contraseña
| ID   | Caso de prueba                  | Precondición                        | Pasos                                              | Resultado esperado                              |
|------|---------------------------------|-------------------------------------|----------------------------------------------------|-------------------------------------------------|
| TC05 | Link de recuperación recibido   | Correo registrado en el sistema     | POST /api/auth/recuperar con correo válido         | 200 OK, correo enviado vía Firebase Auth        |
| TC06 | Correo no registrado            | Correo inexistente en BD            | POST /api/auth/recuperar con correo desconocido    | 404 o 200 (no revelar existencia), sin correo   |
| TC07 | Link de recuperación expirado   | Link generado hace >1 hora          | Acceder al link expirado                           | Firebase rechaza el token, mensaje de expiración|

### RF04 — Cierre de sesión
| ID   | Caso de prueba                  | Precondición                        | Pasos                                              | Resultado esperado                              |
|------|---------------------------------|-------------------------------------|----------------------------------------------------|-------------------------------------------------|
| TC08 | Cierre de sesión válido         | Usuario autenticado, token activo   | POST /api/auth/logout con token válido             | 200 OK, token invalidado en Firebase            |
| TC09 | Token inválido tras cierre      | Haberse cerrado sesión              | Usar el token anterior en cualquier endpoint       | 401 Unauthorized, token rechazado               |
| TC10 | Cierre sin token                | —                                   | POST /api/auth/logout sin header Authorization     | 401 Unauthorized                                |

---

## HU-05: Reportes Financieros (RF12, RF13, RF14)

### RF12 — Reporte financiero
| ID   | Caso de prueba                        | Precondición                              | Pasos                                                              | Resultado esperado                                                    |
|------|---------------------------------------|-------------------------------------------|--------------------------------------------------------------------|-----------------------------------------------------------------------|
| TC11 | Reporte con datos en el período       | Ventas y pedidos en BD para el período    | GET /api/reportes/financiero?fechaInicio=X&fechaFin=Y (rol ADMIN)  | 200 OK, totalInversion > 0, utilidadNeta y margenGanancia calculados  |
| TC12 | Sin datos en el período               | Sin ventas ni pedidos en las fechas       | GET /api/reportes/financiero?fechaInicio=X&fechaFin=Y              | 200 OK, todos los campos en 0, margenGanancia=null                    |
| TC13 | Período inválido (inicio > fin)       | —                                         | GET /api/reportes/financiero?fechaInicio=2026-02&fechaFin=2026-01  | 400 Bad Request                                                       |
| TC14 | Acceso sin rol ADMINISTRADOR          | Usuario con rol ENCARGADO_VENTAS          | GET /api/reportes/financiero con token válido no admin             | 403 Forbidden                                                         |

### RF13 — Gráfica inversión vs ingresos
| ID   | Caso de prueba                        | Precondición                              | Pasos                                                                  | Resultado esperado                                                  |
|------|---------------------------------------|-------------------------------------------|------------------------------------------------------------------------|---------------------------------------------------------------------|
| TC15 | Gráfica con datos suficientes         | Ventas en varias semanas del período      | GET /api/reportes/grafica-inversion-ingresos?fechaInicio=X&fechaFin=Y | 200 OK, lista con puntos por semana, inversion e ingresos > 0       |
| TC16 | Datos insuficientes para gráfica      | Sin ventas ni pedidos                     | GET /api/reportes/grafica-inversion-ingresos                           | 200 OK, lista con puntos en cero (una entrada por cada semana)      |

### RF14 — Tabla rentabilidad por canal
| ID   | Caso de prueba                        | Precondición                              | Pasos                                                              | Resultado esperado                                                    |
|------|---------------------------------------|-------------------------------------------|--------------------------------------------------------------------|-----------------------------------------------------------------------|
| TC17 | Todos los canales con datos           | Ventas VENTANILLA, RURAL y planillas       | GET /api/reportes/por-canal?fechaInicio=X&fechaFin=Y               | 200 OK, 3 filas: VENTANILLA, RURAL, COMERCIANTES con valores > 0     |
| TC18 | Canal sin ventas muestra ceros        | Solo hay ventas VENTANILLA, no RURAL       | GET /api/reportes/por-canal                                        | Fila RURAL con ingresos=0, costos=0, margen=0                         |
| TC19 | Margen calculado correctamente        | Datos conocidos en BD                     | GET /api/reportes/por-canal, validar margen=(ing-cost)/ing*100     | Margen coincide con cálculo manual                                    |

---

## Criterios de Aceptación Globales
- Todos los endpoints de reportes devuelven 403 a roles no-ADMINISTRADOR.
- Ningún endpoint lanza 500 ante períodos sin datos (manejo defensivo de división por cero).
- Los tiempos de respuesta deben ser < 5 segundos con el volumen de datos actual.
