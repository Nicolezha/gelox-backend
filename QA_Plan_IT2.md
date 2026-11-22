# Plan de Pruebas — Iteración 2 GELOX

Tarea del Project: **T138** — Elaborar plan de pruebas para los módulos de la Iteración 2 (T138-PR1 y T138-PR2) · 26/10/2026  
Responsable: Israel Bulla Rey (QA) · Predecesoras: T107, T122 · Casos: T139 (27/10/2026)  
Formato: el de `QA_Plan_Voz_Iteracion1.md` (tablas ID / Caso / Precondición / Pasos / Resultado esperado).  
Fuentes: Planificación de Alcance Global (HU-17 cierre, HU-18 y HU-19 parciales), FP-15 §1.2–§1.4, §2.7–§2.13 y §4.2, Planeación técnica de la Iteración 2 v2 (contrato técnico, sección 2).

Los casos de aceptación usan los IDs del FP-15 §4.2 (CP-28 … CP-43), que siguen la numeración de la Iteración 1 (CP-01 … CP-27). Los casos complementarios por API, los negativos y los de aislamiento que el FP-15 no cubre usan los IDs de la tarea T139 (CP-54-nn … CP-62-nn, CP-TEN-nn).

## Alcance
RF54, RF55, RF56, RF57 (HU-17, cierre) — Recomendación de compras  
RF58, RF59 (HU-18, parcial) — Aprovisionamiento y ciclo de vida de distribuidoras  
RF60, RF61 (HU-19, parcial) — Identidad visual y landing page por distribuidora  
RF62 (HU-19, parcial) — Gestión de usuarios internos por distribuidora  
Transversal — Aislamiento de datos entre distribuidoras (tenant_id) y bloqueo por estado

Fuera de alcance: RF63–RF71 (Iteración 3). La voz de la Iteración 1 (RF46–RF53) solo entra como regresión.

---

## 1. Tipos de prueba

| Tipo | Qué se comprueba | Herramienta | Tarea que lo ejecuta |
|------|------------------|-------------|----------------------|
| Unitaria / integración backend | Lógica de servicios y reglas del contrato (rf54 … rf62, tenant/) | JUnit 5 + Mockito, `./mvnw test` | Cada desarrollador antes del PR; T130 |
| Funcional por API | Cada endpoint del contrato: códigos HTTP, cuerpo de respuesta, validaciones y roles | Postman: `GELOX_IT2.postman_collection.json`, `GELOX_RF55_RF57.postman_collection.json` | T140 |
| Funcional por pantalla | Criterios de aceptación de HU-17, HU-18 y HU-19 desde el frontend | Chrome sobre el servidor de desarrollo | T140 |
| Aislamiento por tenant | Una distribuidora nunca ve ni modifica datos de otra; tokens inconsistentes y rutas por rol | Postman con el token de cada distribuidora + dos sesiones de navegador | T140-PR3, T141-PR3 |
| Voz con LLM | Intenciones nuevas (CONSULTAR_RECOMENDACION, DECIDIR_RECOMENDACION), respaldo al clasificador, precisión y latencia | Chrome, DevTools, tabla `comando_voz` | T141 |
| Aceptación con el cliente | El cliente (Ayde Arévalo) usa las pantallas y dice los comandos de voz | Sesión grabada sobre el servidor de desarrollo | T142 |
| Regresión | Lo construido en Iteración 1 y antes sigue funcionando con el multi-tenant | `./mvnw test`, Postman `GELOX_RF46_RF53` y `GELOX_IT2`, flujos manuales | T141-PR1, T145 |

---

## 2. Entorno y datos de prueba

### 2.1 Entorno
| Elemento | Valor |
|----------|-------|
| Servidor de desarrollo | Backend `https://gelox-backend.onrender.com`, frontend `https://gelox-frontend.onrender.com` con `VITE_USAR_MOCK_IT2=false` |
| Base de datos | Supabase (PostgreSQL) con migraciones V5 (`multitenant_distribuidora`) y V6 (`recomendacion_compra`) aplicadas |
| Pruebas locales | Backend local con JDK 25 (`sh ./mvnw test` desde Git Bash) para la suite JUnit y Postman contra `{{baseUrl}}` local cuando el despliegue no esté disponible |
| LLM | Variables `LLM_API_URL`, `LLM_API_KEY`, `LLM_MODELO` configuradas; `llm.timeout-ms=2500` |
| Navegador | Chrome (Web Speech API, es-CO) con micrófono permitido |

### 2.2 Distribuidoras
| Distribuidora | Datos | Uso |
|---------------|-------|-----|
| Mágico Sabor (piloto) | `tenant_id = 00000000-0000-0000-0000-000000000001`, subdominio `magicosabor`, historial real de ventas y planillas del 07/08/2026 en adelante | Recomendación, identidad, landing, usuarios, regresión |
| Helados Prueba S.A.S | NIT 901111222, subdominio `helados-prueba`, plan Básico; la registra el Superadministrador en CP-36 | Aprovisionamiento, ciclo de vida (CP-38, CP-39), aislamiento |
| Prueba1 S.A.S | Nombre comercial «prueba», subdominio `prueba`, plan Básico, sin productos | Identidad visual (CP-40, CP-41), landing (CP-42) |

