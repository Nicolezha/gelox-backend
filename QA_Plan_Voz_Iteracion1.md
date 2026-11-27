# Plan de Pruebas — Asistente de Voz, Iteración 1 GELOX

Tarea del Project: **T60** — Plan de pruebas de los módulos (T60-PR1 y T60-PR2) · 16/09/2026  
Responsable: Israel Bulla Rey (QA) · Casos de prueba: T61 (17/09–18/09/2026)  
Formato: el de `QA_Plan_Iteracion1.md` (tablas ID / Caso / Precondición / Pasos / Resultado esperado).  
Fuentes: Planificación de Alcance Global (HU-15, HU-16, HU-17 parcial), FP-15 §1.2–§1.4, §2.4–§2.6 y §4.1, Planeación técnica del Sprint 1 (E1).

Los casos de aceptación usan los IDs del FP-15 §4.1 (CP-01 … CP-27), que son los que se ejecutaron con el cliente. Los casos complementarios por API y los negativos que el FP-15 no cubre conservan los IDs de la tarea T61 (CP-46-nn … CP-53-nn).

## Alcance
RF46 (HU-15) — Activación y reconocimiento de voz  
RF47, RF48 (HU-15) — Registro de ventas por voz (ventanilla y rural)  
RF49 (HU-16) — Consulta de inventario por voz  
RF50, RF51 (HU-16) — Consultas financieras y cierre del día por voz  
RF52, RF53 (HU-17, parcial) — Generación y modificación de pedidos a proveedor por voz

Fuera de alcance: el asistente de recomendación de compras (RF54–RF57, Iteración 2). Esta iteración no toca el modelo de datos de los módulos existentes, solo agrega `comando_voz` (V2).

---

## 1. Tipos de prueba

| Tipo | Qué se comprueba | Herramienta | Tarea que lo ejecuta |
|------|------------------|-------------|----------------------|
| Unitaria / integración backend | Clasificador, resolvedor de productos, handlers, VozService y VozPendienteStore | JUnit 5 + Mockito, `./mvnw test` (`rf46` … `rf53`, `voz/`) | Cada desarrollador antes del PR; T51 |
| Funcional por API | `POST /api/voz/interpretar` y `POST /api/voz/confirmar`: intención, datos, confirmación, roles y errores | Postman `GELOX_RF46_RF53.postman_collection.json` | T62 |
| Funcional por UI | Botón de micrófono, «Hola GELOX», indicador «Escuchando…», transcripción parcial, resumen narrado y confirmación | Chrome y Edge sobre el servidor de desarrollo | T62 |
| Precisión de reconocimiento | Comandos con intención y datos correctos sobre comandos dichos | Frases de CP-PRE-01, tabla `comando_voz` | T62-PR3 |
| Tolerancia a ruido de mostrador | Misma precisión con ruido ambiente de ≈ 60 dB | Audio de mostrador a 1 m, sonómetro | T62-PR3 |
| Aceptación con el cliente | Ayde Arévalo dice los comandos (no el equipo) y usa las pantallas | Sesión grabada sobre el servidor de desarrollo | T63 |
| Regresión | Lo que cambió fuera del paquete voz sigue funcionando | `./mvnw -q test`, Postman completa y flujos manuales | T66 |

---

## 2. Entorno y datos de prueba

### 2.1 Entorno
| Elemento | Valor |
|----------|-------|
| Servidor de desarrollo | Backend `https://gelox-backend.onrender.com`, frontend `https://gelox-frontend.onrender.com` |
| Base de datos | Supabase (PostgreSQL) con V2 (`crear_comando_voz`) aplicada |
| Navegadores | Chrome y Edge (Web Speech API, es-CO). Firefox no soporta la API: solo se usa para comprobar que el botón no aparece |
| Micrófono | Micrófono del equipo del mostrador, con permiso concedido; un perfil con el permiso denegado para CP-02 |
| Pruebas locales | JDK 25, `sh ./mvnw test` desde Git Bash (H2 en modo PostgreSQL) |
| Zona horaria | «Hoy» se calcula con `America/Bogota` (Render corre en UTC) |

