# Plan de Pruebas — Iteración 3 GELOX

Tarea del Project: **209** — Plan de pruebas por módulo · Jue 26/11/2026  
Responsable: Israel Bulla Rey (QA)  
Formato: el de `QA_Plan_Iteracion1.md` (ID / Caso / Precondición / Pasos / Resultado esperado) con una columna **Tipo** (funcional, integración, aislamiento, cuotas, auditoría, regresión), como pide la tarea 209.  
Fuentes: Planificación de Alcance Global (HU-18 y HU-19, cierre), FP-15 versión del 23/11/2026: §1.2–§1.4, §1.6 y §2.14–§2.19 (HU-18, HU-19, RF63–RF71), y §4.1–§4.2, y Plan de trabajo — Iteración 3 GELOX (E3). Punto de partida: `QA_Plan_Iteracion1.md`, `QA_Plan_Voz_Iteracion1.md` y `QA_Plan_IT2.md`.

Numeración: los casos de aceptación son **CP-44 … CP-58** (tarea 210, `Casos_de_Prueba_Funcionales_RF63_RF71.docx`, planeados por Israel el 25/11/2026), que siguen la numeración del FP-15 (la Iteración 2 termina en CP-43) y van a FP-15 §4.3 (tarea 224). Los IDs CP-63-nn … CP-71-nn, CP-TEN, INT y CP-REG son casos complementarios por API y negativos para las tareas 211–214. La matriz de trazabilidad relaciona ambos.

> Este plan sigue el FP-15 del 23/11/2026, el Plan de trabajo E3 alineado al FP-15 y el documento de casos de la tarea 210, contrastados con el código de `develop` al 26/11/2026. Donde el documento de casos espera algo que el código no hace todavía, se anota en «Diferencias con el código» (sección antes de la matriz).

## Alcance
RF63, RF64 (HU-19, cierre) — Catálogo y proveedores multimarca  
RF65, RF66 (HU-19, cierre) — Políticas de crédito y liquidación  
RF67, RF68 (HU-19, cierre) — Exportación de pedidos y reportes  
RF69, RF70, RF71 (HU-18, cierre) — Panel y gobierno de la plataforma  
Transversal — Aislamiento entre distribuidoras, cuotas del plan, auditoría y cruces entre módulos (recomendación, voz, ventas, planillas)

Fuera de alcance: cambios funcionales a RF46–RF62 (solo entran como regresión).

---

## 1. Tipos de prueba

| Tipo | Qué se comprueba | Herramienta | Tarea |
|------|------------------|-------------|-------|
| Funcional | Cada criterio de aceptación de HU-18 y HU-19 por API y por pantalla | Postman `GELOX_IT3.postman_collection.json` + Chrome | 211 |
| Integración | Cruces entre módulos: catálogo ↔ inventario, proveedor ↔ recomendación, esquema ↔ liquidación, plantilla ↔ pedido (manual, recomendación y voz) | Chrome + Postman sobre Render | 212 |
| Aislamiento | La distribuidora B no ve ni edita proveedores, categorías, políticas, esquemas, plantillas, reportes ni auditoría de A | Postman con el token de cada distribuidora | 213 |
| Cuotas | Al alcanzar el límite de usuarios, productos, comerciantes o almacenamiento la operación responde 409 CUOTA_PLAN_ALCANZADA y no guarda nada; al pasar el límite de llamadas IA la voz sigue con el clasificador de respaldo (FP-15 §2.16.2) | Postman + pantalla, plan con cuotas pequeñas | 213 |
| Auditoría | Cada operación crítica deja su fila con fecha_hora, IP, distribuidora y usuario | `GET /api/plataforma/auditoria` + tabla `auditoria` | 213 |
| Regresión | RF46–RF62 siguen funcionando después de V9 y V10 y de los cambios a categorías, liquidación y exportación | `./mvnw test`, Postman `GELOX_IT2` y `GELOX_RF55_RF57`, flujos en navegador | 214, 222 |
| Aceptación con el cliente | El cliente ejecuta los casos sobre el despliegue | Sesión sobre `https://gelox-frontend.onrender.com` | 215, 223 |
| Unitaria backend | Reglas de cada RF (JUnit 5 + Mockito en `rf63` … `rf71`) | `./mvnw test` | Cada desarrollador antes del PR; 201 |

---

## 2. Ambiente

| Elemento | Valor |
|----------|-------|
| Ambiente de pruebas | Render: backend `https://gelox-backend.onrender.com`, frontend `https://gelox-frontend.onrender.com` |
| Versión | Rama `main` después del merge `develop → main` de la tarea 201 (Sáb 28/11) |
| Base de datos | Supabase con V9 (`V9__auditoria_y_cuotas_plan.sql`) y V10 (`V10__catalogo_credito_exportacion.sql`) aplicadas; respaldo tomado antes del merge |
| Frontend | `VITE_USAR_MOCK_IT2=false` configurado en Render antes del 28/11 |
| Local | JDK 25, `sh ./mvnw test` desde Git Bash; frontend con `npx vite build` |
| Exportaciones | Excel (Apache POI) y PDF (librería agregada en la tarea 190) abiertos en Excel/LibreOffice y en un lector de PDF |

---

## 3. Datos de prueba

### 3.1 Distribuidoras y cuentas
| Dato | Detalle | Para |
|------|---------|------|
| Distribuidora A: Mágico Sabor | Tenant piloto. Tras V10 tiene el proveedor «Nutresa» (1 día hábil, monto mínimo $0) en todos sus productos; categorías PALETAS, CONOS, FAMILIARES y VASOS en tabla; política de crédito por defecto con cupo $50.000, plazo 0 y SOLO_ALERTA; esquema MARGEN. Tras V9 tiene el plan migrado de su texto (PILOTO), sin límites. | Regresión y migración |
| Distribuidora B: de prueba | La crea Juan el sábado 28/11 en la mañana (tarea 213, paso 0); Jesús la revisa en 215. Con un plan de cuotas pequeñas. No sirve Helados Prueba (`helados-prueba`): quedó «Inactivo / Archivado» en CP-39 y ese estado es final. Se crea una nueva o se usa Prueba1 S.A.S (`prueba`) | Aislamiento, cuotas, aceptación |
| Cuentas | SUPERADMINISTRADOR; Gerente (ADMINISTRADOR) de A y de B; Encargado de Inventarios y Encargado de Ventas de B | Roles y 403 |

Las credenciales no se escriben en este archivo; se comparten por el canal del equipo.

### 3.2 Datos de la distribuidora B
| Dato | Valor propuesto |
|------|-----------------|
| Proveedor 1 | «Distribuciones Andina S.A.S», NIT 900123456, contacto «Carlos Rojas», teléfono 3000000001, correo de prueba; tiempo de entrega 2 días hábiles y monto mínimo $ 100.000 (CP-44 los cambia a 3 días hábiles y $ 150.000) |
| Proveedor 2 | «Lácteos del Norte Ltda», NIT 900654321, contacto «Rosa Pérez»; tiempo de entrega 1 día (no hábil), monto mínimo $ 50.000 |
| Categorías propias | «Bebidas» (orden 1) y «Snacks» (orden 2) |
| Unidad y factor | Unidad PAQ «Paquete»; factor 1 PAQ = 6 UN |
| Productos | Gaseosa Cola 400 ml (SKU GC-400, Marca Sur, Andina, Bebidas, 12 por caja, costo $ 1.200, comerciante $ 1.600, público $ 2.000, stock mínimo 24); Galleta Vainilla (GV-100, Dulce Sur, Lácteos del Norte, Snacks, 1 PAQ = 6 UN, $ 800 / $ 1.100 / $ 1.500); Agua Mineral 600 ml (AM-600, Marca Sur, Andina, Bebidas, 12 por caja, $ 700 / $ 1.000 / $ 1.300) |
| Comerciantes de B | 2 activos (para llegar al límite de Plan Mini en CP-57) |
| Comerciante de crédito (Mágico Sabor) | «Juan»: planilla anterior con saldo $ 30.000 vencida y la planilla del ejemplo 3.3. En CP-52 recibe la excepción cupo $ 50.000, plazo 0, BLOQUEAR; en CP-53 pasa a cupo $ 20.000, plazo 0, SOLO_ALERTA |
| Plantilla de exportación | Distribuciones Andina, modo GENERAR_FILAS: 1 CODIGO_PROVEEDOR «Código», 2 NOMBRE_PRODUCTO «Producto», 3 CANTIDAD_CAJAS «Cajas», 4 PRECIO_UNITARIO «Precio», 5 SUBTOTAL «Total», 6 TEXTO_FIJO «Cliente» = CLI-0457. Homologación: Gaseosa Cola 400 ml = AND-7781 (Agua Mineral sin código) |
| Plan con cuotas pequeñas | «Plan Mini», descripción «Plan de pruebas con cuotas pequeñas», precio mensual $ 50.000 (CP-56 lo edita a $ 60.000); máx. 3 usuarios (incluye al gerente titular), 3 productos, 2 comerciantes, almacenamiento 5 MB y 2 consultas de voz al LLM por mes. Asignado a B con motivo «Pruebas de cuotas» |
| Identidad | Logo, nombre comercial y color primario distintos a los de Mágico Sabor (para revisar los encabezados de Excel y PDF) |