Helados Prueba termina «Inactivo / Archivado» en CP-39, así que los casos de aislamiento se corren antes de CP-39 o con Prueba1.

### 2.3 Cuentas por rol
| Rol | Distribuidora | Variable Postman |
|-----|---------------|------------------|
| SUPERADMINISTRADOR (Equipo RESET) | Ninguna (sin tenant) | `{{tokenSuper}}` |
| ADMINISTRADOR (Gerente titular: Ayde Arévalo) | Mágico Sabor | `{{token}}` |
| ENCARGADO_INVENTARIO | Mágico Sabor | — (sesión de navegador) |
| ENCARGADO_VENTAS | Mágico Sabor | `{{tokenVendedor}}` |
| ADMINISTRADOR (Gerente titular) | Helados Prueba o Prueba1 | `{{tokenGerentePrueba}}` |

Las credenciales no se escriben en este archivo; se comparten por el canal del equipo.

### 2.4 Archivos de prueba
Carpeta `postman-fixtures/`: `logo-valido.png`, `logo-3mb.png` (excede 2 MB), `logo-con-script.svg` (SVG con `<script>`), `favicon-valido.ico`, `favicon-600kb.ico` (excede 512 KB).

---

## Módulo Recomendación de Compras (HU-17, cierre)

### RF54 — Cálculo de la recomendación · Casos de aceptación (FP-15 §4.2)
| ID | Caso de prueba | Precondición | Pasos | Resultado esperado |
|----|----------------|--------------|-------|--------------------|
| CP-31 | Cálculo con presupuesto suficiente | Mágico Sabor con historial del 07/08/2026 en adelante | Menú «Recomendación de Compra», presupuesto $ 3.000.000 y «Generar recomendación» | Recomendación «Generada» con urgencia, score, cajas, costo, margen y justificación por producto, ordenada por urgencia, margen y score, sin superar el presupuesto y con los excluidos por datos incompletos aparte |
| CP-32 | Presupuesto que no alcanza | Recomendaciones de $ 2.000.000 y $ 3.000.000 | Revisar los productos «Excluido» y «Reducido» | El costo nunca supera el presupuesto; el que no cabe queda «Excluido» con 0 cajas y se sigue evaluando la lista; el que cabe en parte queda «Reducido» |

### RF54 — Casos complementarios (T139)
| ID | Caso de prueba | Precondición | Pasos | Resultado esperado |
|----|----------------|--------------|-------|--------------------|
| CP-54-01 | Respuesta de la API | Gerente de Mágico Sabor | POST /api/recomendaciones {presupuesto: 3000000, origen: PANTALLA} | 201 con los campos del contrato (urgencia, score, coberturaDias, demandaDiaria, cajasSugeridas, ajustePresupuesto, costoEstimado, margenEsperado, justificacion) |
| CP-54-02 | Ejemplo resuelto Aloha Mango Biche | Datos de FP-15 §2.10.6 | POST y revisar el ítem del producto | Demanda diaria, punto de reorden y cajas iguales al cálculo del FP-15 |
| CP-54-03 | Presupuesto que excluye un producto | Mismos datos | POST con presupuesto $ 80.000 | Un ítem EXCLUIDO que no detiene el recorrido; costoTotal ≤ presupuesto |
| CP-54-04 | Presupuesto que reduce un producto | Mismos datos | POST con presupuesto $ 170.000 | Un ítem REDUCIDO; costoTotal ≤ presupuesto |
| CP-54-05 | Presupuesto inválido | — | POST con presupuesto 0, negativo y sin presupuesto | 400 en los tres |
| CP-54-06 | Ningún producto requiere compra | Stock de todos los productos sobre su punto de reorden | POST con presupuesto válido | 201 con lista vacía, sin 500 |
| CP-54-07 | Confianza baja | Distribuidora con pocos días con ventas | POST con `{{tokenGerentePrueba}}` | `confianzaBaja = true` y aviso en pantalla |
| CP-54-08 | Producto sin costo | Producto con precio_costo vacío o 0 | POST | Aparece en `productosExcluidos` con su motivo |
| CP-54-09 | Rol sin permiso | ENCARGADO_VENTAS | POST con `{{tokenVendedor}}` | 403 |

