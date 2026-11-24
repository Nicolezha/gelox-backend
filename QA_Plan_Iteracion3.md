# Plan de Pruebas — Iteración 3 GELOX

Tarea del Project: **209** — Plan de pruebas por módulo · Jue 26/11/2026  
Responsable: Israel Bulla Rey (QA)  
Formato: el de `QA_Plan_Iteracion1.md` (ID / Caso / Precondición / Pasos / Resultado esperado) con una columna **Tipo** (funcional, integración, aislamiento, cuotas, auditoría, regresión), como pide la tarea 209.  
Fuentes: Planificación de Alcance Global (HU-18 y HU-19, cierre), FP-15 versión del 23/11/2026: §1.2–§1.4, §1.6 y §2.14–§2.19 (HU-18, HU-19, RF63–RF71), y §4.1–§4.2, y Plan de trabajo — Iteración 3 GELOX (E3). Punto de partida: `QA_Plan_Iteracion1.md`, `QA_Plan_Voz_Iteracion1.md` y `QA_Plan_IT2.md`.

Numeración: los IDs CP-63-nn … CP-71-nn son de planeación. Los casos de aceptación que se escriban en FP-15 §4.3 (tarea 224) siguen la numeración del FP-15, que termina en CP-43 en la Iteración 2: la Iteración 3 empieza en **CP-44**. La matriz de trazabilidad del final relaciona ambos.

> Los endpoints y nombres de este plan son los del Plan de trabajo E3 (tareas 184–198). Si el diseño aprobado en FP-15 §2.14 (hito 179) o la implementación cambian un nombre o un código HTTP, manda el FP-15 y se actualiza este archivo antes de los casos de la tarea 210.

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
| Proveedor 1 | «Proveedor QA Uno», NIT propio, contacto (nombre, teléfono, correo), tiempo de entrega 2 días, días hábiles = sí, monto mínimo $ 300.000 |
| Proveedor 2 | «Proveedor QA Dos», NIT propio, contacto (nombre, teléfono, correo), tiempo de entrega 5 días, días hábiles = sí, monto mínimo $ 150.000 |
| Categorías propias | «Bebidas» y «Snacks» (además de las que se creen en la prueba) |
| Unidad y factor | Caja → unidad, factor 24 |
| Productos | Al menos uno por proveedor, con SKU propio y código de homologación |
| Comerciante con cupo bajo | Cupo $ 50.000, plazo 0 días, política BLOQUEAR (plazo del cliente, FP-15 §1.6.2.3, A3) |
| Comerciante con alerta | Cupo $ 50.000, plazo 0 días, política SOLO_ALERTA |
| Comerciante con prórroga | Planilla con saldo y fecha límite vencida, para extenderla con PATCH /api/planillas/{id}/fecha-limite |
| Plantilla de exportación | Proveedor QA Uno con columnas en orden distinto al de Nutresa, encabezados propios y códigos de homologación |
| Plan con cuotas pequeñas | Nombre «QA Pequeño», descripción «Plan de pruebas»; máx. 3 usuarios (incluye al gerente titular), máx. 5 productos, máx. 3 comerciantes, almacenamiento 10 MB, 20 llamadas IA/mes |
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
| ID | Caso de prueba | Tipo | Precondición | Pasos | Resultado esperado |
|----|----------------|------|--------------|-------|--------------------|
| CP-63-01 | Registrar proveedor | Funcional | Gerente o Encargado de Inventarios de B | POST /api/proveedores con nombre, NIT, `contacto_nombre`, `telefono`, `correo`, `tiempo_entrega_dias` 2, `dias_habiles` true y `monto_minimo_pedido` 300.000 | 201; aparece en `/inventarios/proveedores` |
| CP-63-02 | Editar proveedor | Funcional | CP-63-01 ejecutado | PUT /api/proveedores/{id} cambiando el tiempo de entrega | 200 y valor nuevo |
| CP-63-03 | Desactivar y activar | Funcional | CP-63-01 ejecutado | PATCH /api/proveedores/{id}/activar dos veces | Cambia el estado; el inactivo no se ofrece al crear productos |
| CP-63-04 | Datos inválidos | Funcional | — | POST sin nombre y con tiempo de entrega o monto negativos | 400 |
| CP-63-05 | Proveedor de otra distribuidora | Aislamiento | Proveedor de A | GET y PUT por id con el token de B | 404 |
| CP-63-06 | Recomendación con tiempo de entrega por proveedor | Integración | Productos de B con Proveedor QA Uno (2 días) y QA Dos (5 días) | POST /api/recomendaciones | El punto de reorden usa el tiempo de entrega de cada proveedor en días hábiles; Nutresa de Mágico Sabor usa 1 día hábil (el ejemplo de RF54 de FP-15 §2.10.6 se ajusta a este valor) |
| CP-63-07 | Un pedido por proveedor | Integración | Recomendación de CP-63-06 con aprobados de los dos proveedores | POST /api/recomendaciones/{id}/pedido | Se crea un pedido por proveedor |
| CP-63-08 | Pedido bajo el monto mínimo | Funcional | Proveedor QA Uno con mínimo $ 300.000 | Generar un pedido de $ 150.000 | El pedido se crea y la respuesta trae `advertenciaMontoMinimo`; la pantalla muestra el aviso (no se bloquea) |