### 3.3 Ejemplo para validar la liquidación (RF66)
Ejemplo de FP-15 §2.17.4.

Precios de Mágico Sabor: Aloha Paleta Niveles (1076473) precio al público $ 3.000 y al comerciante $ 2.400; Jet Paleta Vainilla (1051367) $ 5.000 y $ 4.600. Planilla del comerciante «Juan»: Niveles 24 despachadas y 4 devueltas (**20 vendidas**); Jet 14 despachadas y 2 devueltas (**12 vendidas**).

| | MARGEN | COMISION 18 % | TARIFA_FIJA $ 15.000 |
|---|---|---|---|
| Total vendido al público | $ 120.000 | $ 120.000 | $ 120.000 |
| Ganancia del comerciante (`ganancia_comerciante`) | $ 16.800 | $ 21.600 | $ 15.000 |
| Monto que se cobra (`total_ganancia`) | **$ 103.200** | **$ 98.400** | **$ 105.000** |

Crédito con MARGEN y la política de Mágico Sabor (cupo $ 50.000, plazo 0, SOLO_ALERTA): Juan debía $ 30.000; al liquidar entrega $ 80.000 de $ 103.200 → saldo de la planilla **$ 23.200**. Al día siguiente la deuda es **$ 53.200** (> cupo) y tiene planillas vencidas: el despacho se crea con la advertencia «Juan supera el cupo ($53.200 de $50.000) y tiene 2 planillas vencidas». Si abona **$ 25.000**, se aplica primero a la planilla más antigua y la deuda baja a **$ 28.200**.

Nota: `total_ganancia` sigue siendo lo que se cobra al comerciante (ingreso del canal comerciantes en los reportes); `ganancia_comerciante` es lo que gana él. Con MARGEN, `total_ganancia` es igual al cálculo de la Iteración 2.

---

## Módulo Catálogo y Proveedores Multimarca (RF63, RF64)

### RF63 — Proveedores por distribuidora
#### Casos de aceptación (tarea 210, `Casos_de_Prueba_Funcionales_RF63_RF71.docx`)
| ID | Caso de prueba | Tipo | Precondición | Pasos | Resultado esperado |
|----|----------------|------|--------------|-------|--------------------|
| CP-44 | Registrar, administrar y validar los proveedores de una distribuidora | Funcional + aislamiento | B sin proveedores propios; Mágico Sabor solo con Nutresa | Encargado de Inventarios de B en `/inventarios/proveedores`: registra Distribuciones Andina y Lácteos del Norte (§3.2); edita Andina a 3 días hábiles y $ 150.000; desactiva Lácteos, abre «Generar pedido» y la reactiva. Intentos: NIT 900123456 repetido, tiempo -1, monto -5.000, sin nombre. Encargado de Ventas abre la pantalla. GET del Nutresa de Mágico Sabor con el token de B | Dos proveedores propios de B con su contacto; el desactivado no aparece al generar pedidos; Mágico Sabor solo ve Nutresa (1 día hábil, $ 0). NIT repetido → 409 «Ya existe un proveedor con ese NIT»; negativos o sin nombre → 400; Encargado de Ventas → 403; proveedor de otra distribuidora → 404 «Proveedor no encontrado» |
| CP-47 | Pedido por proveedor con tiempo de entrega y monto mínimo | Funcional | Productos de CP-46; Andina con 3 días hábiles y mínimo $ 150.000 | «Generar pedido» con Andina: pedido 1 de 5 cajas de Gaseosa Cola ($ 72.000); pedido 2 de 13 cajas de Gaseosa Cola y 2 de Agua Mineral ($ 204.000) | Al elegir el proveedor solo se listan sus productos y su tiempo de entrega en días hábiles; el pedido de $ 72.000 se crea con el aviso de monto mínimo; el de $ 204.000 sin aviso; cada pedido queda con un solo proveedor |

#### Casos complementarios (API y negativos)
| ID | Caso de prueba | Tipo | Precondición | Pasos | Resultado esperado |
|----|----------------|------|--------------|-------|--------------------|
| CP-63-01 | Registrar proveedor | Funcional | Gerente o Encargado de Inventarios de B | POST /api/proveedores con nombre, NIT, `contacto_nombre`, `telefono`, `correo`, `tiempo_entrega_dias` 2, `dias_habiles` true y `monto_minimo_pedido` 300.000 | 201; aparece en `/inventarios/proveedores`; fila PROVEEDOR_CREAR |
| CP-63-02 | Editar proveedor | Funcional | CP-63-01 ejecutado | PUT /api/proveedores/{id} cambiando el tiempo de entrega | 200 y valor nuevo; fila PROVEEDOR_EDITAR |
| CP-63-03 | Desactivar y activar | Funcional | CP-63-01 ejecutado | PATCH /api/proveedores/{id}/activar?activo=false y luego PATCH …/activar | Cambia el estado; el inactivo no se ofrece al crear productos; fila PROVEEDOR_ACTIVAR |
| CP-63-04 | Datos inválidos | Funcional | — | POST sin nombre; sin tiempo de entrega o sin monto mínimo; con tiempo de entrega o monto negativos; con un NIT ya registrado; GET con Encargado de Ventas | 400; 400 (son obligatorios); 400; 409 «Ya existe un proveedor con ese NIT»; 403 |
| CP-63-05 | Proveedor de otra distribuidora | Aislamiento | Proveedor de A | GET y PUT por id con el token de B | 404 |
| CP-63-06 | Recomendación con tiempo de entrega por proveedor | Integración | Productos de B con Distribuciones Andina (3 días hábiles) y Lácteos del Norte (1 día no hábil) | POST /api/recomendaciones | El punto de reorden usa el tiempo de entrega de cada proveedor en días hábiles; Nutresa de Mágico Sabor usa 1 día hábil (el ejemplo de RF54 de FP-15 §2.10.6 se ajusta a este valor) |
| CP-63-07 | Un pedido por proveedor | Integración | Recomendación de CP-63-06 con aprobados de los dos proveedores | POST /api/recomendaciones/{id}/pedido | Se crea un pedido por proveedor |
| CP-63-08 | Pedido bajo el monto mínimo | Funcional | Distribuciones Andina con mínimo $ 150.000 | Generar un pedido de $ 72.000 (ver CP-47) | El pedido se crea y la respuesta trae `advertenciaMontoMinimo`; la pantalla muestra el aviso (no se bloquea) |

### RF64 — Catálogo multimarca, categorías y unidades
#### Casos de aceptación (tarea 210, `Casos_de_Prueba_Funcionales_RF63_RF71.docx`)
| ID | Caso de prueba | Tipo | Precondición | Pasos | Resultado esperado |
|----|----------------|------|--------------|-------|--------------------|
| CP-45 | Categorías propias, unidades de medida y factores de conversión | Funcional | B sin catálogo | Encargado de Inventarios de B: crea «Bebidas» (orden 1) y «Snacks» (orden 2) y repite «Bebidas»; crea la unidad PAQ «Paquete» y el factor 1 PAQ = 6 UN; intenta 1 PAQ = 0 UN, 1 PAQ = -2 UN y 1 UN = 1 UN | Categorías y unidad propias de B (no aparecen en Mágico Sabor); categoría repetida → 409; el factor 1 PAQ = 6 UN se guarda; factor 0, negativo o con la misma unidad → 400 |
| CP-46 | Catálogo multimarca con SKU propio, marca y proveedor | Funcional + aislamiento | Datos de CP-44 y CP-45 | «Nuevo producto»: Gaseosa Cola 400 ml, Galleta Vainilla y Agua Mineral 600 ml (§3.2). Intentos: SKU GC-400 repetido; producto con el proveedor Nutresa de Mágico Sabor; producto con la categoría «Paletas» de Mágico Sabor | Tres productos con SKU, marca, proveedor, categoría, costos y precios, agrupados por categoría y filtrables por proveedor y categoría; SKU repetido → 409; proveedor o categoría de otra distribuidora → 404 |