### RF55 — Consultar y decidir · Casos de aceptación (FP-15 §4.2)
| ID | Caso de prueba | Precondición | Pasos | Resultado esperado |
|----|----------------|--------------|-------|--------------------|
| CP-28 | Consultar la recomendación por voz | Sin recomendación del día | Activar con el micrófono o «Hola GELOX» y decir «¿Qué me recomiendas pedir con dos millones?» | Responde sin pedir confirmación, muestra los tres primeros productos y queda una recomendación de $ 2.000.000 con origen «Voz» que se abre completa en pantalla |
| CP-29 | Decidir por voz con confirmación, cancelación y plazo | Recomendación de CP-28 | «Aprueba.», «Aprueba el choco kono mini.» + «Confirmar», «Cambia la cantidad de casero a dos cajas y a prueba.», «O k cambia al casero coco a dos cajas.» + «Confirmar» | Cada orden muestra producto, cajas y costo y pide «¿Confirmas?» con plazo de 15 s; solo cambian los confirmados; frases incompletas o no reconocidas no cambian nada; recomendación «En revisión» con el costo aprobado recalculado |
| CP-33 | Decidir en pantalla | Recomendación de $ 3.000.000 (CP-31) | Modificar Paleta Coco de 2 a 4 cajas, luego rechazarla sin motivo; aprobar Chococono mini | «Aprobar» deja las cajas sugeridas; «Modificar» tacha la sugerida; 0 cajas = «Rechazado»; en un «Excluido» «Aprobar» está deshabilitado; el costo aprobado se recalcula |

### RF55 — Casos complementarios (T139)
| ID | Caso de prueba | Precondición | Pasos | Resultado esperado |
|----|----------------|--------------|-------|--------------------|
| CP-55-01 | Aprobar por API | Recomendación de CP-54-01 | PATCH /api/recomendaciones/{id}/items/{itemId} {decision: APROBADO} | 200; costoAprobado recalculado |
| CP-55-02 | Modificar por API | Misma recomendación | PATCH {decision: MODIFICADO, cajas: 2} | Ítem MODIFICADO; recomendación EN_REVISION |
| CP-55-03 | Modificar a 0 cajas | Misma recomendación | PATCH {decision: MODIFICADO, cajas: 0} | El ítem queda RECHAZADO |
| CP-55-04 | Rechazar sin motivo | Misma recomendación | PATCH {decision: RECHAZADO} | RECHAZADO (motivo opcional) |
| CP-55-05 | Aprobar un ítem EXCLUIDO | Ítem EXCLUIDO de CP-54-03 | PATCH {decision: APROBADO} | 400 |
| CP-55-06 | Decidir una recomendación convertida | CP-56-01 ejecutado | PATCH sobre un ítem | 409 |
| CP-55-07 | Recomendación de otra distribuidora | Recomendación de Mágico Sabor | GET y PATCH con `{{tokenGerentePrueba}}` | 404 |
| CP-55-08 | Voz: rechazar y cancelar | Recomendación del día | «Rechaza el …» y «Cancelar» | No cambia nada (no se ejecutó en CP-29) |
| CP-55-09 | Voz: vence el plazo | Recomendación del día | Una orden de decisión y esperar más de 15 s | Operación expirada; no cambia nada (no se ejecutó en CP-29) |
| CP-55-10 | Voz: producto excluido o fuera de la recomendación | Recomendación del día | «Aprueba el …» con un producto EXCLUIDO y con uno que no está | Lo indica y no cambia nada (no se ejecutó en CP-29) |

Las demás filas de la tabla de flujos de voz de FP-15 §2.11.5 se agregan como CP-55-11 en adelante.

### RF56 — Pedido desde la recomendación · Casos de aceptación (FP-15 §4.2)
| ID | Caso de prueba | Precondición | Pasos | Resultado esperado |
|----|----------------|--------------|-------|--------------------|
| CP-30 | Aprobar todo y generar el pedido por voz | Recomendación #EAA47B73 (CP-29) | «Aprueba todo.» + «Confirmar», «Genera el pedido.» + «Confirmar.» | Aprueba los pendientes con cajas y crea un pedido Pendiente vinculado con lo aprobado y modificado; la recomendación queda «Pedido generado» y no admite más decisiones |
| CP-34 | Generar el pedido en pantalla | Recomendación #F116D55C decidida en CP-33 | «Generar pedido», confirmar y abrir `pedido.xlsx` | Un solo pedido Pendiente con lo aprobado y modificado; Excel con el formato de pedido masivo; no se puede generar un segundo pedido |

### RF56 — Casos complementarios (T139)
| ID | Caso de prueba | Precondición | Pasos | Resultado esperado |
|----|----------------|--------------|-------|--------------------|
| CP-56-01 | Generar por API | Recomendación con ítems APROBADO y MODIFICADO | POST /api/recomendaciones/{id}/pedido | 200 {pedidoId, exportUrl}; evento PEDIDO_DESDE_RECOMENDACION |
| CP-56-02 | Sin ítems aprobados | Recomendación sin aprobados | POST …/pedido | 400 |
| CP-56-03 | Generar dos veces | CP-56-01 ejecutado | POST …/pedido otra vez | 409 |
| CP-56-04 | Excel por API | CP-56-01 ejecutado | GET /api/inventario/pedidos/{id}/exportar | Excel de Nutresa (`templates/catalogo.xlsx`) con las cantidades del pedido |
| CP-56-05 | Recomendación de otra distribuidora | Recomendación de Mágico Sabor | POST …/pedido con `{{tokenGerentePrueba}}` | 404 |