### RF64 — Catálogo multimarca, categorías y unidades
| ID | Caso de prueba | Tipo | Precondición | Pasos | Resultado esperado |
|----|----------------|------|--------------|-------|--------------------|
| CP-64-01 | Crear producto multimarca | Funcional | Proveedor activo de B | Crear producto con SKU, marca, proveedor, categoría, costo, precio y unidad con factor 24 | Se guarda y se ve en el catálogo |
| CP-64-02 | SKU repetido | Funcional | CP-64-01 ejecutado | Crear otro producto con el mismo SKU en B | 409 |
| CP-64-03 | Crear categoría propia | Funcional | — | Crear «Bebidas» desde el formulario de producto | Disponible en B y no en A |
| CP-64-04 | Editar producto | Funcional | CP-64-01 ejecutado | Cambiar proveedor, categoría y factor | Se guardan los valores nuevos |
| CP-64-05 | Venta con factor de conversión | Integración | Entrada de 2 cajas (factor 24 = 48 unidades) | Vender 30 unidades | El inventario queda en 18 unidades |
| CP-64-06 | Producto en la landing | Integración | CP-64-01 ejecutado | Abrir `/tienda/:subdominio` de B | El producto aparece en su categoría propia |
| CP-64-07 | Categorías migradas de Mágico Sabor | Regresión | V10 aplicada | GET /productos/conos, /familiares, /paletas y landing de `magicosabor` | Agrupan igual que antes de V10 |
| CP-64-08 | Producto o categoría de otra distribuidora | Aislamiento | Producto y categoría de A | GET y PUT por id con el token de B | 404 |
| CP-64-09 | Cuota de productos alcanzada | Cuotas | B con 5 productos y plan de máx. 5 | Crear el producto n.º 6 | 409 «Cuota del plan alcanzada»; no se guarda |

---

## Módulo Políticas de Crédito y Liquidación (RF65, RF66)

### RF65 — Política de crédito por comerciante
El crédito de la venta rural queda fuera de alcance (FP-15, decisión D2); no se prueba.

