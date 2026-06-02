# 🍦 GELOX — Backend

> Sistema de gestión integral para la heladería **Mágico Sabor**: inventario, ventas por múltiples canales, planillas de comerciantes, reportes financieros y landing page pública.

![Java](https://img.shields.io/badge/Java-25-007396?style=flat-square&logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5.14-6DB33F?style=flat-square&logo=springboot&logoColor=white)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-Supabase-4169E1?style=flat-square&logo=postgresql&logoColor=white)
![Firebase](https://img.shields.io/badge/Firebase-Admin%20SDK%209.9-FFCA28?style=flat-square&logo=firebase&logoColor=black)

---

## 📋 Tabla de contenidos

1. [Stack tecnológico](#-stack-tecnológico)
2. [Arquitectura](#-arquitectura)
3. [Módulos del sistema](#-módulos-del-sistema)
4. [Requisitos previos](#-requisitos-previos)
5. [Variables de entorno](#-variables-de-entorno)
6. [Instalación y ejecución local](#-instalación-y-ejecución-local)
7. [Estructura de carpetas](#-estructura-de-carpetas)
8. [Base de datos](#-base-de-datos)
9. [Equipo de desarrollo](#-equipo-de-desarrollo)
10. [Licencia](#-licencia)

---

## 🛠️ Stack tecnológico

| Tecnología | Versión | Uso |
|---|---|---|
| Java | 25 | Lenguaje principal |
| Spring Boot | 3.5.14 | Framework base (Web, JPA, Security, AOP, Actuator) |
| PostgreSQL en Supabase | — | Base de datos relacional en la nube |
| Firebase Admin SDK | 9.9.0 | Autenticación y verificación de tokens JWT |
| Supabase Storage | — | Almacenamiento de imágenes (fotos de perfil, comerciantes) |
| Flyway | — | Migraciones incrementales de la base de datos |
| Apache POI | 5.3.0 | Generación de archivos Excel (pedidos al proveedor Nutresa) |
| Spring Security | — | Filtros de autenticación y control de acceso |
| Spring Data JPA / Hibernate | — | ORM y acceso a datos |
| Spring AOP | — | Autorización por rol vía aspecto `@RequiereRol` |
| Spring Mail + SendGrid | — | Envío de correos (recuperación de contraseña) |
| Lombok | — | Reducción de boilerplate |
| H2 | — | Base de datos en memoria para pruebas |

---

## 🏗️ Arquitectura

El proyecto sigue una arquitectura de **tres capas** (Controlador → Servicio → Repositorio) con separación clara de responsabilidades:

```
Cliente (Frontend / App)
        │
        │  HTTP + Authorization: Bearer <Firebase ID Token>
        ▼
┌──────────────────────────────────────────────────────┐
│                   Spring Security                    │
│              FirebaseAuthFilter                      │
│  · Verifica el token con Firebase Admin SDK          │
│  · Busca el usuario en BD por firebaseUid            │
│  · Valida que el usuario esté activo                 │
│  · Establece el SecurityContext con rol del usuario  │
└──────────────────┬───────────────────────────────────┘
                   │
        ┌──────────▼──────────┐
        │    Controllers      │  @RestController  /api/**
        │  · Reciben DTOs     │
        │  · @RequiereRol     │◄── RolVerificacionAspect (AOP)
        └──────────┬──────────┘
                   │
        ┌──────────▼──────────┐
        │      Services       │  Lógica de negocio
        └──────────┬──────────┘
                   │
        ┌──────────▼──────────┐
        │    Repositories     │  Spring Data JPA
        └──────────┬──────────┘
                   │
        ┌──────────▼──────────┐
        │  PostgreSQL         │  Supabase (nube)
        │  Supabase Storage   │
        └─────────────────────┘
```

### Flujo de autenticación

1. El frontend obtiene un **Firebase ID Token** tras el login con Firebase Auth.
2. Cada petición incluye el token en el header `Authorization: Bearer <token>`.
3. `FirebaseAuthFilter` intercepta la petición, verifica el token con el SDK de Firebase Admin y carga el usuario desde la base de datos.
4. Si el usuario está activo, se establece en el `SecurityContext` con su rol como `GrantedAuthority`.

### Control de acceso por roles (RBAC)

La anotación personalizada `@RequiereRol` se coloca en métodos o clases de los controladores para restringir el acceso por rol. `RolVerificacionAspect` (Spring AOP) intercepta la ejecución antes del método y lanza un `403 Forbidden` si el usuario autenticado no posee uno de los roles requeridos.

**Roles disponibles:**

| Rol | Descripción |
|---|---|
| `ADMINISTRADOR` | Acceso total al sistema |
| `ENCARGADO_INVENTARIO` | Gestión de stock, pedidos y pérdidas |
| `ENCARGADO_VENTAS` | Registro de ventas, planillas y cierre de caja |

---

## 📦 Módulos del sistema

### 🔐 Autenticación y sesión
Gestiona el inicio de sesión mediante Firebase, la verificación de tokens, el cierre de sesión y la recuperación de contraseña por correo electrónico (SendGrid).

| Método | Ruta | Descripción |
|---|---|---|
| `POST` | `/api/auth/verificar` | Verifica el token Firebase y retorna el perfil del usuario |
| `GET` | `/api/auth/perfil` | Obtiene el perfil del usuario autenticado |
| `POST` | `/api/auth/cerrar-sesion` | Cierra la sesión activa |
| `POST` | `/api/auth/recuperar-contrasena` | Envía enlace de recuperación al correo |

---

### 👥 Gestión de usuarios
Permite crear, editar, habilitar y deshabilitar cuentas de los operarios del sistema. Soporta subida de foto de perfil mediante Supabase Storage.

| Método | Ruta | Descripción |
|---|---|---|
| `GET` | `/api/usuarios` | Listar todos los usuarios |
| `GET` | `/api/usuarios/{id}` | Obtener usuario por ID |
| `POST` | `/api/usuarios` | Crear usuario (JSON o multipart con foto) |
| `PUT` | `/api/usuarios/{id}` | Editar usuario |
| `DELETE` | `/api/usuarios/{id}` | Deshabilitar usuario |
| `PATCH` | `/api/usuarios/{id}/habilitar` | Habilitar usuario |

---

### 🪪 Perfil y contraseña
Permite a cada usuario actualizar sus datos personales, cambiar su foto de perfil y modificar su contraseña directamente desde la aplicación.

| Método | Ruta | Descripción |
|---|---|---|
| `PUT` | `/api/perfil/{userId}` | Actualizar datos del perfil |
| `POST` | `/api/perfil/{userId}/foto` | Subir foto de perfil |
| `PUT` | `/api/perfil/{firebaseUid}/contrasena` | Cambiar contraseña |

---

### 📊 Dashboard gerencial
Proporciona una vista ejecutiva en tiempo real con indicadores clave de desempeño (KPIs), gráfico de inversión vs ingresos, distribución de ventas por canal, top 5 comerciantes más activos y un feed de eventos del sistema.

| Método | Ruta | Descripción |
|---|---|---|
| `GET` | `/api/dashboard/kpis` | KPIs del día (ventas, ingresos, inversión, utilidad) |
| `GET` | `/api/dashboard/inversion-ingresos` | Serie temporal inversión vs ingresos |
| `GET` | `/api/dashboard/ventas-por-canal` | Distribución por VENTANILLA / RURAL |
| `GET` | `/api/dashboard/top5-comerciantes` | Ranking de los 5 comerciantes más activos |
| `GET` | `/api/dashboard/eventos` | Feed de eventos del sistema (paginado) |

---

### 🗂️ Catálogo de productos
Gestión completa del catálogo de la heladería: creación, edición, activación/desactivación de productos con sus precios de costo y venta, categoría y unidad de medida.

| Método | Ruta | Descripción |
|---|---|---|
| `GET` | `/api/productos` | Listar todos los productos |
| `GET` | `/api/productos/{id}` | Obtener producto por ID |
| `POST` | `/api/productos` | Crear producto |
| `PUT` | `/api/productos/{id}` | Editar producto |
| `PATCH` | `/api/productos/{id}/activar` | Activar producto |
| `PATCH` | `/api/productos/{id}/desactivar` | Desactivar producto |

**Categorías disponibles:** `PALETAS`, `CONOS`, `FAMILIARES`, `VASOS`

---

### 📦 Gestión de inventario
Visibilidad en tiempo real del stock disponible, sistema de alertas de bajo stock, registro de todos los movimientos de mercancía (entradas, salidas por venta, despachos, pérdidas) y búsqueda con filtros.

| Método | Ruta | Descripción |
|---|---|---|
| `GET` | `/api/inventario/productos` | Stock actual con filtros |
| `GET` | `/api/inventario/alertas` | Productos con alerta de bajo stock |
| `POST` | `/api/inventario/perdidas` | Registrar pérdida de producto |

---

### 📋 Pedidos al proveedor y entradas de mercancía
Generación de pedidos en formato Excel compatible con **Nutresa** para enviar al proveedor. Registro de entradas de mercancía con comparación automática entre lo pedido y lo recibido.

| Método | Ruta | Descripción |
|---|---|---|
| `POST` | `/api/inventario/pedidos` | Crear pedido y descargar Excel |
| `GET` | `/api/inventario/pedidos` | Historial de pedidos (paginado) |
| `GET` | `/api/inventario/pedidos/{id}` | Detalle del pedido con comparación pedido vs recibido |
| `POST` | `/api/inventario/entradas` | Registrar entrada de mercancía al inventario |

---

### 💸 Ventas por ventanilla
Flujo de venta directa al cliente final: selección de productos, cálculo del subtotal, elección del canal (`VENTANILLA` o `RURAL`) y confirmación de la transacción con método de pago.

| Método | Ruta | Descripción |
|---|---|---|
| `GET` | `/api/ventas/catalogo` | Catálogo disponible para venta |
| `POST` | `/api/ventas/calcular` | Calcular totales antes de confirmar |
| `POST` | `/api/ventas/iniciar` | Iniciar una venta |
| `POST` | `/api/ventas/confirmar` | Confirmar y registrar la venta |

---

### 🚚 Pedidos rurales
Módulo especializado para ventas a clientes recurrentes fuera del local. Permite vender en presentación mixta (cajas completas y unidades sueltas), asociar el pedido a un cliente registrado e incluir costo de envío.

| Método | Ruta | Descripción |
|---|---|---|
| `GET` | `/api/ventas/rural` | Listar pedidos rurales (paginado) |
| `POST` | `/api/ventas/rural` | Confirmar pedido rural |
| `GET` | `/api/ventas/clientes-rurales` | Listar clientes rurales (con búsqueda) |
| `POST` | `/api/ventas/clientes-rurales` | Registrar nuevo cliente rural |

---

### 🏪 Gestión de comerciantes
CRUD completo de los 26 comerciantes independientes que distribuyen los productos. Incluye datos de identificación, EPS, foto y estado activo/inactivo.

| Método | Ruta | Descripción |
|---|---|---|
| `GET` | `/api/comerciantes` | Listar comerciantes (con búsqueda `?q=`) |
| `POST` | `/api/comerciantes` | Crear comerciante (JSON o multipart con foto) |
| `PUT` | `/api/comerciantes/{id}` | Editar comerciante |
| `PATCH` | `/api/comerciantes/{id}/estado` | Activar o desactivar comerciante |
| `GET` | `/api/comerciantes/{id}/planillas` | Historial de planillas del comerciante |
| `GET` | `/api/comerciantes/{id}/planillas/{planillaId}` | Detalle de una planilla |

---

### 📝 Planilla diaria de comerciante
Registro del despacho de productos a cada comerciante, liquidación al final del día con cálculo automático de ganancia y deuda, historial de planillas e impresión de la planilla.

| Método | Ruta | Descripción |
|---|---|---|
| `POST` | `/api/planillas/despacho` | Crear despacho para un comerciante |
| `PUT` | `/api/planillas/{id}/despacho` | Actualizar ítems del despacho |
| `POST` | `/api/planillas/{id}/liquidar` | Liquidar planilla y calcular ganancia |
| `GET` | `/api/planillas/{id}/imprimir` | Obtener planilla lista para impresión |

---

### 📈 Reportes financieros gerenciales
Reportes por período configurable: inversión total, ingresos por canal, utilidad neta, margen de rentabilidad y gráficas dinámicas para análisis gerencial.

| Método | Ruta | Descripción |
|---|---|---|
| `GET` | `/api/reportes/financiero` | Reporte por período |
| `GET` | `/api/reportes/grafica-inversion-ingresos` | Gráfica dinámica inversión vs ingresos |
| `GET` | `/api/reportes/por-canal` | Rentabilidad desglosada por canal de venta |
| `GET` | `/api/reportes/financieros` | Reporte financiero completo con filtro de período |

---

### 🗃️ Cierre de caja
Conciliación diaria del dinero recibido (efectivo y transferencia) contra las ventas registradas en el sistema por canal, con historial de cierres anteriores.

| Método | Ruta | Descripción |
|---|---|---|
| `POST` | `/api/cierre-caja` | Registrar cierre con dinero físico contado |
| `GET` | `/api/cierre-caja` | Historial de cierres con filtros |
| `GET` | `/api/cierre-caja/{id}` | Detalle de un cierre de caja |

---

### 📅 Reportes diarios operativos
Consolidado del día para el personal: ventas por canal, tabla de transacciones y cierre operativo al finalizar la jornada.

| Método | Ruta | Descripción |
|---|---|---|
| `GET` | `/api/reportes/diario` | Consolidado del día por canal |
| `GET` | `/api/reportes/diario/transacciones` | Tabla de transacciones del día |
| `POST` | `/api/reportes/diario/cierre-operativo` | Registrar cierre operativo |

---

### 🌐 Landing page pública
Endpoints sin autenticación para la landing page visible a clientes: catálogo de productos agrupado por categoría, información del negocio y enlace de contacto por WhatsApp.

| Método | Ruta | Descripción |
|---|---|---|
| `GET` | `/api/landing/productos` | Catálogo completo agrupado por categoría |
| `GET` | `/api/landing/productos/paletas` | Solo paletas |
| `GET` | `/api/landing/productos/conos` | Solo conos |
| `GET` | `/api/landing/productos/familiares` | Solo familiares |
| `GET` | `/api/landing/whatsapp` | URL de contacto por WhatsApp |
| `GET` | `/api/landing/info-negocio` | Dirección, horario y datos del negocio |

---

## ✅ Requisitos previos

- **JDK 21+** (el proyecto compila con Java 25)
- **Maven 3.9+**
- Cuenta en **[Supabase](https://supabase.com)** con proyecto PostgreSQL activo
- Proyecto en **Firebase** con archivo de credenciales de Admin SDK (`serviceAccountKey.json`)
- Cuenta en **SendGrid** para el envío de correos

---

## 🔑 Variables de entorno

Crea un archivo `application-local.properties` en `src/main/resources/` (no lo subas al repositorio) o define estas variables en tu entorno:

```properties
# Base de datos (Supabase PostgreSQL)
SUPABASE_DB_URL=jdbc:postgresql://<host>:<port>/<database>
SUPABASE_DB_USERNAME=postgres
SUPABASE_DB_PASSWORD=tu_password_supabase

# Firebase Auth
FIREBASE_SERVICE_ACCOUNT_PATH=classpath:serviceAccountKey.json
# Alternativa: JSON completo como string en variable de entorno
FIREBASE_SERVICE_ACCOUNT_JSON={"type":"service_account",...}
FIREBASE_WEB_API_KEY=tu_web_api_key_de_firebase

# Supabase Storage
SUPABASE_URL=https://<proyecto>.supabase.co
SUPABASE_SERVICE_KEY=tu_service_role_key
SUPABASE_BUCKET=fotos

# SendGrid (correos)
SENDGRID_API_KEY=SG.xxxxxxxxxxxxxxxx
MAIL_FROM=noreply@tudominio.com
MAIL_FROM_NAME=GELOX

# Servidor
PORT=8080
```

---

## 🚀 Instalación y ejecución local

### 1. Clonar el repositorio

```bash
git clone https://github.com/tu-org/gelox-backend.git
cd gelox-backend
```

### 2. Configurar Firebase

Descarga el archivo `serviceAccountKey.json` desde la consola de Firebase:

> Firebase Console → Configuración del proyecto → Cuentas de servicio → Generar nueva clave privada

Coloca el archivo en:

```
src/main/resources/serviceAccountKey.json
```

> ⚠️ Este archivo **no debe subirse al repositorio**. Está incluido en `.gitignore`.

### 3. Configurar variables de entorno

Crea el archivo `src/main/resources/application-local.properties` con las variables descritas en la sección anterior, o expórtalas directamente en tu entorno.

### 4. Ejecutar la aplicación

```bash
# Con Maven Wrapper
./mvnw spring-boot:run -Dspring-boot.run.profiles=local

# O compilar y ejecutar el JAR
./mvnw clean package -DskipTests
java -jar target/backend-0.0.1-SNAPSHOT.jar --spring.profiles.active=local
```

La API quedará disponible en `http://localhost:8080`.

### 5. Ejecutar los tests

```bash
./mvnw test
```

---

## 🗂️ Estructura de carpetas

```
src/main/java/com/gelox/backend/
│
├── auth/                        # Filtros de seguridad y configuración de Spring Security
│   ├── FirebaseAuthFilter.java  # Intercepta y valida el token Firebase en cada request
│   ├── SecurityConfig.java      # Rutas públicas y protegidas, CORS, CSRF
│   └── UserDetailsServiceImpl.java
│
├── catalogo/                    # Repositorio y DTO específicos del catálogo público
│
├── config/                      # Configuración de beans (Firebase, propiedades del negocio)
│   ├── FirebaseConfig.java
│   └── NegocioProperties.java
│
├── controllers/                 # Capa de presentación — endpoints REST
│   ├── AuthController.java
│   ├── CierreCajaController.java
│   ├── ComercianteController.java
│   ├── DashboardController.java
│   ├── InventarioController.java
│   ├── LandingController.java
│   ├── PerfilController.java
│   ├── PlanillaController.java
│   ├── ProductoController.java
│   ├── ReporteDiarioController.java
│   ├── ReporteFinancieroController.java
│   ├── UsuarioController.java
│   └── VentaController.java
│
├── dto/                         # Objetos de transferencia de datos (request / response)
│
├── entities/                    # Entidades JPA y enumerados del dominio
│
├── exceptions/                  # Excepciones de dominio personalizadas
│
├── repositories/                # Interfaces Spring Data JPA
│
├── security/                    # RBAC: anotación @RequiereRol y aspecto AOP
│   ├── RequiereRol.java
│   └── RolVerificacionAspect.java
│
├── services/                    # Lógica de negocio
│   ├── AuthService.java
│   ├── CierreCajaService.java
│   ├── ComercianteService.java
│   ├── DashboardService.java
│   ├── EmailService.java
│   ├── InventarioService.java
│   ├── LandingService.java
│   ├── LiquidacionService.java
│   ├── PedidoProveedorService.java
│   ├── PlanillaService.java
│   ├── ProductoService.java
│   ├── ReporteDiarioService.java
│   ├── ReporteFinancieroService.java
│   ├── SupabaseStorageService.java
│   └── UsuarioService.java
│
└── ventas/
    └── rural/                   # Módulo de ventas rurales y clientes rurales
        ├── ClienteRuralController.java
        ├── VentaRuralController.java
        ├── VentaRuralService.java
        └── dto/
```

---

## 🗄️ Base de datos

- **Motor:** PostgreSQL hospedado en [Supabase](https://supabase.com)
- **Migraciones:** Flyway — ubicadas en `src/main/resources/db/migration/`
- **DDL automático:** desactivado (`spring.jpa.hibernate.ddl-auto=none`); todas las migraciones son explícitas

### Tablas principales

| Tabla | Entidad JPA | Descripción |
|---|---|---|
| `usuario` | `Usuario` | Usuarios del sistema (administradores, encargados) |
| `producto` | `Producto` | Catálogo de productos de la heladería |
| `venta` | `Venta` | Transacciones de venta (ventanilla o rural) |
| `item_venta` | `ItemVenta` | Líneas de producto dentro de una venta |
| `cierre_caja` | `CierreCaja` | Conciliaciones diarias de caja |
| `comerciante` | `Comerciante` | Distribuidores independientes |
| `cliente_rural` | `ClienteRural` | Clientes recurrentes para ventas rurales |
| `pedido_rural` | `PedidoRural` | Pedidos de clientes rurales |
| `pedido_proveedor` | `PedidoProveedor` | Órdenes de compra al proveedor Nutresa |
| `item_pedido_proveedor` | `ItemPedidoProveedor` | Líneas de un pedido al proveedor |
| `movimiento_inventario` | `MovimientoInventario` | Auditoría de todos los movimientos de stock |
| `perdida` | `Perdida` | Registro de mermas y pérdidas de producto |
| `evento_sistema` | `EventoSistema` | Log de eventos y auditoría de acciones |
| `planilla_comerciante` | `PlanillaComerciante` | Planillas de despacho y liquidación |
| `item_planilla` | `ItemPlanilla` | Ítems dentro de una planilla de comerciante |

### Enumerados principales

**`RolUsuario`**
```
ADMINISTRADOR · ENCARGADO_INVENTARIO · ENCARGADO_VENTAS
```

**`CanalVenta`**
```
VENTANILLA · RURAL
```

**`CategoriaProducto`**
```
PALETAS · CONOS · FAMILIARES · VASOS
```

**`TipoMovimiento`**
```
ENTRADA · SALIDA_VENTA · SALIDA_DESPACHO · PERDIDA · DEVOLUCION
```

**`MetodoPago`**
```
EFECTIVO · TRANSFERENCIA
```

---

## 👨‍💻 Equipo de desarrollo

| Nombre | Rol |
|--------|-----|
| Jesus Gabriel Torres Daza | Gerente del Proyecto, Líder de QA, Desarrollador Full Stack, Analista de Sistemas |
| Zharick Nicole Hernandez Arevalo | Líder Tecnológico, Coordinador Backend, Desarrollador Full Stack, Arquitecto de Software |
| Emerson Amir Vera Gonzalez | Coordinador Frontend, Desarrollador Full Stack, Diseñador UX/UI |
| Jose Luis Jimenez Bayona | Desarrollador Full Stack, Diseñador de Bases de Datos (DBA) |
| Daniela Garcia Peñaranda | Desarrollador Frontend, Diseñador de Bases de Datos (DBA) |
| Angie Nikol Ortiz Amaya | Desarrollador Frontend, Tester |
| Alejandro Ovallos Torrado | Desarrollador Backend, Tester |
| Israel Bulla Rey | Desarrollador Frontend, Tester |

---

