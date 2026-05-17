# Instrucciones: RF12, RF13, RF14 + Plan QA Iteración 1

## Contexto de BD (leer antes de escribir código)

```
Tablas relevantes:
- producto           → id, precio_costo, precio_venta
- venta              → id, canal (ENUM: 'VENTANILLA','RURAL'), total, fecha
- item_venta         → id, venta_id, producto_id, cantidad_unidades, cantidad_cajas, precio_unitario, subtotal
- pedido_proveedor   → id, fecha, estado
- item_pedido_proveedor → id, pedido_id, producto_id, cantidad_recibida, precio_unitario
- planilla           → id, comerciante_id, fecha, cerrada, total_ganancia, efectivo_recibido
- item_planilla      → (ver esquema — guarda despacho y devoluciones por producto/comerciante)

NOTA: canal_venta ENUM solo tiene 'VENTANILLA' y 'RURAL'.
Los ingresos de COMERCIANTES vienen de planilla.total_ganancia (no de tabla venta).
```

---

## 1. DTOs

### `PeriodoFiltroDTO.java`
```
package ...dto;

public record PeriodoFiltroDTO(
    LocalDate fechaInicio,
    LocalDate fechaFin
) {}
```

### `ReporteFinancieroDTO.java`
```
package ...dto;

public record ReporteFinancieroDTO(
    BigDecimal totalInversion,
    BigDecimal ingresosVentanilla,
    BigDecimal ingresosRural,
    BigDecimal ingresosComerciantes,
    BigDecimal ingresosTotales,
    BigDecimal utilidadNeta,
    BigDecimal margenGanancia   // porcentaje: utilidad/inversion*100, null si inversion=0
) {}
```

### `RentabilidadCanalDTO.java`
```
package ...dto;

public record RentabilidadCanalDTO(
    String canal,
    BigDecimal totalIngresos,
    BigDecimal totalCostos,
    BigDecimal margen           // porcentaje
) {}
```

### `ReporteRentabilidadDTO.java`
```
package ...dto;

public record ReporteRentabilidadDTO(
    List<RentabilidadCanalDTO> canales
) {}
```

### `PuntoGraficaDTO.java` (para RF13)
```
package ...dto;

public record PuntoGraficaDTO(
    String etiqueta,       // "Sem 1", "Sem 2", etc. o fecha
    BigDecimal inversion,
    BigDecimal ingresos
) {}
```

---

## 2. Repositorios / Queries

Crea `ReporteRepository.java` como `@Repository` con `EntityManager` o usa `@Query` en un nuevo repositorio. Usa **JPQL o SQL nativo** según convenga.

### Query inversión total (RF12)
```sql
SELECT COALESCE(SUM(ipp.precio_unitario * ipp.cantidad_recibida), 0)
FROM item_pedido_proveedor ipp
JOIN pedido_proveedor pp ON pp.id = ipp.pedido_id
WHERE pp.fecha BETWEEN :inicio AND :fin
  AND pp.estado = 'RECIBIDO'
```

### Query ingresos por canal venta (VENTANILLA + RURAL)
```sql
SELECT v.canal, COALESCE(SUM(v.total), 0)
FROM venta v
WHERE CAST(v.fecha AS date) BETWEEN :inicio AND :fin
GROUP BY v.canal
```

### Query ingresos comerciantes
```sql
SELECT COALESCE(SUM(p.total_ganancia), 0)
FROM planilla p
WHERE p.fecha BETWEEN :inicio AND :fin
  AND p.cerrada = true
```

### Query costos por canal (RF14) — VENTANILLA y RURAL
```sql
SELECT v.canal,
       COALESCE(SUM(iv.subtotal), 0)                                    AS ingresos,
       COALESCE(SUM((iv.cantidad_unidades + iv.cantidad_cajas) * pr.precio_costo), 0) AS costos
FROM venta v
JOIN item_venta iv ON iv.venta_id = v.id
JOIN producto pr   ON pr.id = iv.producto_id
WHERE CAST(v.fecha AS date) BETWEEN :inicio AND :fin
GROUP BY v.canal
```

### Query costos canal COMERCIANTES (RF14)
- Ingresos: `SUM(planilla.total_ganancia)` donde cerrada=true y fecha en período.
- Costos por comerciantes: hacer JOIN `item_planilla` → `producto` para obtener `precio_costo * cantidad_despachada`. Revisar estructura real de `item_planilla` antes de escribir.

---

## 3. `ReporteFinancieroService.java`