| ID | Caso de prueba | Tipo | Precondición | Pasos | Resultado esperado |
|----|----------------|------|--------------|-------|--------------------|
| CP-65-01 | Definir política | Funcional | Gerente de B; comerciante registrado | PUT /api/comerciantes/{id}/credito {cupo: 50000, plazoDias: 0, politica: BLOQUEAR} | 200; la sección Crédito muestra cupo, plazo, política y deuda actual |
| CP-65-02 | Valores inválidos | Funcional | — | PUT con cupo o plazo negativos | 400 |
| CP-65-03 | Despacho que supera el cupo | Funcional | Comerciante con política BLOQUEAR y deuda cerca de $ 50.000 | Crear un despacho que supere el cupo | 409 {codigo: CREDITO_BLOQUEADO, deuda, cupo, planillasVencidas}; la planilla no se crea y la pantalla muestra el aviso |
| CP-65-04 | Planilla vencida con alerta | Funcional | Comerciante con política SOLO_ALERTA y planilla vencida | Crear un despacho | Se crea y la respuesta trae advertencias [{tipo: CUPO_SUPERADO \| PLANILLA_VENCIDA}]; aviso en Planilla Diaria |
| CP-65-05 | Comerciante cerca del cupo | Funcional | Deuda cercana al cupo | Abrir Planilla Diaria | Aviso de cupo |
| CP-65-06 | Rol sin permiso | Funcional | Encargado de Ventas de B | PUT /api/comerciantes/{id}/credito | 403 |
| CP-65-07 | Política de otra distribuidora | Aislamiento | Comerciante de A | GET y PUT con el token de B | 404 |
| CP-65-08 | Cambio de política auditado | Auditoría | CP-65-01 ejecutado | Consultar `/api/plataforma/auditoria` filtrando por B | Fila con acción CREDITO_POLITICA_CAMBIAR |
| CP-65-09 | Política por defecto | Funcional | Comerciante sin excepción | GET /api/comerciantes/{id}/credito | Política efectiva = la de la distribuidora (cupo $ 50.000, plazo 0, SOLO_ALERTA) |
| CP-65-10 | Abono | Funcional | Ejemplo 3.3 (deuda $ 53.200) | POST /api/comerciantes/{id}/abonos con $ 25.000 como Encargado de Ventas | 201; se aplica a la planilla más antigua; deuda $ 28.200; fila ABONO_REGISTRAR |
| CP-65-11 | Prórroga de fecha límite | Funcional | Planilla vencida con saldo | PATCH /api/planillas/{id}/fecha-limite como Gerente | 200; la planilla deja de contar como vencida; fila PLANILLA_CAMBIAR_FECHA_LIMITE. Con Encargado de Ventas → 403 |

### RF66 — Esquema de liquidación y planilla diaria
| ID | Caso de prueba | Tipo | Precondición | Pasos | Resultado esperado |
|----|----------------|------|--------------|-------|--------------------|
| CP-66-01 | Configurar esquema | Funcional | Gerente de B | PUT /api/liquidacion/esquema con MARGEN, luego COMISION 18 % y luego TARIFA_FIJA 15.000 | 200 cada vez; pantalla `/ajustes/liquidacion` muestra el vigente |
| CP-66-02 | Liquidar con MARGEN | Integración | Planilla del ejemplo 3.3; esquema MARGEN | Liquidar la planilla | Se cobra (`total_ganancia`) $ 103.200 y ganancia del comerciante $ 16.800 |
| CP-66-03 | Liquidar con COMISION | Integración | Misma planilla; esquema COMISION 18 % | Liquidar | Se cobra $ 98.400 y ganancia del comerciante $ 21.600 |
| CP-66-04 | Liquidar con TARIFA_FIJA | Integración | Misma planilla; TARIFA_FIJA 15.000 | Liquidar | Se cobra $ 105.000 y ganancia del comerciante $ 15.000 |
| CP-66-05 | Esquema por defecto | Regresión | Mágico Sabor sin esquema configurado | Liquidar una planilla | Mismo resultado que en la Iteración 2 |
| CP-66-06 | Planilla bloqueada | Funcional | Planilla liquidada | Intentar editarla | Rechazado; la planilla queda bloqueada |
| CP-66-07 | Rol sin permiso | Funcional | Encargado de Ventas de B | PUT /api/liquidacion/esquema | 403 |
| CP-66-08 | Esquema de otra distribuidora | Aislamiento | Esquemas distintos en A y B | GET /api/liquidacion/esquema con cada token | Cada una ve solo el suyo |
| CP-66-09 | Liquidación auditada | Auditoría | CP-66-02 ejecutado | Consultar la auditoría | Fila PLANILLA_LIQUIDAR |
| CP-66-10 | Efectivo recibido parcial | Funcional | Planilla del ejemplo 3.3; esquema MARGEN | Liquidar con `efectivoRecibido` 80.000; repetir sin `efectivoRecibido`; repetir con un valor mayor que el total y con uno negativo | Saldo $ 23.200 y `fecha_limite_pago` = fecha de la planilla (plazo 0). Sin `efectivoRecibido` se toma igual al total cobrado (saldo 0). Mayor que el total o negativo → 400 |
| CP-66-11 | Producto sin precio al público | Funcional | Planilla con un producto sin precio al público; esquema MARGEN | Liquidar la planilla | Ganancia 0 en ese ítem, el monto cobrado no cambia y la respuesta trae una advertencia |

---

## Módulo Exportación de Pedidos y Reportes (RF67, RF68)