### 2.2 Usuarios por rol
| Rol | Usuario | Uso |
|-----|---------|-----|
| ADMINISTRADOR (Gerente) | Ayde Arévalo | Todas las intenciones; sesión con el cliente |
| ENCARGADO_INVENTARIO | Emerson Vera | Inventario, pedidos y el 403 de finanzas (CP-19) |
| ENCARGADO_VENTAS | Cuenta de ventas del equipo | Ventas e inventario; 403 en pedidos y finanzas |

Las credenciales no se escriben en este archivo; se comparten por el canal del equipo.

### 2.3 Datos semilla
| Dato | Valor | Casos |
|------|-------|-------|
| Minions Paleta | 16 cajas (320 unidades) antes de CP-06 | CP-05, CP-06, CP-08, CP-13, CP-22 |
| Aloha Mango Biche | En catálogo, bajo el mínimo | CP-07, CP-14, CP-22, CP-25, CP-26 |
| Jet Paleta Vainilla | $ 5.000 por unidad, caja de 14 ($ 70.000) | CP-09 … CP-12 |
| Polet Paleta Frutos | Stock 0, mínimo 120 | CP-15 |
| Cliente rural | María Elena Gómez (Vereda La Linda) | CP-09, CP-11, CP-12 |
| Destinatario no registrado | Pedro Ramírez | CP-10 |
| Producto inexistente | «Chocoramo» | CP-24 |
| Ventas del día | Ventanilla y rural registradas | CP-16 … CP-18 |
| Pedido a proveedor recibido hoy | Para que haya inversión del día | CP-17 |
| Cierre de caja del día | Registrado, con diferencia entre esperado y reportado | CP-20, CP-21 |
| Pedido PENDIENTE | El que se crea en CP-23 | CP-25 … CP-27 |

---

## Módulo Activación (RF46, HU-15)

### Casos de aceptación (FP-15 §4.1)
| ID | Caso de prueba | Precondición | Pasos | Resultado esperado |
|----|----------------|--------------|-------|--------------------|
| CP-01 | Activación con la palabra clave «Hola GELOX» | Escucha pasiva habilitada; Ayde Arévalo (ADMINISTRADOR) | Decir «Hola GELOX» sin tocar el botón (también «hola jelox» u «hola helox») | Detecta la palabra clave, abre el panel, activa la escucha y responde el comando siguiente |
| CP-02 | Sin micrófono: respaldo con formulario manual | Chrome con el permiso de micrófono denegado | Intentar activar la voz | Informa «No pudimos acceder al micrófono», reemplaza el botón por «Usa el formulario manual» y las pantallas siguen funcionando |
| CP-03 | Transcripción con ruido de fondo | Ruido de calle y radio cercano | Hablarle al asistente | Muestra «Escuchando…» y la transcripción parcial en tiempo real, fiel a lo dicho |
| CP-04 | Frase fuera de las intenciones | — | Decir «Cuéntame un chiste.» | No ejecuta nada y responde «No entendí el comando…» con ejemplos de frases válidas |

### Casos complementarios (T61)
| ID | Caso de prueba | Precondición | Pasos | Resultado esperado |
|----|----------------|--------------|-------|--------------------|
| CP-46-01 | Activación con clic | Micrófono permitido | Clic en el botón del header | Indicador «Escuchando…» y transcripción parcial |
| CP-46-03 | Texto y confianza al backend | — | Decir un comando y revisar la petición en DevTools | `POST /api/voz/interpretar` recibe el texto final y la confianza |
| CP-46-05 | Navegador sin soporte | Firefox | Abrir cualquier pantalla principal | El botón de micrófono no aparece; el resto funciona |
| CP-46-06 | Silencio | — | Activar y no hablar | Un reintento y luego pide repetir |
| CP-46-07 | Confianza menor al 70 % | Frase dicha con baja confianza | Hacer una consulta | Pide confirmación aunque sea una consulta |
| CP-46-09 | Sin token | — | `POST /api/voz/interpretar` sin Authorization | 401 |
| CP-46-10 | Micrófono para los 3 roles | Cuentas de los 3 roles | Iniciar sesión con cada una | Los 3 roles ven el micrófono |

---

## Módulo Ventas (RF47, RF48, HU-15)