#### Casos complementarios (API y negativos)
| ID | Caso de prueba | Tipo | Precondición | Pasos | Resultado esperado |
|----|----------------|------|--------------|-------|--------------------|
| CP-64-01 | Crear producto multimarca | Funcional | Proveedor activo de B | POST /api/catalogo/productos con SKU, marca, proveedor, categoría, costo, precio, unidad base y factor 24 (`factores`) | 201; se ve en `/inventarios/catalogo`; fila PRODUCTO_CREAR |
| CP-64-02 | SKU repetido | Funcional | CP-64-01 ejecutado | Crear otro producto con el mismo SKU en B | 409 «El SKU ya está registrado en el catálogo» (sin SKU se usa el código técnico) |
| CP-64-03 | Crear categoría y unidad propias | Funcional | — | POST /api/categorias «Bebidas» (luego repetido y sin nombre); GET y POST /api/unidades | Categoría 201 y solo en B; repetida → 409; sin nombre → 400; Encargado de Ventas → 403 al crear. Una distribuidora recién aprovisionada ya trae las unidades UN, CJ y BOL |
| CP-64-04 | Editar producto | Funcional | CP-64-01 ejecutado | Cambiar proveedor, categoría y factor | Se guardan los valores nuevos |
| CP-64-05 | Venta con factor de conversión | Integración | Entrada de 2 cajas (factor 24 = 48 unidades) | Vender 30 unidades | El inventario queda en 18 unidades |
| CP-64-06 | Producto en la landing | Integración | CP-64-01 ejecutado | Abrir `/tienda/:subdominio` de B | El producto aparece en su categoría propia |
| CP-64-07 | Categorías migradas de Mágico Sabor | Regresión | V10 aplicada | GET /productos/conos, /familiares, /paletas y landing de `magicosabor` | Agrupan igual que antes de V10 |
| CP-64-08 | Producto o categoría de otra distribuidora | Aislamiento | Producto y categoría de A | GET y PUT por id con el token de B | 404 |
| CP-64-09 | Cuota de productos alcanzada | Cuotas | B con 3 productos y Plan Mini (máx. 3) | Crear el 4.º producto y reactivar un producto inactivo (PATCH /api/catalogo/productos/{id}/activar) | 409 «Cuota del plan alcanzada» en ambos; no se guarda |

---

## Módulo Políticas de Crédito y Liquidación (RF65, RF66)

### RF65 — Política de crédito por comerciante
El crédito de la venta rural queda fuera de alcance (FP-15, decisión D2); no se prueba.

#### Casos de aceptación (tarea 210, `Casos_de_Prueba_Funcionales_RF63_RF71.docx`)
| ID | Caso de prueba | Tipo | Precondición | Pasos | Resultado esperado |
|----|----------------|------|--------------|-------|--------------------|
| CP-52 | Política de crédito por distribuidora y excepción por comerciante | Funcional + aislamiento | Día siguiente a CP-51; política de Mágico Sabor cupo $ 50.000, plazo 0, SOLO_ALERTA; Juan debe $ 53.200 en 2 planillas vencidas | Gerente en la vista de crédito de Juan (`/ventas/comerciantes/:id/credito`, botón desde Mis comerciantes; la política por defecto en `/ajustes/liquidacion`): crea la excepción de Juan cupo $ 50.000, plazo 0, BLOQUEAR. Intentos: cupo -1, plazo -1, abono $ 0. Encargado de Ventas intenta cambiar la política por defecto. Gerente intenta una excepción para un comerciante de B | Se ve la política vigente (cupo $ 50.000, plazo 0, SOLO_ALERTA) y la deuda de Juan ($ 53.200, 2 vencidas); la excepción BLOQUEAR queda como política efectiva de Juan; negativos y abono no mayor que 0 → 400; Encargado de Ventas → 403 al cambiar; comerciante de otra distribuidora → 404 |
| CP-53 | Despacho bloqueado (BLOQUEAR) y con advertencia (SOLO_ALERTA) | Funcional | Juan con BLOQUEAR (CP-52) y deuda $ 53.200 | Despacho de 10 Niveles con la deuda; Gerente abona $ 25.000; repetir el despacho; Gerente cambia la excepción a cupo $ 20.000, plazo 0, SOLO_ALERTA; repetir el despacho; Gerente extiende 3 días la planilla de $ 23.200; Encargado de Ventas intenta lo mismo con otra | 1.º: 409 CREDITO_BLOQUEADO (deuda 53.200, cupo 50.000, planillasVencidas 2), sin planilla ni stock. Abono a la más antigua: deuda $ 28.200. 2.º: 409 CREDITO_BLOQUEADO por las 2 vencidas. 3.º: se crea con advertencias CUPO_SUPERADO y PLANILLA_VENCIDA («Juan supera el cupo ($ 28.200 de $ 20.000) y tiene 2 planillas vencidas»). La prórroga deja 1 vencida; Encargado de Ventas → 403 |

#### Casos complementarios (API y negativos)

| ID | Caso de prueba | Tipo | Precondición | Pasos | Resultado esperado |
|----|----------------|------|--------------|-------|--------------------|
| CP-65-01 | Definir política | Funcional | Gerente de B; comerciante registrado | PUT /api/credito/politica {cupo: 50000, plazoDias: 0, politica: SOLO_ALERTA} (política de la distribuidora, pantalla `/ajustes/liquidacion`); luego PUT /api/comerciantes/{id}/credito {cupo: 50000, plazoDias: 0, politica: BLOQUEAR} (excepción del comerciante) | 200; la sección Crédito del comerciante muestra cupo, plazo, política y deuda actual |
| CP-65-02 | Valores inválidos | Funcional | — | PUT con cupo o plazo negativos | 400 |
| CP-65-03 | Despacho que supera el cupo | Funcional | Comerciante con política BLOQUEAR y deuda cerca de $ 50.000 | Crear un despacho que supere el cupo | 409 {codigo: CREDITO_BLOQUEADO, deuda, cupo, planillasVencidas}; la planilla no se crea y la pantalla muestra el aviso |
| CP-65-04 | Planilla vencida con alerta | Funcional | Comerciante con política SOLO_ALERTA y planilla vencida | Crear un despacho | Se crea y la respuesta trae advertencias [{codigo y tipo: CUPO_SUPERADO \| PLANILLA_VENCIDA, mensaje}]; aviso en Planilla Diaria |
| CP-65-05 | Comerciante cerca del cupo | Funcional | Deuda cercana al cupo | Abrir Planilla Diaria | Aviso de cupo |
| CP-65-06 | Rol sin permiso | Funcional | Encargado de Ventas de B | PUT /api/credito/politica y PUT /api/comerciantes/{id}/credito | 403 (puede consultar con GET) |
| CP-65-07 | Política de otra distribuidora | Aislamiento | Comerciante de A | GET y PUT con el token de B | 404 |
| CP-65-08 | Cambio de política auditado | Auditoría | CP-65-01 ejecutado | Consultar `/api/plataforma/auditoria` filtrando por B | Fila con acción CREDITO_POLITICA_CAMBIAR |
| CP-65-09 | Política por defecto | Funcional | Comerciante sin excepción | GET /api/comerciantes/{id}/credito | Política efectiva = la de la distribuidora (cupo $ 50.000, plazo 0, SOLO_ALERTA) |
| CP-65-10 | Abono | Funcional | Ejemplo 3.3 (deuda $ 53.200) | POST /api/comerciantes/{id}/abonos con $ 25.000 como Gerente; repetir como Encargado de Ventas | 201; se aplica a la planilla más antigua; deuda $ 28.200; fila ABONO_REGISTRAR con comerciante y valor. Un abono mayor que la deuda → 400 «El abono supera la deuda actual»; Encargado de Ventas → 403 (los abonos son solo del Gerente) |
| CP-65-11 | Prórroga de fecha límite | Funcional | Planilla liquidada, vencida y con saldo | PATCH /api/planillas/{id}/fecha-limite {fechaLimitePago, motivo} como Gerente | 200; la planilla deja de contar como vencida; fila PLANILLA_CAMBIAR_FECHA_LIMITE. Sin motivo → 400; fecha igual o anterior a la actual → 400; más de 15 días adicionales → 400; planilla sin liquidar → 409; Encargado de Ventas → 403 |
| CP-65-12 | Quitar la excepción del comerciante | Funcional | Comerciante con excepción (CP-65-01) | DELETE /api/comerciantes/{id}/credito y luego GET | Vuelve a la política de la distribuidora; fila CREDITO_POLITICA_CAMBIAR |