### RF67 — Plantilla de exportación por proveedor
| ID | Caso de prueba | Tipo | Precondición | Pasos | Resultado esperado |
|----|----------------|------|--------------|-------|--------------------|
| CP-67-01 | Guardar plantilla | Funcional | Proveedor QA Uno | PUT /api/proveedores/{id}/plantilla en modo GENERAR_FILAS con orden de columnas, encabezados y códigos de homologación | 200; vista previa en `/inventarios/plantillas` con las primeras filas; fila PLANTILLA_EXPORTACION_CAMBIAR en la auditoría |
| CP-67-02 | Plantilla sin columnas | Funcional | — | PUT de una plantilla GENERAR_FILAS sin columnas | 400 |
| CP-67-03 | Pedido manual con plantilla | Integración | CP-67-01 ejecutado | Crear un pedido manual a Proveedor QA Uno y abrir el Excel | Columnas en el orden y con los encabezados de la plantilla, y códigos de homologación |
| CP-67-04 | Pedido desde recomendación | Integración | CP-67-01 y CP-63-07 ejecutados | Abrir el Excel del pedido de Proveedor QA Uno | Exportado con su plantilla |
| CP-67-05 | Pedido por voz | Integración | CP-67-01 ejecutado | Dictar un pedido de un producto de Proveedor QA Uno y confirmar | El asistente pide o infiere el proveedor; el Excel usa su plantilla |
| CP-67-06 | Plantilla de Nutresa | Regresión | Nutresa en Mágico Sabor en modo RELLENAR_CATALOGO (`templates/catalogo.xlsx`, fila de encabezados 4) | Crear un pedido y abrir el Excel | Mismo Excel de hoy; `PedidoProveedorServiceExportarTest` en verde |
| CP-67-07 | Plantilla de otra distribuidora | Aislamiento | Proveedor de A | GET y PUT /api/proveedores/{id}/plantilla con el token de B | 404 |
| CP-67-08 | Producto sin código para el proveedor | Funcional | Producto sin código de homologación | Crear un pedido con ese producto | El pedido se exporta igual y la respuesta lista los productos sin código |

### RF68 — Reportes financieros en Excel y PDF
| ID | Caso de prueba | Tipo | Precondición | Pasos | Resultado esperado |
|----|----------------|------|--------------|-------|--------------------|
| CP-68-01 | Exportar a Excel | Funcional | Período con ventas y pedidos | Botón «Exportar Excel» (GET /api/reportes/financiero/excel) | Descarga `reporte-financiero-{subdominio}-{desde}-{hasta}.xlsx` con las hojas «Resumen», «Inversión vs ingresos», «Rentabilidad por canal» y «Top 5 comerciantes», con los mismos valores de la pantalla Reportes |
| CP-68-02 | Exportar a PDF | Funcional | Mismo período | Botón «Exportar PDF» (GET /api/reportes/financiero/pdf) | Mismos valores que el Excel y la pantalla |
| CP-68-03 | Identidad en el encabezado | Funcional | Identidades distintas en A y B | Exportar en cada distribuidora | Logo, nombre y color de cada una |
| CP-68-04 | Período sin datos | Funcional | Período sin ventas | Exportar Excel y PDF | Archivos con valores en cero, sin 500 |
| CP-68-05 | Período inválido | Funcional | — | Exportar con inicio > fin | 400 |
| CP-68-06 | Rol sin permiso | Funcional | Encargado de Inventarios | GET …/excel y …/pdf | 403 |
| CP-68-07 | Datos de otra distribuidora | Aislamiento | Ventas en A y B | Exportar con el token de B | Solo datos de B |
| CP-68-08 | Secciones elegidas | Funcional | Período con datos | Exportar con `secciones` = resumen y Top 5 | Solo esos bloques, más el encabezado (que siempre va) |

---

## Módulo Panel y Gobierno de la Plataforma (RF69, RF70, RF71)