```java
@Service
@RequiredArgsConstructor
public class ReporteFinancieroService {

    private final ReporteRepository reporteRepository;

    public ReporteFinancieroDTO generarReporte(PeriodoFiltroDTO periodo) {
        BigDecimal inversion        = reporteRepository.getTotalInversion(periodo);
        BigDecimal ingVentanilla    = reporteRepository.getIngresosPorCanal("VENTANILLA", periodo);
        BigDecimal ingRural         = reporteRepository.getIngresosPorCanal("RURAL", periodo);
        BigDecimal ingComerciant    = reporteRepository.getIngresosComerciantesEnPeriodo(periodo);
        BigDecimal ingresosTotales  = ingVentanilla.add(ingRural).add(ingComerciant);
        BigDecimal utilidad         = ingresosTotales.subtract(inversion);
        BigDecimal margen           = inversion.compareTo(BigDecimal.ZERO) == 0
                                        ? null
                                        : utilidad.divide(inversion, 4, RoundingMode.HALF_UP)
                                                  .multiply(BigDecimal.valueOf(100));

        return new ReporteFinancieroDTO(inversion, ingVentanilla, ingRural,
                ingComerciant, ingresosTotales, utilidad, margen);
    }

    public List<PuntoGraficaDTO> getGraficaInversionVsIngresos(PeriodoFiltroDTO periodo) {
        // Agrupar por semana dentro del período
        // Retornar lista de PuntoGraficaDTO con etiqueta="Sem N", inversion, ingresos
        // Delegar queries al repositorio
    }

    public ReporteRentabilidadDTO getRentabilidadPorCanal(PeriodoFiltroDTO periodo) {
        // Construir lista con VENTANILLA, RURAL, COMERCIANTES
        // Para cada canal: calcular margen = (ingresos - costos) / ingresos * 100
        // Si ingresos = 0 → margen = 0, mostrar fila con ceros (RF14 flujo alterno)
    }
}
```

---

## 4. `ReporteFinancieroController.java`

```java
@RestController
@RequestMapping("/api/reportes")
@RequiredArgsConstructor
public class ReporteFinancieroController {

    private final ReporteFinancieroService service;

    /** RF12 */
    @GetMapping("/financiero")
    public ResponseEntity<ReporteFinancieroDTO> getReporteFinanciero(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaInicio,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaFin) {

        if (fechaInicio.isAfter(fechaFin))
            return ResponseEntity.badRequest().build();

        var resultado = service.generarReporte(new PeriodoFiltroDTO(fechaInicio, fechaFin));
        return ResponseEntity.ok(resultado);
    }

    /** RF13 — gráfica inversión vs ingresos */
    @GetMapping("/grafica-inversion-ingresos")
    public ResponseEntity<List<PuntoGraficaDTO>> getGrafica(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaInicio,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaFin) {

        var puntos = service.getGraficaInversionVsIngresos(new PeriodoFiltroDTO(fechaInicio, fechaFin));
        return ResponseEntity.ok(puntos);
    }

    /** RF14 — tabla rentabilidad por canal */
    @GetMapping("/por-canal")
    public ResponseEntity<ReporteRentabilidadDTO> getRentabilidadPorCanal(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaInicio,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaFin) {

        var resultado = service.getRentabilidadPorCanal(new PeriodoFiltroDTO(fechaInicio, fechaFin));
        return ResponseEntity.ok(resultado);
    }
}
```

**Seguridad:** Asegurar que estos endpoints estén protegidos con rol `ADMINISTRADOR` igual que el resto de endpoints de reportes del proyecto.

---

## 5. Verificación tras implementar

Antes de marcar como hecho, ejecutar:

```bash
# compilar
./mvnw compile

# levantar y probar manualmente con fechas que tengan datos reales en Supabase:
curl "http://localhost:8080/api/reportes/financiero?fechaInicio=2026-01-01&fechaFin=2026-01-31" \
  -H "Authorization: Bearer <token>"

curl "http://localhost:8080/api/reportes/por-canal?fechaInicio=2026-01-01&fechaFin=2026-01-31" \
  -H "Authorization: Bearer <token>"
```

Verificar que:
- Sin datos en el período → todos los campos en 0 (no NullPointerException).
- Margen nulo cuando inversión = 0.
- Canal COMERCIANTES aparece siempre en la tabla (RF14 flujo alterno: con ceros si no hay datos).

---

## 6. `QA_Plan_Iteracion1.md`

Crear este archivo en la raíz del proyecto con el siguiente contenido:

```markdown
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
| TC12 | Sin datos en el período               | Sin ventas ni pedidos en las fechas       | GET /api/reportes/financiero?fechaInicio=X&fechaFin=Y              | 200 OK, todos los campos en 0, mensaje "Sin registros"                |
| TC13 | Período inválido (inicio > fin)       | —                                         | GET /api/reportes/financiero?fechaInicio=2026-02&fechaFin=2026-01  | 400 Bad Request                                                       |
| TC14 | Acceso sin rol ADMINISTRADOR          | Usuario con rol ENCARGADO_VENTAS          | GET /api/reportes/financiero con token válido no admin             | 403 Forbidden                                                         |

### RF13 — Gráfica inversión vs ingresos
| ID   | Caso de prueba                        | Precondición                              | Pasos                                                                  | Resultado esperado                                                  |
|------|---------------------------------------|-------------------------------------------|------------------------------------------------------------------------|---------------------------------------------------------------------|
| TC15 | Gráfica con datos suficientes         | Ventas en varias semanas del período      | GET /api/reportes/grafica-inversion-ingresos?fechaInicio=X&fechaFin=Y | 200 OK, lista con puntos por semana, inversion e ingresos > 0       |
| TC16 | Datos insuficientes para gráfica      | Sin ventas ni pedidos                     | GET /api/reportes/grafica-inversion-ingresos                           | 200 OK, lista vacía o puntos en cero                                |

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
```