### RF57 — Historial y métricas · Casos de aceptación (FP-15 §4.2)
| ID | Caso de prueba | Precondición | Pasos | Resultado esperado |
|----|----------------|--------------|-------|--------------------|
| CP-35 | Historial por período | CP-28 a CP-34 ejecutados | Menú «Historial de Recomendaciones»; períodos Mes, Día, Semana y Año | Solo las recomendaciones del período con fecha, código, origen, presupuesto, costos, decisiones y estado; métricas de generadas, tasa de aprobación, decisiones y precisión de cantidad |

### RF57 — Casos complementarios (T139)
| ID | Caso de prueba | Precondición | Pasos | Resultado esperado |
|----|----------------|--------------|-------|--------------------|
| CP-57-01 | Historial paginado | Varias recomendaciones | GET /api/recomendaciones?page=0&size=10 | Lista paginada solo de la distribuidora |
| CP-57-02 | Historial filtrado | Recomendaciones en distintos estados | GET /api/recomendaciones?desde&hasta&estado | Solo las del período y estado pedidos |
| CP-57-03 | Métricas cuadradas a mano | Mismo período | GET /api/recomendaciones/metricas?desde&hasta | generadas, itemsSugeridos, aprobados, modificados, rechazados, tasaAprobacion y precisionCantidad coinciden con un conteo manual |
| CP-57-04 | Fecha inicial posterior a la final | — | GET con desde > hasta | 400 |

---

## Módulo Aprovisionamiento y Ciclo de Vida (HU-18, parcial)

### RF58 — Registrar y aprovisionar · Casos de aceptación (FP-15 §4.2)
| ID | Caso de prueba | Precondición | Pasos | Resultado esperado |
|----|----------------|--------------|-------|--------------------|
| CP-36 | Registrar una distribuidora y acceso del gerente titular | Superadministrador (Equipo RESET) | «Nueva distribuidora» con Helados Prueba S.A.S, NIT 901111222, subdominio `helados-prueba`, plan Básico y datos del gerente titular; la gerente define su contraseña desde el correo e inicia sesión | Identificador único no editable, estado «En periodo de prueba», identidad por defecto; la gerente entra a una distribuidora propia y vacía |
| CP-37 | Datos repetidos y reenvío del acceso | CP-36 ejecutado | Registrar con el NIT 901111222 y luego con el subdominio `helados-prueba`; «Reenviar acceso» en el detalle | Indica el dato repetido sin crear nada; el reenvío genera un acceso nuevo y el anterior deja de funcionar |

### RF58 — Casos complementarios (T139)
| ID | Caso de prueba | Precondición | Pasos | Resultado esperado |
|----|----------------|--------------|-------|--------------------|
| CP-58-01 | Respuesta de la API | `{{tokenSuper}}` | POST /api/plataforma/distribuidoras con datos válidos | 201 con id, estado PRUEBA, identidad #1F3864 / #2E75B6 sin logo y `correoEnviado` |
| CP-58-02 | NIT o subdominio repetidos por API | CP-58-01 ejecutado | POST con el mismo NIT y luego con el mismo subdominio | 409 en ambos |
| CP-58-03 | Correo del gerente repetido | CP-58-01 ejecutado | POST con el mismo correo | 409 (no se probó en CP-37) |
| CP-58-04 | Falla de Firebase | Prueba unitaria (`rf58/AprovisionamientoTest`) | Simular error al crear el usuario | 502 y rollback: no queda distribuidora a medias |
| CP-58-05 | Gerente en rutas de plataforma | Gerente de Mágico Sabor | GET /api/plataforma/distribuidoras con `{{token}}` | 403 |

### RF59 — Ciclo de vida y bloqueo · Casos de aceptación (FP-15 §4.2)
| ID | Caso de prueba | Precondición | Pasos | Resultado esperado |
|----|----------------|--------------|-------|--------------------|
| CP-38 | Activar, suspender por mora y bloquear | Helados Prueba «En periodo de prueba» y su gerente con sesión abierta | Cambiar a «Activo» con motivo y luego a «Suspendido por mora» con motivo; la gerente intenta usar el sistema | Solo se ofrecen transiciones permitidas y el motivo es obligatorio; la gerente queda bloqueada en su siguiente acción con la pantalla informativa (estado, contacto y «Cerrar sesión», sin el motivo interno) |
| CP-39 | Reactivar, dar de baja y transiciones no permitidas | Helados Prueba «Suspendido por mora» | Cambiar a «Activo» (la gerente entra) y luego a «Inactivo / Archivado» (la gerente intenta entrar) | Al reactivar se recupera el acceso; «Inactivo / Archivado» es final, sin más cambios ni acceso; nunca se ofrece volver a «En periodo de prueba» |