### RF66 — Esquema de liquidación y planilla diaria
#### Casos de aceptación (tarea 210, `Casos_de_Prueba_Funcionales_RF63_RF71.docx`)
| ID | Caso de prueba | Tipo | Precondición | Pasos | Resultado esperado |
|----|----------------|------|--------------|-------|--------------------|
| CP-50 | Esquema de liquidación de comerciantes | Funcional | Planilla abierta de Juan (ejemplo 3.3) | Gerente en `/ajustes/liquidacion` cambia a COMISION 18 %, TARIFA_FIJA $ 15.000 y MARGEN; tras cada cambio el Encargado de Ventas carga las devoluciones (Niveles 4, Jet 2) y revisa el resumen sin confirmar. Intentos: COMISION 0, COMISION 101, TARIFA_FIJA -1; Encargado de Ventas cambia el esquema | Ganancia / cobro: COMISION $ 21.600 / $ 98.400; TARIFA_FIJA $ 15.000 / $ 105.000; MARGEN $ 16.800 / $ 103.200 (igual a la Iteración 2); valores fuera de rango → 400 y conservan el esquema; Encargado de Ventas → 403 |
| CP-51 | Liquidar y bloquear la planilla diaria | Funcional + aislamiento | Esquema MARGEN (CP-50) | Encargado de Ventas: intentos con devolución de Niveles 25 (despachadas 24), efectivo -1 y efectivo $ 110.000; luego liquida con devoluciones 4 y 2 y efectivo $ 80.000; intenta liquidar otra vez y editar el despacho. Gerente cambia a COMISION y vuelve a MARGEN. Encargado de Ventas de B intenta liquidar la planilla de Juan | Intentos → 400 sin cerrar. Planilla cerrada con cobro $ 103.200, ganancia $ 16.800, efectivo $ 80.000, saldo $ 23.200 y fecha límite del mismo día; comprobante con MARGEN e identidad de Mágico Sabor. Liquidar otra vez → 409 «…ya fue liquidada»; no admite edición; el cambio de esquema no la altera; desde B → 404 |

#### Casos complementarios (API y negativos)
| ID | Caso de prueba | Tipo | Precondición | Pasos | Resultado esperado |
|----|----------------|------|--------------|-------|--------------------|
| CP-66-01 | Configurar esquema | Funcional | Gerente de B | PUT /api/liquidacion/esquema con MARGEN, luego COMISION 18 % y luego TARIFA_FIJA 15.000; repetir con COMISION 0 o mayor que 100 y con TARIFA_FIJA negativa | 200 cada vez y fila ESQUEMA_LIQUIDACION_CAMBIAR; pantalla `/ajustes/liquidacion` muestra el vigente. Comisión fuera de (0, 100] o tarifa negativa → 400 |
| CP-66-02 | Liquidar con MARGEN | Integración | Planilla del ejemplo 3.3; esquema MARGEN | Liquidar la planilla | Se cobra (`total_ganancia`) $ 103.200 y ganancia del comerciante $ 16.800 |
| CP-66-03 | Liquidar con COMISION | Integración | Misma planilla; esquema COMISION 18 % | Liquidar | Se cobra $ 98.400 y ganancia del comerciante $ 21.600 |
| CP-66-04 | Liquidar con TARIFA_FIJA | Integración | Misma planilla; TARIFA_FIJA 15.000 | Liquidar | Se cobra $ 105.000 y ganancia del comerciante $ 15.000 |
| CP-66-05 | Esquema por defecto | Regresión | Mágico Sabor sin esquema configurado | Liquidar una planilla | Mismo resultado que en la Iteración 2 |
| CP-66-06 | Planilla bloqueada | Funcional | Planilla liquidada | Intentar editarla | Rechazado; la planilla queda bloqueada |
| CP-66-07 | Rol sin permiso | Funcional | Encargado de Ventas de B | PUT /api/liquidacion/esquema | 403 |
| CP-66-08 | Esquema de otra distribuidora | Aislamiento | Esquemas distintos en A y B | GET /api/liquidacion/esquema con cada token | Cada una ve solo el suyo |
| CP-66-09 | Liquidación auditada | Auditoría | CP-66-02 ejecutado | Consultar la auditoría | Fila PLANILLA_LIQUIDAR |
| CP-66-10 | Efectivo recibido parcial | Funcional | Planilla del ejemplo 3.3; esquema MARGEN | Liquidar con `efectivoRecibido` 80.000; repetir sin `efectivoRecibido`; repetir con un valor mayor que el total y con uno negativo | Saldo $ 23.200 y `fecha_limite_pago` = fecha de la planilla + plazo (plazo 0: el mismo día de la planilla). Sin `efectivoRecibido` se toma igual al total cobrado: saldo 0 y sin fecha límite. Mayor que el total o negativo → 400 |
| CP-66-11 | Producto sin precio al público | Funcional | Planilla con un producto sin precio al público; esquema MARGEN | Liquidar la planilla | Ganancia 0 en ese ítem, el monto cobrado no cambia y la respuesta trae `advertencias` [{codigo y tipo: SIN_PRECIO_PUBLICO, mensaje}] |

---

## Módulo Exportación de Pedidos y Reportes (RF67, RF68)

### RF67 — Plantilla de exportación por proveedor
#### Casos de aceptación (tarea 210, `Casos_de_Prueba_Funcionales_RF63_RF71.docx`)
| ID | Caso de prueba | Tipo | Precondición | Pasos | Resultado esperado |
|----|----------------|------|--------------|-------|--------------------|
| CP-48 | Plantilla de exportación y códigos de homologación | Funcional | Andina sin plantilla en B | Encargado de Inventarios de B en `/inventarios/plantillas`: plantilla GENERAR_FILAS de §3.2 y homologación Gaseosa Cola = AND-7781; revisa la vista previa. Intentos: sin columnas; dos columnas con orden 2; AND-7781 para Agua Mineral. Encargado de Ventas abre la pantalla | Seis columnas en orden con sus encabezados y vista previa; AND-7781 solo en Gaseosa Cola; sin columnas → 400; orden repetido y código repetido → 409; Encargado de Ventas → 403 |
| CP-49 | Archivo del pedido con la plantilla del proveedor | Integración + regresión | Pedido 2 de CP-47 en B; pedido a Nutresa en Mágico Sabor | Cerrar el pedido de Andina y descargar el archivo; en Mágico Sabor, cerrar un pedido a Nutresa de 10 cajas de Aloha Paleta Niveles (1076473) y descargarlo | Andina: archivo .xlsx con encabezados Código, Producto, Cajas, Precio, Total, Cliente, una fila por ítem, AND-7781 y 13 cajas en Gaseosa Cola y CLI-0457 en Cliente; Agua Mineral no se omite y se informa sin código. Nutresa: catálogo base con encabezados en la fila 4, 10 en la fila 1076473, filas sin pedido en blanco y Mágico Sabor en A1 |

#### Casos complementarios (API y negativos)
| ID | Caso de prueba | Tipo | Precondición | Pasos | Resultado esperado |
|----|----------------|------|--------------|-------|--------------------|
| CP-67-01 | Guardar plantilla | Funcional | Distribuciones Andina | PUT /api/proveedores/{id}/plantilla en modo GENERAR_FILAS con orden de columnas, encabezados y códigos de homologación | 200; vista previa en `/inventarios/plantillas` con las primeras filas; fila PLANTILLA_EXPORTACION_CAMBIAR en la auditoría |
| CP-67-02 | Plantilla sin columnas | Funcional | — | PUT de una plantilla GENERAR_FILAS sin columnas; sin modo; columna sin encabezado | 400 «Agrega al menos una columna a la plantilla»; 400; 400 |
| CP-67-09 | Vista previa sin guardar | Funcional | Distribuciones Andina | POST /api/proveedores/{id}/plantilla/vista-previa con una plantilla modificada y luego GET …/plantilla | Devuelve encabezados, filas y productos sin código; el GET sigue con la plantilla guardada |
| CP-67-10 | Códigos de homologación | Funcional | Productos de Distribuciones Andina | PUT /api/proveedores/{id}/homologaciones con códigos; repetir con el mismo código en dos productos y con un producto dos veces | 200; mismo código en dos productos → 409; producto repetido → 400; código vacío quita la homologación |
| CP-67-11 | Rol sin permiso | Funcional | Encargado de Ventas de B | GET y PUT /api/proveedores/{id}/plantilla | 403 (plantillas: Gerente y Encargado de Inventarios) |
| CP-67-03 | Pedido manual con plantilla | Integración | CP-67-01 ejecutado | Crear un pedido manual a Distribuciones Andina y abrir el Excel | Columnas en el orden y con los encabezados de la plantilla, y códigos de homologación |
| CP-67-04 | Pedido desde recomendación | Integración | CP-67-01 y CP-63-07 ejecutados | Abrir el Excel del pedido de Distribuciones Andina | Exportado con su plantilla |
| CP-67-05 | Pedido por voz | Integración | CP-67-01 ejecutado | Dictar un pedido de un producto de Distribuciones Andina y confirmar | El asistente infiere el proveedor de cada producto, lo dice en el resumen y genera un pedido por proveedor; el Excel usa su plantilla |
| CP-67-06 | Plantilla de Nutresa | Regresión | Nutresa en Mágico Sabor en modo RELLENAR_CATALOGO (`templates/catalogo.xlsx`, fila de encabezados 4) | Crear un pedido y abrir el Excel | Mismo Excel de hoy; `PedidoProveedorServiceExportarTest` en verde |
| CP-67-07 | Plantilla de otra distribuidora | Aislamiento | Proveedor de A | GET y PUT /api/proveedores/{id}/plantilla con el token de B | 404 |
| CP-67-08 | Producto sin código para el proveedor | Funcional | Producto sin código de homologación | Crear un pedido con ese producto | El pedido se exporta igual y la respuesta lista los productos sin código |