### RF69 — Panel del Superadministrador
| ID | Caso de prueba | Tipo | Precondición | Pasos | Resultado esperado |
|----|----------------|------|--------------|-------|--------------------|
| CP-69-01 | Indicadores consolidados | Funcional | Superadministrador; A y B con datos | GET /api/plataforma/panel?desde&hasta y abrir `/plataforma/panel` | Distribuidoras por estado, usuarios activos por distribuidora, ventas y pedidos del período, almacenamiento, uso de IA y uso de cada cuota frente al límite del plan por distribuidora; incluye A y B |
| CP-69-02 | Cifras contra la base | Funcional | CP-69-01 ejecutado | Contar a mano en la base cada indicador | Coinciden; uso de IA = `comando_voz` con `metodo_clasificacion = LLM` |
| CP-69-03 | Período sin actividad | Funcional | — | GET con un período sin datos | Ventas y pedidos en cero, sin 500 |
| CP-69-04 | Gerente en el panel | Funcional | Gerente de A | Abrir `/plataforma/panel` y llamar al endpoint | 403 |
| CP-69-05 | Almacenamiento | Funcional | Archivos subidos antes y después de la Iteración 3 | GET /api/plataforma/panel | Cuenta solo los archivos registrados desde la Iteración 3 (los anteriores cuentan 0) |

### RF70 — Planes de suscripción y cuotas
| ID | Caso de prueba | Tipo | Precondición | Pasos | Resultado esperado |
|----|----------------|------|--------------|-------|--------------------|
| CP-70-01 | Crear plan | Funcional | Superadministrador | POST /api/plataforma/planes con nombre (máx. 30), descripción, precio mensual y cuotas de usuarios, productos, comerciantes, almacenamiento MB y llamadas IA/mes; una cuota vacía = sin límite | 201; aparece en `/plataforma/planes` |
| CP-70-02 | Editar plan | Funcional | CP-70-01 ejecutado | PUT /api/plataforma/planes/{id} | 200 y valores nuevos |
| CP-70-03 | Asignar plan | Funcional | Plan y distribuidora B | PATCH /api/plataforma/distribuidoras/{id}/plan {planId, motivo} y alta de una distribuidora con `planId` | El plan queda asignado y se ve en el detalle |
| CP-70-04 | Cuota de usuarios | Cuotas | B con 3 usuarios activos contando al gerente titular, y plan de máx. 3 | Crear el usuario n.º 4 | 409 «Cuota del plan alcanzada»; no se crea en Firebase ni en la base |
| CP-70-05 | Cuota de productos | Cuotas | Ver CP-64-09 | Crear el producto n.º 6 | 409 |
| CP-70-06a | Cuota de IA | Cuotas | Llamadas IA del mes en el límite (20) | Hacer una consulta por voz más | La voz sigue funcionando con el clasificador de respaldo (OpenNLP) y avisa; no hay 409 |
| CP-70-06b | Cuota de almacenamiento | Cuotas | Almacenamiento cerca de 10 MB | Subir un archivo que pasa de 10 MB | 409 CUOTA_PLAN_ALCANZADA; el archivo no se guarda |
| CP-70-07 | Planes migrados | Regresión | V9 aplicada | Operar con Mágico Sabor | Sigue operando con el plan PILOTO migrado de su texto, sin límites (máximos nulos) |
| CP-70-08 | Gerente en planes | Funcional | Gerente de A | GET /api/plataforma/planes | 403 |
| CP-70-09 | Cambio de plan auditado | Auditoría | CP-70-03 ejecutado | Consultar la auditoría | Fila PLAN_ASIGNAR con plan anterior, nuevo y motivo |
| CP-70-10 | Cuota de comerciantes | Cuotas | B con 3 comerciantes activos y plan de máx. 3 | Crear el 4.º; luego desactivar uno, crear otro y volver a activar el desactivado | 409 en el 4.º y en la reactivación; no se guarda |
| CP-70-11 | Cuerpo del 409 | Cuotas | CP-70-04 | Revisar la respuesta y la pantalla | `{error: "Cuota del plan alcanzada: usuarios (3 de 3).", codigo: "CUOTA_PLAN_ALCANZADA", cuota: "USUARIOS", limite: 3, usado: 3}`; la pantalla muestra «Cuota del plan alcanzada» |
| CP-70-12 | Validaciones del plan | Funcional | Plan existente con distribuidoras | Crear un plan con nombre repetido; con cuota o precio negativo; borrar un plan con distribuidoras | Nombre repetido → 409; cuota o precio negativo → 400; borrar un plan con distribuidoras → 409 |
| CP-70-13 | Bajar a un plan menor | Funcional | B con más usuarios que el límite del plan nuevo | Asignar a B un plan con menos usuarios de los que tiene | Se asigna; no se deshabilita nada; las altas nuevas responden 409 |