### RF59 — Casos complementarios (T139)
| ID | Caso de prueba | Precondición | Pasos | Resultado esperado |
|----|----------------|--------------|-------|--------------------|
| CP-59-01 | Transición no permitida por API | Distribuidora INACTIVO | PATCH …/{id}/estado a ACTIVO | 409 «Transición no válida» |
| CP-59-02 | Motivo vacío por API | Distribuidora de prueba | PATCH sin motivo | 400 |
| CP-59-03 | Cuerpo del bloqueo | Distribuidora SUSPENDIDO | Llamar a la API con un usuario suyo | 403 con `codigo` DISTRIBUIDORA_SUSPENDIDA, `distribuidora` y `contacto` |
| CP-59-04 | Cuerpo del bloqueo por inactivación | Distribuidora INACTIVO | Llamar a la API con un usuario suyo | 403 con `codigo` DISTRIBUIDORA_INACTIVA |
| CP-59-05 | Usuario inactivo | Usuario deshabilitado de una distribuidora ACTIVO | Llamar a la API | 403 con `codigo` USUARIO_INACTIVO, sin pantalla de distribuidora bloqueada |

---

## Módulo Identidad Visual y Landing Page (HU-19, parcial)

### RF60 — Identidad visual · Casos de aceptación (FP-15 §4.2)
| ID | Caso de prueba | Precondición | Pasos | Resultado esperado |
|----|----------------|--------------|-------|--------------------|
| CP-40 | Personalizar la identidad y verla en la interfaz y los documentos | Gerente titular de Prueba1 S.A.S | Ajustes › Identidad visual: primario #CCAA00, secundario #DD00C9, logotipo PNG y «Guardar cambios»; la Gerente de Mágico Sabor imprime una planilla | Vista previa en vivo; se aplica sin recargar en menú, botones, encabezados, pestaña y documentos; Mágico Sabor conserva su identidad |
| CP-41 | Validaciones y permisos | Gerente de Prueba1 | Cargar archivos y colores fuera de las reglas; un encargado intenta abrir la identidad | Rechaza formato, tamaño y SVG con código; advierte contraste menor a 4,5:1; solo el Gerente modifica |

### RF60 — Casos complementarios (T139)
| ID | Caso de prueba | Precondición | Pasos | Resultado esperado |
|----|----------------|--------------|-------|--------------------|
| CP-60-01 | Identidad por API | Gerente de Mágico Sabor | PUT /api/distribuidora/identidad multipart con colores, logo y favicon válidos | 200; archivos en `distribuidoras/{tenantId}/`; GET devuelve las URL nuevas |
| CP-60-02 | Color inválido | — | PUT con colorPrimario «azul» | 400 |
| CP-60-03 | Logo mayor a 2 MB | `logo-3mb.png` | PUT con el logo | 400 |
| CP-60-04 | Favicon mayor a 512 KB | `favicon-600kb.ico` | PUT con el favicon | 400 |
| CP-60-05 | SVG con script | `logo-con-script.svg` | PUT con el logo | 400 |
| CP-60-06 | Excel del pedido con identidad | Identidad guardada | Descargar el Excel de un pedido | Encabezado A1:I3 con logo, nombre y colores |
| CP-60-07 | Permisos por API | ENCARGADO_VENTAS | GET y PUT con `{{tokenVendedor}}` | GET 200; PUT 403 |
| CP-60-08 | Sin token | — | PUT sin Authorization | 401 |

### RF61 — Landing pública · Casos de aceptación (FP-15 §4.2)
| ID | Caso de prueba | Precondición | Pasos | Resultado esperado |
|----|----------------|--------------|-------|--------------------|
| CP-42 | Landing por subdominio | Visitante sin sesión | Abrir `/tienda/magicosabor` y `/tienda/prueba` | Cada una con su nombre, identidad, catálogo activo con precio, ubicación, horario y WhatsApp; sin productos lo indica |
| CP-43 | Distribuidora suspendida o inexistente | Helados Prueba archivada (CP-39) | Abrir `/tienda/helado`, `/tienda/helados-prueba` y `/tienda/magicosabor` | Las dos primeras muestran «Esta distribuidora no está disponible» sin datos; Mágico Sabor sigue disponible |

### RF61 — Casos complementarios (T139)
| ID | Caso de prueba | Precondición | Pasos | Resultado esperado |
|----|----------------|--------------|-------|--------------------|
| CP-61-01 | Endpoints públicos | — | GET /api/landing/{subdominio}/info-negocio, /productos, /whatsapp, /identidad | Datos solo de esa distribuidora |
| CP-61-02 | Rutas sin subdominio | — | GET /api/landing/productos | Mismo catálogo de `magicosabor` |
| CP-61-03 | Catálogo de otra distribuidora | Prueba1 sin productos | GET /api/landing/prueba/productos | Ningún producto de la piloto |
| CP-61-04 | Inexistente o suspendida por API | — | GET con un subdominio inexistente y con uno suspendido | 404 |

---

## Módulo Usuarios Internos (HU-19, parcial)

### RF62 — Usuarios de la distribuidora
El FP-15 §4.2 no tiene un caso de aceptación de RF62: termina en CP-43 (RF61). Los casos de esta tabla son la base para agregarlo.