### RF68 — Reportes financieros en Excel y PDF
#### Casos de aceptación (tarea 210, `Casos_de_Prueba_Funcionales_RF63_RF71.docx`)
| ID | Caso de prueba | Tipo | Precondición | Pasos | Resultado esperado |
|----|----------------|------|--------------|-------|--------------------|
| CP-54 | Exportar el reporte financiero en Excel y PDF | Funcional | Mágico Sabor con operaciones del 01/11 al 27/11/2026 | Gerente en `/reportes`, RANGO 01/11–27/11: Excel con las cuatro secciones; PDF solo con resumen y rentabilidad por canal. Intentos: sin sección; rango invertido. Encargado de Inventarios abre `/reportes` | Excel `reporte-financiero-magicosabor-{desde}-{hasta}.xlsx` con las hojas «Resumen», «Inversión vs ingresos», «Rentabilidad por canal» y «Top 5 comerciantes», encabezado y valores de la pantalla; PDF con logo, nombre y color, solo las dos secciones, número de página y «Generado por GELOX»; pesos con miles y margen con un decimal; sin sección o rango invertido → 400; Encargado de Inventarios → 403 |

#### Casos complementarios (API y negativos)
| ID | Caso de prueba | Tipo | Precondición | Pasos | Resultado esperado |
|----|----------------|------|--------------|-------|--------------------|
| CP-68-01 | Exportar a Excel | Funcional | Período con ventas y pedidos | Botón «Exportar Excel» (GET /api/reportes/financiero/excel?tipo=RANGO&fechaInicio&fechaFin; `tipo` = DIA, SEMANA, MES, ANIO o RANGO) | Descarga `reporte-financiero-{subdominio}-{desde}-{hasta}.xlsx` con las hojas «Resumen», «Inversión vs ingresos», «Rentabilidad por canal» y «Top 5 comerciantes», con los mismos valores de la pantalla Reportes |
| CP-68-02 | Exportar a PDF | Funcional | Mismo período | Botón «Exportar PDF» (GET /api/reportes/financiero/pdf) | Mismos valores que el Excel y la pantalla |
| CP-68-03 | Identidad en el encabezado | Funcional | Identidades distintas en A y B | Exportar en cada distribuidora | Logo, nombre y color de cada una |
| CP-68-04 | Período sin datos | Funcional | Período sin ventas | Exportar Excel y PDF | Archivos con valores en cero, sin 500 |
| CP-68-05 | Período inválido | Funcional | — | Exportar con inicio > fin | 400 |
| CP-68-06 | Rol sin permiso | Funcional | Encargado de Inventarios | GET …/excel y …/pdf | 403 |
| CP-68-07 | Datos de otra distribuidora | Aislamiento | Ventas en A y B | Exportar con el token de B | Solo datos de B |
| CP-68-08 | Secciones elegidas | Funcional | Período con datos | Exportar con `secciones=RESUMEN,TOP5_COMERCIANTES` (valores: RESUMEN, INVERSION_INGRESOS, RENTABILIDAD_CANAL, TOP5_COMERCIANTES) | Solo esos bloques, más el encabezado (que siempre va) |

---

## Módulo Panel y Gobierno de la Plataforma (RF69, RF70, RF71)

### RF69 — Panel del Superadministrador
#### Casos de aceptación (tarea 210, `Casos_de_Prueba_Funcionales_RF63_RF71.docx`)
| ID | Caso de prueba | Tipo | Precondición | Pasos | Resultado esperado |
|----|----------------|------|--------------|-------|--------------------|
| CP-55 | Panel de la plataforma con indicadores consolidados | Funcional | A y B con operaciones de CP-44 a CP-54 | Equipo RESET en `/plataforma/panel` del 01/11 al 27/11/2026; selecciona B y cambia al 01/01–31/01/2027 (sin operaciones). El Gerente de Mágico Sabor abre `/plataforma/panel` | Distribuidoras por estado, usuarios activos, ventas y pedidos, almacenamiento y llamadas al LLM, en total y por distribuidora, con el uso de cada cuota frente al plan; el detalle de B solo con conteos y sumas; período sin operaciones en cero; Gerente → 403 |

#### Casos complementarios (API y negativos)
| ID | Caso de prueba | Tipo | Precondición | Pasos | Resultado esperado |
|----|----------------|------|--------------|-------|--------------------|
| CP-69-01 | Indicadores consolidados | Funcional | Superadministrador; A y B con datos | GET /api/plataforma/panel?desde&hasta y abrir `/plataforma/panel` | Distribuidoras por estado, usuarios activos por distribuidora, ventas y pedidos del período, almacenamiento, uso de IA y uso de cada cuota frente al límite del plan por distribuidora; incluye A y B |
| CP-69-02 | Cifras contra la base | Funcional | CP-69-01 ejecutado | Contar a mano en la base cada indicador | Coinciden; uso de IA = `comando_voz` con `metodo_clasificacion = LLM` |
| CP-69-03 | Período sin actividad | Funcional | — | GET con un período sin datos | Ventas y pedidos en cero, sin 500 |
| CP-69-04 | Gerente en el panel | Funcional | Gerente de A | Abrir `/plataforma/panel` y llamar al endpoint | 403 |
| CP-69-05 | Almacenamiento | Funcional | Archivos subidos antes y después de la Iteración 3 | GET /api/plataforma/panel | Cuenta solo los archivos registrados desde la Iteración 3 (los anteriores cuentan 0) |

### RF70 — Planes de suscripción y cuotas
#### Casos de aceptación (tarea 210, `Casos_de_Prueba_Funcionales_RF63_RF71.docx`)
| ID | Caso de prueba | Tipo | Precondición | Pasos | Resultado esperado |
|----|----------------|------|--------------|-------|--------------------|
| CP-56 | Crear, editar y asignar planes de suscripción | Funcional | B con plan sin límites, 3 usuarios, 3 productos y 2 comerciantes | Equipo RESET en `/plataforma/planes`: crea «Plan Mini» (§3.2) y edita el precio a $ 60.000. Intentos: nombre repetido, productos -1, eliminar el plan ya asignado. Asigna Plan Mini a B con motivo «Pruebas de cuotas». El Gerente de B abre `/plataforma/planes` | Plan creado y editado (vacío = sin límite); nombre repetido → 409, negativo → 400, eliminar con distribuidoras → 409; el detalle de B muestra usuarios 3 de 3, productos 3 de 3 y comerciantes 2 de 2 sin deshabilitar nada; Gerente → 403 |
| CP-57 | Cuota alcanzada en usuarios, productos, comerciantes, almacenamiento y voz | Cuotas | B con Plan Mini en el límite (CP-56) y sin consultas de voz en el mes | Gerente de B crea un 4.º usuario; Encargado de Inventarios crea el 4.º producto (JN-100); Gerente crea un 3.er comerciante; se sube una imagen de 6 MB y otra de 1 MB; tres veces «¿Qué me recomiendas pedir?» por voz; Equipo RESET revisa el detalle de B en el panel | Usuario, producto y comerciante → 409 CUOTA_PLAN_ALCANZADA con `cuota`, `limite` y `usado` (el gerente titular cuenta); la pantalla muestra «Cuota del plan alcanzada» con usado y límite; nada se crea. 6 MB → 409 ALMACENAMIENTO_MB (limite 5); 1 MB se sube. Consultas 1 y 2 con LLM; la 3.ª con el clasificador de respaldo y un aviso, sin 409. El panel muestra 2 llamadas de IA y el almacenamiento de B |