### RF47 — Venta por ventanilla · Casos de aceptación (FP-15 §4.1)
| ID | Caso de prueba | Precondición | Pasos | Resultado esperado |
|----|----------------|--------------|-------|--------------------|
| CP-05 | Comando compuesto con resumen | Minions con stock | «Registra tres cajas de Minions, canal ventanilla.» | Muestra y narra «Venta ventanilla: 3 cajas de Minions Paleta. Total $90.000…¿Confirmas?» sin registrar nada |
| CP-06 | Confirmar y descontar inventario | CP-05; Minions con 16 cajas (320 u.) | Decir «Confirmar» | «Venta registrada por $90000.»; Minions queda en 13 cajas (260 u.); la venta aparece en reportes y dashboard |
| CP-07 | Cancelar por voz | — | «Registra cinco paletas de Mango Biche, canal ventanilla.» y «Cancelar» | «Comando cancelado.»; el stock no cambia y no hay venta nueva (igual para RF48 y RF52) |
| CP-08 | Stock insuficiente | Minions con 13 cajas | «Registra cincuenta cajas de Minions, canal ventanilla.» | «Stock insuficiente para Minions Paleta. Disponible: 260, solicitado: 1000»; no pide confirmación ni registra |

### RF47 — Casos complementarios (T61)
| ID | Caso de prueba | Precondición | Pasos | Resultado esperado |
|----|----------------|--------------|-------|--------------------|
| CP-47-05 | Productos agregados uno a uno | — | Dictar un producto y luego otro en frases separadas | El resumen acumula ambos productos |
| CP-47-06 | Producto ambiguo o inexistente | Dos productos con nombre parecido | Dictar el nombre ambiguo | Pide aclarar; no registra |
| CP-47-07 | Vence el plazo de confirmación | Resumen pendiente | No responder durante 45 s | La operación se cancela sola |
| CP-47-08 | Precio hablado distinto al catálogo | — | Dictar una venta con otro precio | El total usa el precio del catálogo |
| CP-47-09 | Rol sin permiso | ENCARGADO_INVENTARIO | Dictar una venta | 403 |
| CP-47-10 | El stock cambia antes de confirmar | Resumen pendiente; otra venta descuenta el stock | Decir «Confirmar» | 422; la venta no se registra |

### RF48 — Venta rural · Casos de aceptación (FP-15 §4.1)
| ID | Caso de prueba | Precondición | Pasos | Resultado esperado |
|----|----------------|--------------|-------|--------------------|
| CP-09 | Cliente rural existente con envío | María Elena Gómez registrada | «Registra venta rural de dos cajas de Jet Vainilla para María Elena Gómez, envío de diez mil pesos.» | Resumen: 2 cajas ($140.000), envío $10.000, total $150.000; no crea un cliente nuevo |
| CP-10 | Destinatario nuevo | Pedro Ramírez no registrado | «Registra venta rural de tres cajas de Jet Vainilla para Pedro Ramírez, envío de ocho mil pesos.» | Resumen con Pedro Ramírez: $210.000 + $8.000 = $218.000 |
| CP-11 | Cajas y unidades sueltas | — | «Registra venta rural de una caja y seis unidades de Jet Vainilla para María Elena Gómez, envío de diez mil pesos.» | Una sola línea de 1 caja y 6 unidades ($100.000), total $110.000 |
| CP-12 | Confirmar y consultar la venta rural | CP-11 | «Confirmar» y abrir /ventas/reportes con filtro Rural | «Pedido rural registrado por $110000.»; stock baja 20 unidades; aparece con cliente y total |

### RF48 — Casos complementarios (T61)
| ID | Caso de prueba | Precondición | Pasos | Resultado esperado |
|----|----------------|--------------|-------|--------------------|
| CP-48-05 | Destinatario ambiguo | Dos clientes rurales con nombre parecido | Dictar el nombre | Pide aclarar |
| CP-48-06 | Falta el destinatario | — | Dictar una venta rural sin destinatario | Pide el destinatario; no registra |
| CP-48-07 | Stock insuficiente | — | Dictar más cajas de las disponibles | Mensaje de stock insuficiente; no registra |
| CP-48-09 | Venta rural en la API | CP-12 | `GET /api/ventas/rural` | El pedido aparece con los datos confirmados |

---

## Módulo Inventario (RF49, HU-16)