| ID | Caso de prueba | Precondición | Pasos | Resultado esperado |
|----|----------------|--------------|-------|--------------------|
| CP-62-01 | Listar usuarios | Gerente de Mágico Sabor | GET /api/usuarios | Solo usuarios del propio tenant; el titular con `esGerenteTitular = true` |
| CP-62-02 | Crear Encargado de Ventas | — | POST /api/usuarios con rol ENCARGADO_VENTAS | 201; el usuario inicia sesión y su token trae el claim `tenant_id` |
| CP-62-03 | Crear con rol ADMINISTRADOR | — | POST con rol ADMINISTRADOR | 400 |
| CP-62-04 | Crear con rol SUPERADMINISTRADOR | — | POST con rol SUPERADMINISTRADOR | 400 |
| CP-62-05 | Correo ya registrado | Usuario existente | POST con el mismo correo | 400 |
| CP-62-06 | Editar asignando ADMINISTRADOR | Usuario de CP-62-02 | PUT con rol ADMINISTRADOR | 400 |
| CP-62-07 | Deshabilitar y habilitar | Usuario de CP-62-02 | Deshabilitar, intentar iniciar sesión, habilitar | Deshabilitado no entra (USUARIO_INACTIVO); habilitado vuelve a entrar |
| CP-62-08 | Proteger al gerente titular | Gerente titular de Mágico Sabor | Deshabilitarlo y cambiarle el rol | 400 en ambos; conserva su rol y su acceso |
| CP-62-09 | Usuario de otra distribuidora | Usuario de Helados Prueba | GET /api/usuarios/{id} con `{{token}}` | 404 |
| CP-62-10 | Gestión en pantalla | Gerente de Mágico Sabor | Crear, editar y deshabilitar desde Gestión de Usuarios | Mismo resultado que por API; solo ofrece los roles de Ventas e Inventarios |

---

## Aislamiento entre distribuidoras

Se ejecuta con el Gerente de Mágico Sabor y el Gerente de Helados Prueba (antes de CP-39) o de Prueba1. Cualquier fuga es defecto **Crítico**. Base automatizada: `src/test/java/com/gelox/backend/tenant/AislamientoTenantTest.java` y `FirebaseAuthFilterTenantTest.java`. En el FP-15 §4.2 el aislamiento se ve de forma indirecta en CP-36 (gerente sin datos de Mágico Sabor), CP-40 (identidad propia) y CP-42 (landing propia).

| ID | Caso de prueba | Precondición | Pasos | Resultado esperado |
|----|----------------|--------------|-------|--------------------|
| CP-TEN-01 | Producto de otra distribuidora | Id de un producto de Mágico Sabor | GET por id con `{{tokenGerentePrueba}}` | 404 |
| CP-TEN-02 | Venta de otra distribuidora | Id de una venta de Mágico Sabor | GET por id con `{{tokenGerentePrueba}}` | 404 |
| CP-TEN-03 | Pedido a proveedor de otra distribuidora | Id de un pedido de Mágico Sabor | GET por id y exportar con `{{tokenGerentePrueba}}` | 404 |
| CP-TEN-04 | Planilla de otra distribuidora | Id de una planilla de Mágico Sabor | GET por id con `{{tokenGerentePrueba}}` | 404 |
| CP-TEN-05 | Usuario de otra distribuidora | Id de un usuario de Mágico Sabor | GET por id con `{{tokenGerentePrueba}}` | 404 |
| CP-TEN-06 | Recomendación de otra distribuidora | Id de una recomendación de Mágico Sabor | GET, PATCH y POST …/pedido con `{{tokenGerentePrueba}}` | 404 |
| CP-TEN-07 | Comando de voz de otra distribuidora | Comando pendiente de Mágico Sabor | Confirmarlo con `{{tokenGerentePrueba}}` | 404 o rechazo; nada se aplica en Mágico Sabor |
| CP-TEN-08 | Listados y landing | Ambas distribuidoras con datos | Listar productos, ventas, pedidos, usuarios y recomendaciones con cada token; abrir cada `/tienda/:subdominio` | Cada una solo ve lo propio |
| CP-TEN-09 | Claim tenant_id inconsistente | Token con `tenant_id` distinto al de la base | Cualquier petición autenticada | 401 |
| CP-TEN-10 | Superadministrador en ruta de negocio | — | GET /api/productos con `{{tokenSuper}}` | 403 |
| CP-TEN-11 | Voz por distribuidora | Sesión de cada distribuidora | «¿Cuántas cajas de Festival me quedan?» y «¿Cuánto vendimos hoy?» | Solo datos propios |
| CP-TEN-12 | Voz con distribuidora suspendida | Distribuidora SUSPENDIDO | Decir un comando | Recibe el bloqueo 403 |

---

## Voz con LLM

Los casos de voz de RF55 y RF56 están en CP-28, CP-29 y CP-30. Esta sección cubre la regresión de la Iteración 1 y el respaldo.