#### Casos complementarios (API y negativos)
| ID | Caso de prueba | Tipo | Precondición | Pasos | Resultado esperado |
|----|----------------|------|--------------|-------|--------------------|
| CP-70-01 | Crear plan | Funcional | Superadministrador | POST /api/plataforma/planes con nombre (máx. 30), descripción, precio mensual y cuotas de usuarios, productos, comerciantes, almacenamiento MB y llamadas IA/mes; una cuota vacía = sin límite | 201; aparece en `/plataforma/planes` |
| CP-70-02 | Editar plan | Funcional | CP-70-01 ejecutado | PUT /api/plataforma/planes/{id} | 200 y valores nuevos |
| CP-70-03 | Asignar plan | Funcional | Plan y distribuidora B | PATCH /api/plataforma/distribuidoras/{id}/plan {planId, motivo} y alta de una distribuidora con `planId` | El plan queda asignado y se ve en el detalle con el uso de cuotas. El mismo plan → 409; sin motivo → 400; plan inexistente → 404; `planId` inexistente en el alta → 400 |
| CP-70-04 | Cuota de usuarios | Cuotas | B con 3 usuarios activos contando al gerente titular, y plan de máx. 3 | Crear el usuario n.º 4; luego deshabilitar uno, crear otro y volver a habilitar el deshabilitado | 409 «Cuota del plan alcanzada» en el n.º 4 y en la rehabilitación; no se crea en Firebase ni en la base |
| CP-70-05 | Cuota de productos | Cuotas | Ver CP-64-09 | Crear el 4.º producto | 409 |
| CP-70-06a | Cuota de IA | Cuotas | Llamadas IA del mes en el límite (2 de Plan Mini) | Hacer una consulta por voz más | 200: la voz sigue funcionando con el clasificador de respaldo (OpenNLP) y `comando_voz.metodo_clasificacion` deja de ser LLM; no hay 409. La respuesta de `/api/voz/interpretar` trae `aviso` «Se alcanzó la cuota mensual de consultas de voz con IA. El asistente sigue con el reconocimiento básico.» y el asistente lo muestra encima de la respuesta |
| CP-70-06b | Cuota de almacenamiento | Cuotas | Plan Mini con 5 MB | Subir imágenes de 5 MB o menos (límite por archivo: `spring.servlet.multipart.max-file-size=5MB`) hasta que la siguiente haga pasar el total de 5 MB; luego una de 1 MB con espacio libre | La que hace pasar los 5 MB → 409 CUOTA_PLAN_ALCANZADA (cuota ALMACENAMIENTO_MB, limite 5) y no se guarda; con espacio, 1 MB se sube y el uso de B aumenta 1 MB. Un archivo de más de 5 MB no llega a la cuota (ver «Diferencias con el código») |
| CP-70-07 | Planes migrados | Regresión | V9 aplicada | Operar con Mágico Sabor | Sigue operando con el plan PILOTO migrado de su texto, sin límites (máximos nulos) |
| CP-70-08 | Gerente en planes | Funcional | Gerente de A | GET /api/plataforma/planes | 403 |
| CP-70-09 | Cambio de plan auditado | Auditoría | CP-70-03 ejecutado | Consultar la auditoría | Fila PLAN_ASIGNAR con plan anterior, nuevo y motivo |
| CP-70-10 | Cuota de comerciantes | Cuotas | B con 2 comerciantes activos y Plan Mini (máx. 2) | Crear el 3.º; luego desactivar uno, crear otro y volver a activar el desactivado (PATCH /api/comerciantes/{id}/estado) | 409 en el 3.º y en la reactivación; no se guarda |
| CP-70-11 | Cuerpo del 409 | Cuotas | CP-70-04 | Revisar la respuesta y la pantalla | `{error: "Cuota del plan alcanzada: usuarios (3 de 3).", codigo: "CUOTA_PLAN_ALCANZADA", cuota: "USUARIOS", limite: 3, usado: 3}` (en ALMACENAMIENTO_MB, `limite` y `usado` en MB), como el ejemplo de FP-15 §2.16.2; la pantalla muestra «Cuota del plan alcanzada: usuarios (3 de 3). Contacta al Superadministrador para cambiar de plan.» |
| CP-70-12 | Validaciones del plan | Funcional | Plan existente con distribuidoras | Crear un plan con nombre repetido; con cuota o precio negativo; borrar un plan con distribuidoras | Nombre repetido → 409; cuota o precio negativo → 400; borrar un plan con distribuidoras → 409 |
| CP-70-13 | Bajar a un plan menor | Funcional | B con más usuarios que el límite del plan nuevo | Asignar a B un plan con menos usuarios de los que tiene | Se asigna; la respuesta y el detalle de PLAN_ASIGNAR traen `cuotasExcedidas`; no se deshabilita nada; las altas nuevas responden 409 |

### RF71 — Log de auditoría
#### Casos de aceptación (tarea 210, `Casos_de_Prueba_Funcionales_RF63_RF71.docx`)
| ID | Caso de prueba | Tipo | Precondición | Pasos | Resultado esperado |
|----|----------------|------|--------------|-------|--------------------|
| CP-58 | Registro y consulta del log de auditoría | Auditoría + funcional | CP-38 (gerente de Helados Prueba con la distribuidora suspendida) y CP-44 a CP-57 ejecutados | Equipo RESET en `/plataforma/auditoria` sin filtros; luego filtra por distribuidora B, acción PLAN_ASIGNAR, acción ACCESO_BLOQUEADO, usuario Encargado de Inventarios de B y fecha del día; intenta un rango invertido. El Gerente de Mágico Sabor abre `/plataforma/auditoria` | Lista más reciente primero con DISTRIBUIDORA_APROVISIONAR, USUARIO_CREAR, PROVEEDOR_CREAR, PRODUCTO_CREAR, PLANTILLA_EXPORTACION_CAMBIAR, ESQUEMA_LIQUIDACION_CAMBIAR, PLANILLA_LIQUIDAR, CREDITO_POLITICA_CAMBIAR, ABONO_REGISTRAR, PLANILLA_CAMBIAR_FECHA_LIMITE, PLAN_CREAR, PLAN_EDITAR, PLAN_ASIGNAR, SESION_INICIAR y SESION_CERRAR; cada fila con fecha y hora, IP, distribuidora (vacía en plataforma), nombre, correo y rol, y detalle sin contraseñas; cada filtro funciona; el intento de la gerente de Helados Prueba aparece como ACCESO_BLOQUEADO con el estado de la distribuidora en el detalle; las demás operaciones rechazadas (SKU repetido, bloqueos de crédito, cuotas) no dejan fila; rango invertido → 400; Gerente → 403 |

#### Casos complementarios (API y negativos)
| ID | Caso de prueba | Tipo | Precondición | Pasos | Resultado esperado |
|----|----------------|------|--------------|-------|--------------------|
| CP-71-01 | Operaciones de usuarios auditadas | Auditoría | Gerente de B | Crear, editar, deshabilitar y habilitar un usuario | Filas USUARIO_CREAR, USUARIO_EDITAR, USUARIO_DESHABILITAR y USUARIO_HABILITAR con fecha_hora, ip, tenant_id, usuario_id, rol, accion, entidad, entidad_id y detalle (JSON) |
| CP-71-02 | Aprovisionamiento y cambio de estado auditados | Auditoría | Superadministrador | Registrar una distribuidora, pasarla a «Suspendido por mora» y reactivarla | Filas DISTRIBUIDORA_APROVISIONAR y DISTRIBUIDORA_CAMBIAR_ESTADO; tenant_id vacío en las acciones del Superadministrador |
| CP-71-03 | Operación fallida | Auditoría | Cuota alcanzada (CP-70-04) | Intentar crear el usuario | No queda fila de operación exitosa |
| CP-71-04 | Consulta con filtros | Funcional | Filas de A y B | GET /api/plataforma/auditoria con filtros distribuidora, usuario, desde, hasta y acción; pantalla `/plataforma/auditoria` | Solo las filas que cumplen el filtro, paginadas; GET /api/plataforma/auditoria/acciones y /auditoria/usuarios llenan los filtros |
| CP-71-05 | Gerente en auditoría | Funcional | Gerente de A | GET /api/plataforma/auditoria | 403 |
| CP-71-06 | Seguridad auditada | Auditoría | Usuario de B; distribuidora suspendida | Iniciar (POST /api/auth/verificar) y cerrar sesión (POST /api/auth/cerrar-sesion), cambiar la contraseña, intentar entrar con una distribuidora suspendida y llamar con un token inválido | Filas SESION_INICIAR, SESION_CERRAR, CONTRASENA_CAMBIAR (el detalle no trae la contraseña), ACCESO_BLOQUEADO (403) y TOKEN_INVALIDO (401) |
| CP-71-07 | Otras acciones | Auditoría | Gerente de B y Superadministrador | Crear un producto, reenviar el acceso al gerente y cambiar la identidad visual | Filas PRODUCTO_CREAR, DISTRIBUIDORA_REENVIAR_ACCESO e IDENTIDAD_ACTUALIZAR. La lista completa de acciones es la de `gobierno/AccionAuditoria.java` (26 códigos, la misma que devuelve /auditoria/acciones) |
| CP-71-08 | Rango de fechas inválido | Funcional | Superadministrador | GET /api/plataforma/auditoria con desde > hasta | 400 |