### RF71 — Log de auditoría
| ID | Caso de prueba | Tipo | Precondición | Pasos | Resultado esperado |
|----|----------------|------|--------------|-------|--------------------|
| CP-71-01 | Operaciones de usuarios auditadas | Auditoría | Gerente de B | Crear, editar y deshabilitar un usuario | Filas USUARIO_CREAR, USUARIO_EDITAR y USUARIO_DESHABILITAR con fecha_hora, ip, tenant_id, usuario_id, rol, accion, entidad, entidad_id y detalle (JSON) |
| CP-71-02 | Aprovisionamiento y cambio de estado auditados | Auditoría | Superadministrador | Registrar una distribuidora, pasarla a «Suspendido por mora» y reactivarla | Filas DISTRIBUIDORA_APROVISIONAR y DISTRIBUIDORA_CAMBIAR_ESTADO; tenant_id vacío en las acciones del Superadministrador |
| CP-71-03 | Operación fallida | Auditoría | Cuota alcanzada (CP-70-04) | Intentar crear el usuario | No queda fila de operación exitosa |
| CP-71-04 | Consulta con filtros | Funcional | Filas de A y B | GET /api/plataforma/auditoria con filtros distribuidora, usuario, desde, hasta y acción; pantalla `/plataforma/auditoria` | Solo las filas que cumplen el filtro, paginadas; GET /api/plataforma/auditoria/acciones y /auditoria/usuarios llenan los filtros |
| CP-71-05 | Gerente en auditoría | Funcional | Gerente de A | GET /api/plataforma/auditoria | 403 |
| CP-71-06 | Seguridad auditada | Auditoría | Usuario de B; distribuidora suspendida | Iniciar y cerrar sesión, cambiar la contraseña e intentar entrar con una distribuidora suspendida | Filas SESION_INICIAR, SESION_CERRAR, CONTRASENA_CAMBIAR (el detalle no trae la contraseña) y ACCESO_BLOQUEADO |
| CP-71-07 | Otras acciones | Auditoría | Gerente de B y Superadministrador | Crear un producto, reenviar el acceso al gerente y cambiar la identidad visual | Filas PRODUCTO_CREAR, DISTRIBUIDORA_REENVIAR_ACCESO e IDENTIDAD_ACTUALIZAR |
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
| CP-REG-05 | Suite backend completa | Regresión | Rama `main` | `./mvnw test` | 0 fallos (479 pruebas antes de la iteración más rf63 … rf71) |
| CP-REG-06 | Pruebas tocadas por 188, 189, 190 y 194 | Regresión | — | Correr rf52, rf54 (con el ejemplo ajustado a 1 día hábil), rf56, voz/ y `PedidoProveedorServiceExportarTest` | 0 fallos |
| CP-REG-07 | API de la Iteración 2 | Regresión | Render | Ejecutar Postman `GELOX_IT2` y `GELOX_RF55_RF57` | Todas en verde |
| CP-REG-08 | Flujos en el navegador | Regresión | Render | Venta por voz, pedido por voz, recomendación de compras, cambio de estado de una distribuidora, landing `/tienda/:subdominio` y liquidación de una planilla de Mágico Sabor con MARGEN | Igual que en la Iteración 2; la liquidación da el mismo `total_ganancia` que antes |
| CP-REG-09 | Regresión tras las correcciones (222) | Regresión | Correcciones de 219 y 220 en `main` | Repetir CP-REG-05 a CP-REG-08 | 0 fallos nuevos |

---

## Matriz de trazabilidad

Criterios de aceptación de HU-18 y HU-19 tomados de la Planificación de Alcance Global; la tarea 155 los pasa al FP-15 §1.2. La columna FP-15 §4.3 se llena en la tarea 224 a partir de CP-44.