| ID | Caso de prueba | Precondición | Pasos | Resultado esperado |
|----|----------------|--------------|-------|--------------------|
| CP-LLM-01 | Regresión RF46–RF53 con el LLM | LLM configurado | Casos principales de `QA_Plan_Voz_Iteracion1.md` (CP-01 … CP-27) | Mismo resultado que en la Iteración 1; `comando_voz.metodo_clasificacion = LLM` |
| CP-LLM-02 | Respaldo ante falla | `LLM_API_URL` inválida o LLM apagado | Decir comandos de venta, consulta y recomendación | Responde el clasificador anterior (REGLAS/MODELO) sin error para el usuario |
| CP-LLM-03 | Respaldo por timeout | Respuesta del LLM > 2 500 ms | Decir un comando | Responde el clasificador anterior |
| CP-LLM-04 | Respaldo por respuesta inválida | JSON inválido, intención nula o confianza < 0,60 | Pruebas unitarias de voz/ | Se usa el clasificador anterior |
| CP-LLM-05 | Plazos de confirmación | — | Una decisión de recomendación y una venta por voz | `expiraEnSegundos` = 15 para DECIDIR_RECOMENDACION y 45 para las demás |
| CP-PRE-04 | Precisión LLM vs. clasificador | Frases de CP-PRE-01 (Iteración 1) más las de RF55 | Decir cada frase con el LLM y luego con el LLM apagado | Precisión con LLM ≥ precisión del clasificador anterior |
| CP-PRE-05 | Latencia | — | Medir en DevTools desde el fin de la frase hasta la respuesta | < 3 s |

Precisión = comandos con intención y datos correctos ÷ comandos dichos. CP-PRE-01 … 03 son los de la Iteración 1.

---

## Aceptación con el cliente

| Elemento | Detalle |
|----------|---------|
| Participante | Ayde Arévalo (Gerente, Mágico Sabor): dice los comandos de voz y usa las pantallas (regla de la docente) |
| Guion | CP-28 … CP-43: recomendación por voz y en pantalla, decisiones, pedido generado, historial, alta y ciclo de vida de distribuidoras, identidad y landing. Agregar un caso de RF62 (creación de un usuario interno) |
| Cuentas | Gerente, Encargado de Inventarios y Superadministrador; distribuidoras de prueba y una recomendación lista para decidir |
| Registro | Por caso: aprobado / rechazado / con observación; sesión grabada |
| Planeado / ejecución real | Planeado para el 30/10/2026 (T142); ejecutado el 04/11/2026 por Jesús Gabriel Torres y Zharick Nicole Hernandez, verificado por Ayde Arévalo (FP-15 §4.2) |
| Salida | Acta (T143) y observaciones al reporte de defectos (T144) |

---

## Regresión

| ID | Caso de prueba | Precondición | Pasos | Resultado esperado |
|----|----------------|--------------|-------|--------------------|
| CP-REG-01 | Suite backend completa | Código de la rama principal | `./mvnw -q test` | 0 fallos (rf01 … rf62, tenant/, voz/) |
| CP-REG-02 | Colecciones Postman | Servidor de desarrollo | Ejecutar `GELOX_RF46_RF53` y `GELOX_IT2` completas | Todas en verde |
| CP-REG-03 | Flujos tocados por el multi-tenant | Cuentas de los 3 roles de Mágico Sabor | Login por rol, venta ventanilla y rural, alerta de stock bajo (trigger de V5), planilla y liquidación, cierre de caja, reportes y dashboard, pedido manual con Excel, entrada de mercancía, asistente de voz y landing | Igual que antes de la iteración; un cierre de caja por distribuidora y día |
| CP-REG-04 | Reprueba de defectos corregidos | Defectos con estado «Corregido» | Repetir el caso original de cada defecto | Estado «Verificado» en `DEFECTOS_IT2.md` |

---

## Criterios, métricas y severidad (T138-PR2)

### Criterios de entrada
- Hitos T132, T133, T134 y T135 cumplidos (módulos construidos e integrados).
- Migraciones V5 y V6 aplicadas en el servidor de desarrollo.
- `./mvnw test` en verde y frontend desplegado con `VITE_USAR_MOCK_IT2=false`.
- Cuentas por rol y distribuidoras de prueba creadas.

### Criterios de salida
- 0 defectos Críticos abiertos.
- 0 fugas de datos entre distribuidoras.
- Todos los casos con resultado y evidencia.
- Acta de aceptación firmada por el cliente (hito T148).

### Métricas
| Métrica | Meta |
|---------|------|
| Precisión de voz con LLM | ≥ precisión del clasificador anterior |
| Latencia de respuesta de voz | < 3 s |
| Precisión de cantidad de la recomendación (RF57, `precisionCantidad`) | Se reporta y se compara con lo efectivamente pedido |
| Casos aprobados | 100 % de los casos de aceptación |

### Severidad de defectos
| Severidad | Cuándo |
|-----------|--------|
| Crítico | Fuga de datos entre distribuidoras, bloquea un RF o afecta dinero o inventario |
| Mayor | Un criterio de aceptación no se cumple pero existe un rodeo (workaround) |
| Menor | Cosmético: texto, estilo o mensaje sin efecto en datos ni en el flujo |