---

## Aislamiento entre distribuidoras

Se ejecuta con los Gerentes de A y B (tarea 213). Base automatizada: `src/test/java/com/gelox/backend/tenant/AislamientoTenantTest.java`. Cualquier fuga es severidad **crítica**. Los casos por RF (CP-63-05, CP-64-08, CP-65-07, CP-66-08, CP-67-07, CP-68-07) se resumen en esta matriz.

| ID | Caso de prueba | Tipo | Precondición | Pasos | Resultado esperado |
|----|----------------|------|--------------|-------|--------------------|
| CP-TEN-13 | Matriz de recursos nuevos | Aislamiento | Recursos de A creados | Con el token de B: GET/PUT por id de proveedor, producto, categoría, factor, política de crédito, abonos, esquema de liquidación, planillas (PATCH fecha límite) y plantilla de A | 404 en todos |
| CP-TEN-14 | Listados | Aislamiento | A y B con datos | Listar proveedores, categorías y productos con cada token | Cada una ve solo lo propio |
| CP-TEN-15 | Reportes exportados | Aislamiento | Ventas en A y B | Exportar el reporte financiero con cada token | Solo datos propios |
| CP-TEN-16 | Gobierno fuera del alcance del Gerente | Aislamiento | — | Gerente de B en `/api/plataforma/panel`, `/planes` y `/auditoria` | 403 |

(La numeración CP-TEN continúa la de `QA_Plan_IT2.md`, que llega a CP-TEN-12.)

---

## Cruces entre módulos (integración)

| ID | Caso de prueba | Tipo | Precondición | Pasos | Resultado esperado |
|----|----------------|------|--------------|-------|--------------------|
| INT-01 | Venta con categoría y unidad nuevas | Integración | CP-64-01 ejecutado | Ver CP-64-05 | Descuenta bien el inventario según el factor |
| INT-02 | Recomendación con dos proveedores | Integración | Datos 3.2 | Ver CP-63-06 y CP-63-07 | Usa el tiempo de entrega de cada proveedor y genera un pedido por proveedor |
| INT-03 | Liquidación con el esquema configurado | Integración | Planilla del ejemplo 3.3 | Ver CP-66-02 a CP-66-04 | Valores de la tabla 3.3 (FP-15 §2.17.4) |
| INT-04 | Pedido por voz con plantilla | Integración | CP-67-01 ejecutado | Ver CP-67-05 | Se exporta con la plantilla de su proveedor |
| INT-05 | Despacho a comerciante bloqueado | Integración | Ver CP-65-03 | Crear el despacho desde Planilla Diaria | La planilla no se crea y la pantalla muestra el aviso |
| INT-06 | Cuota y auditoría juntas | Integración | Plan de máx. 3 usuarios con el gerente titular + 1 usuario | Crear el usuario n.º 3 (permitido) y el n.º 4 (bloqueado) | Fila de auditoría solo para el n.º 3 |

Responsables: INT-01 a INT-04 Daniela Garcia Peñaranda (212), INT-05 Angie Nikol Ortiz Amaya (211), INT-06 Juan Sebastián Guerrero Bautista (213).

---

## Regresión (RF46–RF62)

| ID | Caso de prueba | Tipo | Precondición | Pasos | Resultado esperado |
|----|----------------|------|--------------|-------|--------------------|
| CP-REG-05 | Suite backend completa | Regresión | Rama `main` | `./mvnw test` | 0 fallos (479 pruebas antes de la iteración más rf63 … rf71; en `develop` al 26/11 hay 659 métodos `@Test` en `src/test`) |
| CP-REG-06 | Pruebas tocadas por 188, 189, 190 y 194 | Regresión | — | Correr rf52, rf54 (con el ejemplo ajustado a 1 día hábil), rf56, voz/ y `PedidoProveedorServiceExportarTest` | 0 fallos |
| CP-REG-07 | API de la Iteración 2 | Regresión | Render | Ejecutar Postman `GELOX_IT2` y `GELOX_RF55_RF57` | Todas en verde |
| CP-REG-08 | Flujos en el navegador | Regresión | Render | Venta por voz, pedido por voz, recomendación de compras, cambio de estado de una distribuidora, landing `/tienda/:subdominio` y liquidación de una planilla de Mágico Sabor con MARGEN | Igual que en la Iteración 2; la liquidación da el mismo `total_ganancia` que antes |
| CP-REG-09 | Regresión tras las correcciones (222) | Regresión | Correcciones de 219 y 220 en `main` | Repetir CP-REG-05 a CP-REG-08 | 0 fallos nuevos |

---

## Diferencias con el código (develop, 26/11/2026)

Resultados del documento de casos que el código actual no cumple. Si no se corrigen antes del 28/11, el caso sale **fallido** en 211 y se registra como defecto en 216.

| Caso | El documento espera | El código hace hoy | Dónde |
|------|---------------------|--------------------|-------|
| CP-48 | «dos columnas con orden 2» → 409 (FP-15 §1.4, RF67) | **Corregido en la rama `fix/rf67-orden-columnas`, pendiente de PR a develop:** orden repetido → 409 `{error: "El orden N está repetido en dos columnas", campo: "orden"}`, orden menor que 1 → 400, y las columnas se guardan según su `orden`. Hasta el merge, el backend renumera y no responde 409 | `exportacion/PlantillaExportacionService.java`, prueba en `rf67/PlantillaExportacionServiceTest` |
| CP-57 | Imagen de 6 MB → 409 ALMACENAMIENTO_MB | Cada archivo tiene un límite de 5 MB (`spring.servlet.multipart.max-file-size`): el de 6 MB se rechaza antes de revisar la cuota. En develop cae en el manejador genérico (500); **en la rama `fix/rf67-orden-columnas` responde 400 `{error: "El archivo supera el tamaño máximo de 5 MB por archivo.", codigo: "ARCHIVO_MUY_GRANDE"}`**. Para probar la cuota, el caso debe usar archivos de 5 MB o menos que sumados pasen de 5 MB | `application.properties`, `exceptions/GlobalExceptionHandler.java`, prueba en `rf70/ArchivoMuyGrandeTest` |
| CP-58 | Fila ACCESO_BLOQUEADO del intento de la gerente de Helados Prueba en CP-38 | CP-38 se ejecutó el 04/11, antes de que existiera la tabla `auditoria` (V9), y Helados Prueba quedó «Inactivo / Archivado» en CP-39. Para que haya fila hay que repetir el intento de acceso durante la Iteración 3; el detalle mostrará el estado INACTIVO, no SUSPENDIDO | `auth/FirebaseAuthFilter.java` |
| CP-57 / CP-70-10 | Plan Mini (3 usuarios, 3 productos, 2 comerciantes, 5 MB, 2 consultas IA) | **Corregido en la rama `fix/rf67-orden-columnas`, pendiente de PR:** `GELOX_IT3` usa Plan Mini en «Cuotas del plan» y en «Planes», y la prueba de voz verifica el `aviso`. Hasta el merge, la colección de develop prueba con un plan de máx. 4 comerciantes | `GELOX_IT3.postman_collection.json` |

---

## Matriz de trazabilidad

Criterios de aceptación de HU-18 y HU-19 del FP-15 §1.2 (versión del 23/11/2026). Los casos de aceptación son los del documento de la tarea 210 y pasan a FP-15 §4.3 en la tarea 224.