### Casos de aceptación (FP-15 §4.1)
| ID | Caso de prueba | Precondición | Pasos | Resultado esperado |
|----|----------------|--------------|-------|--------------------|
| CP-13 | Existencias de un producto | — | «¿Cuántas cajas de Minions me quedan?» | Voz y pantalla: 13 cajas y 0 unidades, estado Normal; sin confirmación |
| CP-14 | Productos con stock bajo | Al menos un producto bajo el mínimo | «GELOX, muéstrame los productos con bajo stock.» | Solo productos BAJO_STOCK; narra hasta 5 nombres y la pantalla muestra todos |
| CP-15 | Alerta proactiva | Polet Paleta Frutos: stock 0, mínimo 120 | «¿Cuántas unidades de Polet Frutos quedan?» | Informa 0 cajas y 0 unidades y agrega la alerta de stock mínimo en voz y pantalla |

### Casos complementarios (T61)
| ID | Caso de prueba | Precondición | Pasos | Resultado esperado |
|----|----------------|--------------|-------|--------------------|
| CP-49-02 | Producto inexistente | — | Preguntar por un producto que no está en el catálogo | Informa que no lo encontró |
| CP-49-04 | Alerta al abrir sesión | Rol ENCARGADO_INVENTARIO; productos bajo el mínimo | Iniciar sesión | Alerta proactiva de stock bajo |
| CP-49-06 | Consulta desde Ventas | ENCARGADO_VENTAS | Preguntar existencias | Responde (consulta permitida) |

---

## Módulo Finanzas (RF50, RF51, HU-16)

### RF50 — Indicadores · Casos de aceptación (FP-15 §4.1)
| ID | Caso de prueba | Precondición | Pasos | Resultado esperado |
|----|----------------|--------------|-------|--------------------|
| CP-16 | Ventas del día por canal | Ventas registradas hoy | «¿Cuánto vendimos hoy?» | Narra el total y muestra ventanilla, rural y comerciantes; la suma coincide con el total |
| CP-17 | Ganancia por canal | Pedidos a proveedor recibidos hoy | «¿Cuánto ganamos hoy por canal?» | Utilidad neta = ingresos − inversión y margen (%) = utilidad ÷ inversión × 100, con desglose por canal |
| CP-18 | Ventas de la semana | — | «¿Cuánto vendimos esta semana?» | Semana de lunes a hoy; total igual al de /reportes para ese rango |
| CP-19 | Restricción al Administrador | ENCARGADO_INVENTARIO | «¿Cuánto vendimos hoy?» | 403 y «No tienes permiso para realizar esta acción»; ninguna cifra (igual para RF51) |

### RF50 — Casos complementarios (T61)
| ID | Caso de prueba | Precondición | Pasos | Resultado esperado |
|----|----------------|--------------|-------|--------------------|
| CP-50-04 | Ayer y este mes | — | «¿Cuánto vendimos ayer?» y «…este mes?» | Totales del rango correcto (también en `ConsultaFinancieraHandlerTest`) |
| CP-50-05 | Día sin ventas | Período sin ventas | Consultar ventas | $ 0, sin error |
| CP-50-07 | Consulta a las 20:00 hora Colombia | Después de las 19:00 | «¿Cuánto vendimos hoy?» | Devuelve el día de Colombia, no el de UTC |

### RF51 — Cierre del día · Casos de aceptación (FP-15 §4.1)
| ID | Caso de prueba | Precondición | Pasos | Resultado esperado |
|----|----------------|--------------|-------|--------------------|
| CP-20 | Cierre consolidado por canal | Cierre de caja del día registrado | «GELOX, cierra el día.» | «Resumen del Cierre del Día» con total, transacciones, ventas por canal y conciliación, narrado y en pantalla |
| CP-21 | Cierre con diferencia en caja | Esperado y reportado distintos | «GELOX, cierra el día.» | Narra explícitamente la diferencia; pantalla con esperado, reportado y diferencia |

### RF51 — Casos complementarios (T61)
| ID | Caso de prueba | Precondición | Pasos | Resultado esperado |
|----|----------------|--------------|-------|--------------------|
| CP-51-02 | Sin cierre registrado | Día sin cierre de caja | «GELOX, cierra el día.» | Indica conciliación pendiente |
| CP-51-04 | Totales iguales al reporte diario | CP-20 | Comparar con `GET /api/reportes/diario` | Mismos totales |
| CP-51-05 | Solo lectura | — | Pedir el cierre por voz | No crea un cierre de caja |

---

## Módulo Pedidos (RF52, RF53, HU-17 parcial)