### Plantilla de reporte de resultados
| ID caso | RF | Ejecutado por | Fecha | Resultado (Aprobado / Falla / Bloqueado) | Evidencia (video y minuto) | Defecto |
|---------|----|---------------|-------|------------------------------------------|----------------------------|---------|
| CP-28 | RF55 | | | | | |

Registro de defectos (`DEFECTOS_IT2.md`, T144): DEF-NN, RF, caso, descripción, pasos, severidad, evidencia y estado.

---

## Matriz de trazabilidad (T139-PR3)

| RF | Hito | Criterio de HU | Casos de aceptación (FP-15 §4.2) | Casos complementarios |
|----|------|----------------|----------------------------------|-----------------------|
| RF54 | T132 | HU-17 c4–c6: score de urgencia, lista priorizada frente al presupuesto, desglose y justificación | CP-31, CP-32 | CP-54-01 … 09 |
| RF55 | T132 | HU-17 c7: consultar por voz y en pantalla, aprobar, modificar o rechazar | CP-28, CP-29, CP-33 | CP-55-01 … 10 |
| RF56 | T132 | HU-17 c8: pedido desde la recomendación aprobada | CP-30, CP-34 | CP-56-01 … 05 |
| RF57 | T132 | HU-17 c9: historial de generadas, aprobadas y rechazadas | CP-35 | CP-57-01 … 04 |
| RF58 | T133 | HU-18 c1–c2: registrar distribuidora con identificador único y configuración inicial | CP-36, CP-37 | CP-58-01 … 05 |
| RF59 | T133 | HU-18 c3–c4: estados y bloqueo con pantalla informativa | CP-38, CP-39 | CP-59-01 … 05 |
| RF60 | T134 | HU-19 c1–c2: identidad y su reflejo en documentos | CP-40, CP-41 | CP-60-01 … 08 |
| RF61 | T134 | HU-19 c3: landing pública por distribuidora | CP-42, CP-43 | CP-61-01 … 04 |
| RF62 | T135 | HU-19 c4–c5: usuarios internos solo de la propia distribuidora | **Falta en FP-15 §4.2** | CP-62-01 … 10 |
| Transversal | T132–T135 | Aislamiento entre distribuidoras | (indirecto en CP-36, CP-40, CP-42) | CP-TEN-01 … 12 |
| Voz con LLM | T132 | Regresión y precisión | CP-28 … CP-30 | CP-LLM-01 … 05, CP-PRE-04, 05 |

---

## Cronograma y responsables

| Tarea | Fecha (Project) | Responsable | Entregable |
|-------|-----------------|-------------|------------|
| T138 Plan de pruebas | Lun 26/10/2026 | Israel Bulla Rey | Este archivo |
| T139 Casos de prueba y matriz de trazabilidad | Mar 27/10/2026 | Israel Bulla Rey | Casos y matriz de este archivo |
| T130 Revisión del paquete de Construcción | Mié 28/10/2026 | Zharick Nicole Hernandez Arevalo | `./mvnw test`, lint y build en verde |
| T140 Pruebas funcionales, integración y aislamiento | Jue 29/10/2026 | Angie Nikol Ortiz Amaya | Resultado y evidencia por caso; matriz de aislamiento |
| T141 Regresión y precisión de voz con LLM, aislamiento en voz | Jue 29/10/2026 | Israel Bulla Rey | Tabla comparativa LLM vs. clasificador |
| T142 Sesión de aceptación con el cliente | Vie 30/10/2026 (ejecutada el 04/11/2026) | Jesús Gabriel Torres Daza | Resultados por caso, video |
| T143 Acta de la sesión | Sáb 31/10/2026 | Angie Nikol Ortiz Amaya | Acta para firma |
| T144 Reporte de defectos y corrección de críticos | Sáb 31/10/2026 | Daniela Garcia Peñaranda | `DEFECTOS_IT2.md` (hitos T149, T150) |
| T145 Regresión tras las correcciones | Mar 03/11/2026 | Daniela Garcia Peñaranda | Defectos verificados |
| T146 Sección de Prueba del FP-15 | Mié 04/11/2026 | Jesús Gabriel Torres Daza | FP-15 §4.2 (hito T151) |
| T147 Aprobación del reporte final | Mié 04/11/2026 | Zharick Nicole Hernandez Arevalo | Reporte aprobado (hito T148) |

---

## Criterios de Aceptación Globales
- Ningún recurso de una distribuidora es visible ni modificable desde otra: por id responde 404 y los listados solo traen lo propio.
- Las rutas `/api/plataforma/**` responden 403 a todo rol distinto de SUPERADMINISTRADOR, y el SUPERADMINISTRADOR recibe 403 en las rutas de negocio.
- Una distribuidora «Suspendido por mora» o «Inactivo / Archivado» bloquea de inmediato la API y la interfaz de todos sus usuarios con la pantalla informativa.
- Ninguna operación por voz que escribe datos se aplica sin confirmación, y la voz sigue funcionando si el LLM falla.
- Ningún endpoint responde 500 ante datos vacíos (sin historial, sin aprobados, presupuesto insuficiente).