| RF | Criterio de HU | Casos de este plan | Caso FP-15 §4.3 |
|----|----------------|--------------------|-----------------|
| RF63 | HU-19: varios proveedores por distribuidora, con contacto comercial, tiempo de entrega y monto mínimo de pedido | CP-63-01 … 08 | CP-44 en adelante |
| RF64 | HU-19: catálogo multimarca con SKU propio, marca/proveedor, categoría, costos y precios | CP-64-01, 02, 04 | |
| RF64 | HU-19: categorías propias por distribuidora | CP-64-03, 07 | |
| RF64 | HU-19: unidades de medida y factores de conversión (compra al por mayor, venta fraccionada) | CP-64-05, 06 | |
| RF65 | HU-19: cupo máximo, plazo máximo de pago y política ante vencimiento | CP-65-01 … 11 | |
| RF66 | HU-19: esquema de liquidación (margen por producto, comisión porcentual o tarifa fija) | CP-66-01 … 11 | |
| RF66 | HU-19: generar y bloquear la planilla diaria aplicando el esquema | CP-66-02 … 06, 09 | |
| RF67 | HU-19: diseñar y guardar plantillas (orden de columnas, encabezados, códigos de homologación) por proveedor | CP-67-01 … 08 | |
| RF67 | HU-19: generar el archivo del pedido con la plantilla del proveedor al cerrar el pedido | CP-67-01 … 08 | |
| RF68 | HU-19: reportes financieros en Excel y PDF; identidad en reportes exportables (cierra lo que quedó pendiente de RF60, decisión D6 de la IT2) | CP-68-01 … 08 | |
| RF69 | HU-18 c5: dashboard con distribuidoras activas, usuarios, volumen transaccional, almacenamiento y llamadas a IA | CP-69-01 … 05 | |
| RF70 | HU-18 c6: planes de suscripción y cuotas por distribuidora | CP-70-01 … 13 | |
| RF71 | HU-18 c7: log de auditoría con timestamp, IP, distribuidora y usuario de las operaciones de aprovisionamiento, estado y seguridad | CP-71-01 … 08 | |
| Transversal | Aislamiento por tenant sobre catálogo, crédito, exportación y gobierno | CP-TEN-13 … 16 | |
| Transversal | Cruces entre módulos | INT-01 … 06 | |
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
| CP-63-01 | RF63 | Funcional | | | | | |

Registro de defectos (216): caso → resultado → evidencia, con severidad, RF y módulo, mismo formato de FP-15 §4.2.

---

## Responsables y cronograma

| Tarea | Fecha | Tipo | Responsable | Qué |
|-------|-------|------|-------------|-----|
| 209 | Jue 26/11 | QA | Israel Bulla Rey | Este plan |
| 210 | Vie 27/11 | QA | Israel Bulla Rey | Completar un caso por criterio de aceptación de RF63–RF71 a partir de los de este plan, con negativos: cupo superado y planilla vencida con BLOQUEAR (409 CREDITO_BLOQUEADO) y con SOLO_ALERTA (advertencia), cuota alcanzada (409) en usuarios, productos y comerciantes, recurso de otra distribuidora (404), Gerente en `/plataforma` (403), plantilla sin columnas (400), SKU repetido (409), pedido bajo el monto mínimo (advertencia) |
| 201 | Sáb 28/11 | REV | Zharick Nicole Hernandez Arevalo | Merge a `main` y despliegue en Render (entrada de las pruebas) |
| 211 | Sáb 28/11 | QA | Angie Nikol Ortiz Amaya | Casos funcionales sobre Render, aprobado/fallido por criterio, con captura |
| 212 | Sáb 28/11 | QA | Daniela Garcia Peñaranda | Cruces entre módulos (INT-01 a INT-04) |
| 213 | Sáb 28/11 | QA | Juan Sebastián Guerrero Bautista | Paso 0: crea la distribuidora B, sus cuentas y el plan QA Pequeño. Luego aislamiento, cuotas y auditoría |
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
- Al alcanzar la cuota de usuarios, productos, comerciantes o almacenamiento la operación responde 409 CUOTA_PLAN_ALCANZADA y no guarda nada. La cuota de llamadas IA no bloquea: la voz sigue con el clasificador de respaldo (OpenNLP) y avisa.
- Cada operación crítica exitosa deja su fila de auditoría con fecha_hora, IP, distribuidora y usuario.
- Los comportamientos de la Iteración 2 se mantienen como valores por defecto: Excel de Nutresa (plantilla RELLENAR_CATALOGO), liquidación con MARGEN con el mismo `total_ganancia` y tiempo de entrega global (`recomendacion.tiempo-entrega-dias`) solo como respaldo para un producto sin proveedor; con V10, Nutresa usa 1 día hábil.
- Ningún endpoint responde 500 ante períodos sin datos o configuraciones vacías.