### RF52 — Generar pedido · Casos de aceptación (FP-15 §4.1)
| ID | Caso de prueba | Precondición | Pasos | Resultado esperado |
|----|----------------|--------------|-------|--------------------|
| CP-22 | Varios productos y unidad heredada | — | «Genera un pedido con dos cajas de Minions y una de Mango Biche.» | Resumen: 2 cajas de Minions y 1 caja de Mango Biche (la segunda hereda «cajas»); espera confirmación |
| CP-23 | Confirmar: Pendiente y con Excel | CP-22 | «Confirmar» y descargar el Excel | Pedido Pendiente con fecha y productos; Excel de Nutresa con 2 en la fila de Minions y 1 en la de Mango Biche |
| CP-24 | Producto fuera del catálogo | «Chocoramo» no existe | «Genera un pedido con una caja de Chocoramo.» | «No encontré el producto…¿Puedes repetirlo?»; no crea pedido |

### RF52 — Casos complementarios (T61)
| ID | Caso de prueba | Precondición | Pasos | Resultado esperado |
|----|----------------|--------------|-------|--------------------|
| CP-52-06 | Cantidad 0 | — | Dictar un pedido con 0 cajas | Pide una cantidad válida; no crea pedido |
| CP-52-07 | Rol sin permiso | ENCARGADO_VENTAS | Dictar un pedido | 403 |
| CP-52-08 | Vence el plazo | Resumen pendiente | No responder durante 45 s | No se crea el pedido |

### RF53 — Modificar pedido · Casos de aceptación (FP-15 §4.1)
| ID | Caso de prueba | Precondición | Pasos | Resultado esperado |
|----|----------------|--------------|-------|--------------------|
| CP-25 | Agregar a un producto existente | Pedido pendiente de CP-23 | «Al pedido 1 agrégale una caja de Mango Biche.» y «Confirmar» | Mango Biche 1 → 2; el Excel descargado muestra 2 |
| CP-26 | Quitar un producto | Pedido con 2 de Minions y 2 de Mango Biche | «Al pedido 1 quítale una caja de Mango Biche.» y «Confirmar» | Mango Biche 2 → 1; Minions se conserva |
| CP-27 | Reemplazar la cantidad | Pedido con 2 de Minions | «En el pedido 1, cambia a 30 las cajas de Minions.» y «Confirmar» | Minions 2 → 30, sin sumar |

### RF53 — Casos complementarios (T61)
| ID | Caso de prueba | Precondición | Pasos | Resultado esperado |
|----|----------------|--------------|-------|--------------------|
| CP-53-02 | Agregar un producto nuevo al pedido | Pedido pendiente | Agregar un producto que no estaba | Nueva línea en el pedido |
| CP-53-05 | Sin pedido pendiente | No hay pedidos PENDIENTE | Pedir una modificación | Informa que no hay pedido pendiente |
| CP-53-06 | Producto que no está en el pedido | Pedido pendiente | Quitar un producto ausente | Informa que no está en el pedido |
| CP-53-07 | Pedido ya recibido | Pedido RECIBIDO | Pedir una modificación | 409; no se modifica |
| CP-53-08 | Quitar el último ítem | Pedido con un solo producto | Quitarlo | Rechazado; el pedido no queda vacío |

---

## Precisión y ruido de mostrador (RNF-5)

| ID | Caso de prueba | Precondición | Pasos | Resultado esperado |
|----|----------------|--------------|-------|--------------------|
| CP-PRE-01 | Precisión en silencio | 30 frases × 5 intenciones, 3 hablantes | Decir cada frase en silencio y revisar `comando_voz` | Precisión ≥ 90 % |
| CP-PRE-02 | Precisión con ruido de mostrador | Audio de mostrador a 1 m, ≈ 60 dB medido con sonómetro | Repetir las frases de CP-PRE-01 | Precisión ≥ 90 % |
| CP-PRE-03 | Latencia | DevTools (Network/Performance) | Medir desde el fin de la frase hasta la narración | < 3 s |

Precisión = comandos con intención y datos correctos ÷ comandos emitidos.

---

## Aceptación con el cliente