| RF | Criterio de HU | Casos complementarios | Casos de aceptación (FP-15 §4.3) |
|----|----------------|-----------------------|----------------------------------|
| RF63 | HU-19: varios proveedores por distribuidora, con contacto comercial, tiempo de entrega y monto mínimo de pedido | CP-63-01 … 08 | CP-44, CP-47 |
| RF64 | HU-19: catálogo multimarca con SKU propio, marca/proveedor, categoría, costos y precios | CP-64-01, 02, 04 | CP-46 |
| RF64 | HU-19: categorías propias por distribuidora | CP-64-03, 07 | CP-45 |
| RF64 | HU-19: unidades de medida y factores de conversión (compra al por mayor, venta fraccionada) | CP-64-05, 06 | CP-45, CP-46 |
| RF65 | HU-19: cupo máximo, plazo máximo de pago y política ante vencimiento | CP-65-01 … 12 | CP-52, CP-53 |
| RF66 | HU-19: esquema de liquidación (margen por producto, comisión porcentual o tarifa fija) | CP-66-01 … 11 | CP-50 |
| RF66 | HU-19: generar y bloquear la planilla diaria aplicando el esquema | CP-66-02 … 06, 09 | CP-51 |
| RF67 | HU-19: diseñar y guardar plantillas (orden de columnas, encabezados, códigos de homologación) por proveedor | CP-67-01 … 11 | CP-48 |
| RF67 | HU-19: generar el archivo del pedido con la plantilla del proveedor al cerrar el pedido | CP-67-03 … 08 | CP-49 |
| RF68 | HU-19: reportes financieros en Excel y PDF; identidad en reportes exportables (cierra lo que quedó pendiente de RF60, decisión D6 de la IT2) | CP-68-01 … 08 | CP-54 |
| RF69 | HU-18 c5: dashboard con distribuidoras activas, usuarios, volumen transaccional, almacenamiento y llamadas a IA | CP-69-01 … 05 | CP-55 |
| RF70 | HU-18 c6: planes de suscripción y cuotas por distribuidora | CP-70-01 … 13 | CP-56, CP-57 |
| RF71 | HU-18 c7: log de auditoría con timestamp, IP, distribuidora y usuario de las operaciones de aprovisionamiento, estado y seguridad | CP-71-01 … 08 | CP-58 |
| Transversal | Aislamiento por tenant sobre catálogo, crédito, exportación y gobierno | CP-TEN-13 … 16 | CP-44, CP-46, CP-51, CP-52 |
| Transversal | Cruces entre módulos | INT-01 … 06 | CP-47, CP-49, CP-51 |
| Regresión | RF46–RF62 | CP-REG-05 … 09 | CP-01 … CP-43 como referencia |

---

## Criterios, métricas y severidad

### Criterios de entrada
- Hito 202 cumplido: merge `develop → main` (tarea 201) con `./mvnw test` en verde, `npx vite build` sin errores y colección `GELOX_IT3` revisada.
- V9 y V10 aplicadas en Supabase sin errores y respaldo tomado.
- Render desplegado con `VITE_USAR_MOCK_IT2=false`.
- Distribuidora B, cuentas por rol y datos de la sección 3 creados.

### Criterios de salida
- 0 defectos críticos abiertos y 0 fugas entre distribuidoras.
- Cada criterio de aceptación de HU-18 (criterios 5–7) y HU-19 (RF63–RF68) con resultado aprobado y evidencia.
- Regresión 222 sin fallos nuevos.
- Acta de aceptación firmada por el cliente (hito 228) y sección FP-15 §4.3 escrita (hito 229).

### Métricas
| Métrica | Meta |
|---------|------|
| Casos aprobados por criterio de aceptación | 100 % |
| Fugas de datos entre distribuidoras | 0 |
| Operaciones críticas con fila de auditoría | 100 % |
| Pruebas de la suite backend en verde | 100 % |
| Defectos críticos corregidos antes del 02/12 | 100 % (hito 227) |

### Severidad de defectos (tarea 218)
| Severidad | Cuándo |
|-----------|--------|
| Crítica | Fuga de datos entre distribuidoras, bloquea un RF completo, cálculo de dinero o inventario incorrecto, o la migración impide arrancar |
| Alta | Un criterio de aceptación no se cumple pero existe un rodeo |
| Media | Error en un caso negativo o en una validación (código HTTP o mensaje distinto) sin afectar datos |
| Baja | Texto, estilo o mensaje sin efecto en datos ni en el flujo |

### Plantilla de reporte de resultados (211–214, 216)
| ID caso | RF | Tipo | Ejecutado por | Fecha | Resultado (Aprobado / Fallido / Bloqueado) | Evidencia (captura o video y minuto) | Defecto |
|---------|----|------|---------------|-------|--------------------------------------------|--------------------------------------|---------|
| CP-44 | RF63 | Funcional + aislamiento | | | | | |

Registro de defectos (216): caso → resultado → evidencia, con severidad, RF y módulo, mismo formato de FP-15 §4.2.

---

## Responsables y cronograma

| Tarea | Fecha | Tipo | Responsable | Qué |
|-------|-------|------|-------------|-----|
| 209 | Jue 26/11 | QA | Israel Bulla Rey | Este plan |
| 210 | Vie 27/11 (hecho el 25/11) | QA | Israel Bulla Rey | Casos de aceptación CP-44 … CP-58 en `Casos_de_Prueba_Funcionales_RF63_RF71.docx`, uno por criterio de RF63–RF71, con negativos: cupo superado y planilla vencida con BLOQUEAR (409 CREDITO_BLOQUEADO) y con SOLO_ALERTA (advertencia), cuota alcanzada (409) en usuarios, productos y comerciantes, recurso de otra distribuidora (404), Gerente en `/plataforma` (403), plantilla sin columnas (400), SKU repetido (409), pedido bajo el monto mínimo (advertencia) |
| 201 | Sáb 28/11 | REV | Zharick Nicole Hernandez Arevalo | Merge a `main` y despliegue en Render (entrada de las pruebas) |
| 211 | Sáb 28/11 | QA | Angie Nikol Ortiz Amaya | Casos funcionales sobre Render, aprobado/fallido por criterio, con captura |
| 212 | Sáb 28/11 | QA | Daniela Garcia Peñaranda | Cruces entre módulos (INT-01 a INT-04) |
| 213 | Sáb 28/11 | QA | Juan Sebastián Guerrero Bautista | Paso 0: crea la distribuidora B, sus cuentas y el plan «Plan Mini». Luego aislamiento, cuotas y auditoría |
| 214 | Sáb 28/11 | QA | Israel Bulla Rey | Regresión RF46–RF62 (CP-REG-05 a CP-REG-08) |
| 215 | Lun 30/11 | CLI | Jesús Gabriel Torres Daza | Revisa la distribuidora B creada por Juan; sesión con el cliente sobre Render con los casos de 210 |
| 216 | Lun 30/11 | QA | Angie Nikol Ortiz Amaya | Consolidar resultados y lista de defectos (hito 226) |
| 217 | Mar 01/12 | DOC | Angie Nikol Ortiz Amaya | Acta de la sesión de aceptación |
| 218 | Mar 01/12 | QA | Zharick Nicole Hernandez Arevalo | Causa raíz, severidad y asignación de defectos |
| 219 | Mié 02/12 | BE | Daniela Garcia Peñaranda | Corregir críticos de backend (prueba que lo reproduce + fix) |
| 220 | Mié 02/12 | FE | Emerson Amir Vera Gonzalez | Corregir críticos de frontend (hito 227) |
| 221 | Jue 03/12 mañana | QA | Angie Nikol Ortiz Amaya | Re-ejecutar los casos que fallaron |
| 222 | Jue 03/12 mañana | QA | Israel Bulla Rey | Repetir la regresión (CP-REG-09) |
| 223 | Jue 03/12 tarde | CLI | Jesús Gabriel Torres Daza | Repasar con el cliente lo corregido y firmar el acta (hito 228) |
| 224 | Jue 03/12 tarde | DOC | Angie Nikol Ortiz Amaya | FP-15 §4.3 Pruebas de Aceptación (hito 229) |
| 225 | Jue 03/12 tarde | REV | Zharick Nicole Hernandez Arevalo | Revisar y aprobar el reporte final |

Riesgo de calendario: 211–214 son el mismo sábado 28/11 que el merge de 201; las pruebas sobre Render empiezan cuando el despliegue termine. Si se atrasa, 213 y 214 arrancan en local y se repiten en Render.

---

## Criterios de Aceptación Globales
- Toda entidad nueva de una distribuidora (proveedor, categoría, factor, política, esquema, plantilla, código de homologación) responde 404 desde otra distribuidora.
- `/api/plataforma/panel`, `/planes` y `/auditoria` responden 403 a todo rol distinto de SUPERADMINISTRADOR.
- Al alcanzar la cuota de usuarios, productos, comerciantes o almacenamiento la operación responde 409 CUOTA_PLAN_ALCANZADA y no guarda nada. La cuota de llamadas IA no bloquea: la voz sigue con el clasificador de respaldo (OpenNLP) y muestra el aviso de cuota agotada.
- Cada operación crítica exitosa deja su fila de auditoría con fecha_hora, IP, distribuidora y usuario.
- Los comportamientos de la Iteración 2 se mantienen como valores por defecto: Excel de Nutresa (plantilla RELLENAR_CATALOGO), liquidación con MARGEN con el mismo `total_ganancia` y tiempo de entrega global (`recomendacion.tiempo-entrega-dias`) solo como respaldo para un producto sin proveedor; con V10, Nutresa usa 1 día hábil.
- Ningún endpoint responde 500 ante períodos sin datos o configuraciones vacías.