| Elemento | Detalle |
|----------|---------|
| Participante | Ayde Arévalo (Gerente, Mágico Sabor): pronuncia los comandos (no el equipo) |
| Guion | Casos CP-01 … CP-27: micrófono y «Hola GELOX», venta ventanilla y rural, inventario, finanzas y cierre, pedido y modificación con descarga del Excel |
| Preparación (T63-PR1) | Cuentas de los 3 roles, catálogo con stock y un producto bajo el mínimo, un pedido PENDIENTE, ventas del día y un cierre registrado; Chrome/Edge y micrófono de mostrador |
| Registro | Por caso: aprobado / rechazado / con observación, frase dicha, respuesta del sistema y comentarios de usabilidad; sesión grabada |
| Ejecución real | 10/10/2026, ejecutado por Jesús Gabriel Torres y Zharick Nicole Hernandez, verificado por Ayde Arévalo (FP-15 §4.1) |

---

## Regresión

| ID | Caso de prueba | Precondición | Pasos | Resultado esperado |
|----|----------------|--------------|-------|--------------------|
| CP-REG-V1 | Suite backend | Rama principal | `./mvnw -q test` | 0 fallos |
| CP-REG-V2 | Colección Postman | Servidor de desarrollo | Ejecutar `GELOX_RF46_RF53` completa | Todas en verde |
| CP-REG-V3 | Módulos tocados fuera de voz | Cuentas por rol | Pedido manual con Excel, detalle de pedido, entrada de mercancía, Navbar y alertas, venta manual de ventanilla y rural, reportes y cierre de caja, login por rol | Igual que antes de la iteración |
| CP-REG-V4 | Reprueba de defectos | Defectos corregidos en T65 | Repetir el caso original de cada defecto | Estado actualizado en `DEFECTOS_VOZ_Iteracion1.md` |

---

## Criterios, métricas y severidad (T60-PR2)

### Criterios de entrada
- Hitos T53 (RF46), T54 (RF47, RF48), T55 (RF49), T56 (RF50, RF51) y T57 (RF52, RF53) cumplidos.
- V2 aplicada en el servidor de desarrollo.
- `./mvnw test` en verde.

### Criterios de salida
- 0 defectos críticos abiertos.
- Todos los casos con resultado y evidencia.
- Acta de aprobación de pruebas firmada por el cliente (hito T69).

### Métricas (RNF-5)
| Métrica | Meta |
|---------|------|
| Precisión de reconocimiento con ruido < 60 dB | ≥ 90 % |
| Tiempo de transcripción y respuesta | < 3 s |
| Casos aprobados | 100 % de los casos de aceptación |

### Severidad de defectos
| Severidad | Cuándo |
|-----------|--------|
| Crítico | Bloquea un RF o afecta dinero o inventario |
| Mayor | Existe un rodeo (workaround) |
| Menor | Cosmético |

### Plantilla de reporte de resultados
| ID caso | RF | Frase dicha | Respuesta del sistema | Resultado (Aprobado / Falla / Bloqueado) | Evidencia (video y minuto) | Defecto |
|---------|----|-------------|-----------------------|------------------------------------------|----------------------------|---------|
| CP-01 | RF46 | | | | | |

Registro de defectos (`DEFECTOS_VOZ_Iteracion1.md`, T65): DEF-NN, RF, caso, descripción, pasos, severidad, evidencia y estado.

---

## Matriz de trazabilidad (T61-PR9)

| RF | Hito | Criterio de HU | Casos de aceptación (FP-15 §4.1) | Casos complementarios |
|----|------|----------------|----------------------------------|-----------------------|
| RF46 | T53 | HU-15: micrófono y «Hola GELOX», transcripción es-CO, respaldo sin Web Speech API, confianza < 70 % | CP-01 … CP-04 | CP-46-01, 03, 05, 06, 07, 09, 10 |
| RF47 | T54 | HU-15: venta por ventanilla con validación de stock, resumen y confirmación | CP-05 … CP-08 | CP-47-05 … 10 |
| RF48 | T54 | HU-15: venta rural con destinatario, presentaciones y envío | CP-09 … CP-12 | CP-48-05, 06, 07, 09 |
| RF49 | T55 | HU-16: existencias, filtro por estado de stock, alerta proactiva | CP-13 … CP-15 | CP-49-02, 04, 06 |
| RF50 | T56 | HU-16: ventas e ingresos del período y ganancia por canal | CP-16 … CP-19 | CP-50-04, 05, 07 |
| RF51 | T56 | HU-16: resumen ejecutivo del cierre del día | CP-20, CP-21 | CP-51-02, 04, 05 |
| RF52 | T57 | HU-17: pedido por voz con resumen, estado Pendiente y archivo del proveedor | CP-22 … CP-24 | CP-52-06, 07, 08 |
| RF53 | T57 | HU-17: modificar por voz un pedido Pendiente | CP-25 … CP-27 | CP-53-02, 05 … 08 |
| RNF-5 | — | Precisión y latencia | — | CP-PRE-01 … 03 |

---

## Cronograma y responsables

| Tarea | Fecha (Project) | Responsable | Entregable |
|-------|-----------------|-------------|------------|
| T60 Plan de pruebas | Mié 16/09/2026 | Israel Bulla Rey | Este archivo |
| T61 Casos de prueba y matriz | Jue 17/09 – Vie 18/09/2026 | Israel Bulla Rey | Casos y matriz de este archivo |
| T51 Revisión del paquete de Construcción | Vie 25/09/2026 | Zharick Nicole Hernandez Arevalo | `./mvnw test`, lint y build en verde |
| T62 Pruebas funcionales y de precisión | 26/09, 28/09 y 29/09/2026 | Daniela Garcia Peñaranda | Resultados, evidencia y tabla de precisión |
| T63 Sesión de aceptación con el cliente | Mié 30/09/2026 (ejecutada el 10/10/2026) | Jesús Gabriel Torres Daza | Resultados por caso, video |
| T64 Acta de la sesión | Jue 01/10/2026 | Jesús Gabriel Torres Daza | Acta para firma |
| T65 Defectos y corrección de críticos | Vie 02/10 – Sáb 03/10/2026 | Daniela Garcia Peñaranda | `DEFECTOS_VOZ_Iteracion1.md` (hitos T70, T71) |
| T66 Regresión tras las correcciones | Lun 05/10/2026 | Daniela Garcia Peñaranda | Regresión sin fallos |
| T67 Sección de Prueba del FP-15 | Mar 06/10/2026 | Jesús Gabriel Torres Daza | FP-15 §4.1 |
| T68 Aprobación del reporte final | Mar 06/10/2026 | Zharick Nicole Hernandez Arevalo | Reporte aprobado (hito T69) |

---

## Cambios posteriores que afectan la regresión

Los casos de arriba son los ejecutados el 10/10/2026 y no se reescriben. Al repetirlos como regresión (CP-LLM-01 de `QA_Plan_IT2.md` y tareas 214 y 222 de `QA_Plan_Iteracion3.md`), tener en cuenta:

| Caso | Qué cambió | Resultado esperado hoy |
|------|------------|------------------------|
| Todos | Iteración 2: LLM en el PLN | `comando_voz.metodo_clasificacion = LLM`; si el LLM falla o la cuota LLAMADAS_IA_MES del plan se agota, responde el clasificador de respaldo |
| CP-22 … CP-27 | Iteración 3 (RF63, RF67, tarea 194) | El pedido por voz infiere el proveedor de cada producto, genera un pedido por proveedor y lo dice en el resumen antes de confirmar; se exporta con la plantilla de su proveedor (Nutresa: el mismo Excel de `templates/catalogo.xlsx`) y puede traer `advertenciaMontoMinimo` |
| CP-05 … CP-12 | Iteración 3 (RF64) | La venta descuenta según la unidad y el factor de conversión del producto; con los productos de Mágico Sabor (cajas y unidades) el resultado no cambia |

---

## Criterios de Aceptación Globales
- Ninguna operación que escribe datos (venta, pedido, modificación) se aplica sin «Confirmar»; al cancelar o vencer el plazo de 45 s no cambia nada.
- Si la confianza de la transcripción es menor al 70 %, se pide confirmación aunque la operación sea una consulta.
- Cada intención respeta el rol: finanzas y cierre solo para ADMINISTRADOR; pedidos para ADMINISTRADOR y ENCARGADO_INVENTARIO; ventas para ADMINISTRADOR y ENCARGADO_VENTAS.
- Si no hay voz disponible, el sistema ofrece el formulario manual sin bloquear ninguna función.
- Las operaciones por voz dejan el mismo resultado que el formulario manual (stock, ventas, pedidos y Excel).
