-- ============================================================================
-- V10__catalogo_credito_exportacion.sql
-- RF63-RF67 — Catálogo multimarca, crédito y liquidación, exportación de pedidos
-- (FP-15 §2.14.1, §2.14.2 y §2.14.3). Depende de V9 (plan_suscripcion, auditoria,
-- archivo_almacenado y distribuidora.plan_id), que no se toca aquí.
-- Toda tabla nueva lleva tenant_id UUID NOT NULL con FK a distribuidora(id) e índice que
-- empieza por tenant_id (V5); los estados son VARCHAR con CHECK (V6). plantilla_columna
-- hereda el tenant de su plantilla y no lleva tenant_id.
-- ANTES DE DESPLEGAR: respaldo de la base en Supabase (esta migración toca producto,
-- pedido_proveedor y planilla_comerciante). Requiere PostgreSQL 15 (NULLS NOT DISTINCT).
-- ============================================================================

-- ---------------------------------------------------------------------------
-- 1. Tablas nuevas
-- ---------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS proveedor (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           UUID          NOT NULL REFERENCES distribuidora(id),
    nombre              VARCHAR(120)  NOT NULL,
    nit                 VARCHAR(20),
    contacto_nombre     VARCHAR(120),
    telefono            VARCHAR(20),
    correo              VARCHAR(150),
    tiempo_entrega_dias INTEGER       NOT NULL DEFAULT 0
                        CONSTRAINT ck_proveedor_tiempo_entrega CHECK (tiempo_entrega_dias >= 0),
    dias_habiles        BOOLEAN       NOT NULL DEFAULT TRUE,
    monto_minimo_pedido NUMERIC(15,2) NOT NULL DEFAULT 0
                        CONSTRAINT ck_proveedor_monto_minimo CHECK (monto_minimo_pedido >= 0),
    activo              BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_proveedor_tenant_nit UNIQUE (tenant_id, nit)
);
CREATE INDEX IF NOT EXISTS idx_proveedor_tenant_activo ON proveedor (tenant_id, activo);

CREATE TABLE IF NOT EXISTS categoria (
    id        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID         NOT NULL REFERENCES distribuidora(id),
    nombre    VARCHAR(80)  NOT NULL,
    orden     INTEGER      NOT NULL DEFAULT 0,
    activo    BOOLEAN      NOT NULL DEFAULT TRUE,
    CONSTRAINT uq_categoria_tenant_nombre UNIQUE (tenant_id, nombre)
);
CREATE INDEX IF NOT EXISTS idx_categoria_tenant_orden ON categoria (tenant_id, orden);

CREATE TABLE IF NOT EXISTS unidad_medida (
    id        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID        NOT NULL REFERENCES distribuidora(id),
    codigo    VARCHAR(10) NOT NULL,
    nombre    VARCHAR(50) NOT NULL,
    CONSTRAINT uq_unidad_medida_tenant_codigo UNIQUE (tenant_id, codigo)
);

CREATE TABLE IF NOT EXISTS factor_conversion (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id         UUID          NOT NULL REFERENCES distribuidora(id),
    producto_id       UUID          NOT NULL REFERENCES producto(id) ON DELETE CASCADE,
    unidad_origen_id  UUID          NOT NULL REFERENCES unidad_medida(id),
    unidad_destino_id UUID          NOT NULL REFERENCES unidad_medida(id),
    factor            NUMERIC(12,4) NOT NULL CONSTRAINT ck_factor_conversion_factor CHECK (factor > 0),
    CONSTRAINT ck_factor_conversion_unidades CHECK (unidad_origen_id <> unidad_destino_id),
    CONSTRAINT uq_factor_conversion UNIQUE (producto_id, unidad_origen_id, unidad_destino_id)
);
CREATE INDEX IF NOT EXISTS idx_factor_conversion_tenant_producto ON factor_conversion (tenant_id, producto_id);

CREATE TABLE IF NOT EXISTS codigo_homologacion (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id    UUID         NOT NULL REFERENCES distribuidora(id),
    producto_id  UUID         NOT NULL REFERENCES producto(id) ON DELETE CASCADE,
    proveedor_id UUID         NOT NULL REFERENCES proveedor(id),
    codigo       VARCHAR(60)  NOT NULL,
    CONSTRAINT uq_codigo_homologacion_proveedor_codigo   UNIQUE (proveedor_id, codigo),
    CONSTRAINT uq_codigo_homologacion_proveedor_producto UNIQUE (proveedor_id, producto_id)
);
CREATE INDEX IF NOT EXISTS idx_codigo_homologacion_tenant_producto ON codigo_homologacion (tenant_id, producto_id);

CREATE TABLE IF NOT EXISTS plantilla_exportacion (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           UUID         NOT NULL REFERENCES distribuidora(id),
    proveedor_id        UUID         NOT NULL REFERENCES proveedor(id),
    nombre              VARCHAR(120) NOT NULL,
    modo                VARCHAR(20)  NOT NULL
                        CONSTRAINT ck_plantilla_exportacion_modo CHECK (modo IN ('RELLENAR_CATALOGO', 'GENERAR_FILAS')),
    archivo_base_url    TEXT,
    fila_encabezado     INTEGER      NOT NULL DEFAULT 1
                        CONSTRAINT ck_plantilla_exportacion_fila CHECK (fila_encabezado >= 1),
    encabezado_codigo   VARCHAR(80),
    encabezado_cantidad VARCHAR(80),
    encabezado_unidad   VARCHAR(80),
    updated_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_plantilla_exportacion_proveedor UNIQUE (proveedor_id)
);
CREATE INDEX IF NOT EXISTS idx_plantilla_exportacion_tenant ON plantilla_exportacion (tenant_id, proveedor_id);

CREATE TABLE IF NOT EXISTS plantilla_columna (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    plantilla_id UUID         NOT NULL REFERENCES plantilla_exportacion(id) ON DELETE CASCADE,
    orden        INTEGER      NOT NULL,
    campo        VARCHAR(30)  NOT NULL
                 CONSTRAINT ck_plantilla_columna_campo CHECK (campo IN (
                     'CODIGO_PROVEEDOR', 'SKU', 'NOMBRE_PRODUCTO', 'UNIDAD', 'CANTIDAD',
                     'CANTIDAD_CAJAS', 'CANTIDAD_UNIDADES', 'PRECIO_UNITARIO', 'SUBTOTAL',
                     'FECHA_PEDIDO', 'TEXTO_FIJO')),
    encabezado   VARCHAR(80)  NOT NULL,
    valor_fijo   VARCHAR(120),
    CONSTRAINT uq_plantilla_columna_orden UNIQUE (plantilla_id, orden)
);

CREATE TABLE IF NOT EXISTS politica_credito (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id            UUID          NOT NULL REFERENCES distribuidora(id),
    comerciante_id       UUID          REFERENCES comerciante(id),   -- NULL = política por defecto de la distribuidora
    cupo                 NUMERIC(15,2) NOT NULL DEFAULT 0 CONSTRAINT ck_politica_credito_cupo CHECK (cupo >= 0),
    plazo_dias           INTEGER       NOT NULL DEFAULT 0 CONSTRAINT ck_politica_credito_plazo CHECK (plazo_dias >= 0),
    politica_vencimiento VARCHAR(12)   NOT NULL DEFAULT 'SOLO_ALERTA'
                         CONSTRAINT ck_politica_credito_vencimiento CHECK (politica_vencimiento IN ('BLOQUEAR', 'SOLO_ALERTA')),
    activo               BOOLEAN       NOT NULL DEFAULT TRUE,
    updated_at           TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    actualizado_por      UUID          REFERENCES usuario(id),
    CONSTRAINT uq_politica_credito_tenant_comerciante UNIQUE NULLS NOT DISTINCT (tenant_id, comerciante_id)
);

CREATE TABLE IF NOT EXISTS abono_comerciante (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id      UUID          NOT NULL REFERENCES distribuidora(id),
    comerciante_id UUID          NOT NULL REFERENCES comerciante(id),
    planilla_id    UUID          REFERENCES planilla_comerciante(id),   -- NULL = se aplica a la más antigua
    fecha          DATE          NOT NULL DEFAULT CURRENT_DATE,
    valor          NUMERIC(15,2) NOT NULL CONSTRAINT ck_abono_comerciante_valor CHECK (valor > 0),
    usuario_id     UUID          NOT NULL REFERENCES usuario(id),       -- quien recibe el abono
    observacion    TEXT,
    created_at     TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_abono_comerciante_tenant_comerciante_fecha
    ON abono_comerciante (tenant_id, comerciante_id, fecha);

CREATE TABLE IF NOT EXISTS esquema_liquidacion (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID          NOT NULL REFERENCES distribuidora(id),
    tipo            VARCHAR(12)   NOT NULL
                    CONSTRAINT ck_esquema_liquidacion_tipo CHECK (tipo IN ('MARGEN', 'COMISION', 'TARIFA_FIJA')),
    valor           NUMERIC(12,2),
    vigente_desde   TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    actualizado_por UUID          REFERENCES usuario(id),
    CONSTRAINT uq_esquema_liquidacion_tenant UNIQUE (tenant_id),
    CONSTRAINT ck_esquema_liquidacion_valor CHECK (
        tipo = 'MARGEN'
        OR (tipo = 'COMISION'   AND valor IS NOT NULL AND valor > 0 AND valor <= 100)
        OR (tipo = 'TARIFA_FIJA' AND valor IS NOT NULL AND valor >= 0))
);

-- ---------------------------------------------------------------------------
-- 2. Cambios en tablas existentes (columnas nuevas, aún sin NOT NULL)
-- ---------------------------------------------------------------------------

ALTER TABLE producto ADD COLUMN IF NOT EXISTS sku            VARCHAR(40);
ALTER TABLE producto ADD COLUMN IF NOT EXISTS marca          VARCHAR(80);
ALTER TABLE producto ADD COLUMN IF NOT EXISTS proveedor_id   UUID REFERENCES proveedor(id);
ALTER TABLE producto ADD COLUMN IF NOT EXISTS categoria_id   UUID REFERENCES categoria(id);
ALTER TABLE producto ADD COLUMN IF NOT EXISTS unidad_base_id UUID REFERENCES unidad_medida(id);

ALTER TABLE pedido_proveedor ADD COLUMN IF NOT EXISTS proveedor_id UUID REFERENCES proveedor(id);

ALTER TABLE planilla_comerciante ADD COLUMN IF NOT EXISTS ganancia_comerciante NUMERIC(15,2);
ALTER TABLE planilla_comerciante ADD COLUMN IF NOT EXISTS esquema_aplicado     VARCHAR(12);
ALTER TABLE planilla_comerciante ADD COLUMN IF NOT EXISTS valor_esquema        NUMERIC(12,2);
ALTER TABLE planilla_comerciante ADD COLUMN IF NOT EXISTS fecha_limite_pago    DATE;

ALTER TABLE planilla_comerciante DROP CONSTRAINT IF EXISTS ck_planilla_esquema_aplicado;
ALTER TABLE planilla_comerciante ADD CONSTRAINT ck_planilla_esquema_aplicado
    CHECK (esquema_aplicado IS NULL OR esquema_aplicado IN ('MARGEN', 'COMISION', 'TARIFA_FIJA'));

ALTER TABLE item_planilla ADD COLUMN IF NOT EXISTS precio_publico       NUMERIC(12,2);
ALTER TABLE item_planilla ADD COLUMN IF NOT EXISTS ganancia_comerciante NUMERIC(12,2);

-- ---------------------------------------------------------------------------
-- 3. Datos existentes (FP-15 §2.14.3). Idempotente: cada INSERT comprueba antes.
-- ---------------------------------------------------------------------------

-- 3.1 Proveedor «Nutresa» por distribuidora (C2, C3), asignado a sus productos y pedidos
INSERT INTO proveedor (tenant_id, nombre, tiempo_entrega_dias, dias_habiles, monto_minimo_pedido)
SELECT d.id, 'Nutresa', 1, TRUE, 0
  FROM distribuidora d
 WHERE NOT EXISTS (SELECT 1 FROM proveedor p WHERE p.tenant_id = d.id AND p.nombre = 'Nutresa');

UPDATE producto pr
   SET proveedor_id = p.id
  FROM proveedor p
 WHERE p.tenant_id = pr.tenant_id AND p.nombre = 'Nutresa' AND pr.proveedor_id IS NULL;

UPDATE pedido_proveedor pe
   SET proveedor_id = p.id
  FROM proveedor p
 WHERE p.tenant_id = pe.tenant_id AND p.nombre = 'Nutresa' AND pe.proveedor_id IS NULL;

-- 3.2 Categorías: una por valor del enum CategoriaProducto, en ese orden
INSERT INTO categoria (tenant_id, nombre, orden)
SELECT d.id, c.nombre, c.orden
  FROM distribuidora d
 CROSS JOIN (VALUES ('PALETAS', 1), ('CONOS', 2), ('FAMILIARES', 3), ('VASOS', 4)) AS c(nombre, orden)
ON CONFLICT (tenant_id, nombre) DO NOTHING;

UPDATE producto pr
   SET categoria_id = c.id
  FROM categoria c
 WHERE c.tenant_id = pr.tenant_id AND c.nombre = pr.categoria AND pr.categoria_id IS NULL;

-- 3.3 Unidades UN, CJ y BOL por distribuidora; unidad_base_id = UN
-- (unidades_por_caja sigue siendo el factor CJ → UN)
INSERT INTO unidad_medida (tenant_id, codigo, nombre)
SELECT d.id, u.codigo, u.nombre
  FROM distribuidora d
 CROSS JOIN (VALUES ('UN', 'Unidad'), ('CJ', 'Caja'), ('BOL', 'Bolsa')) AS u(codigo, nombre)
ON CONFLICT (tenant_id, codigo) DO NOTHING;

UPDATE producto pr
   SET unidad_base_id = u.id
  FROM unidad_medida u
 WHERE u.tenant_id = pr.tenant_id AND u.codigo = 'UN' AND pr.unidad_base_id IS NULL;

-- 3.4 sku = codigo_tecnico, y su código de homologación con Nutresa (C5)
UPDATE producto SET sku = codigo_tecnico WHERE sku IS NULL;

ALTER TABLE producto DROP CONSTRAINT IF EXISTS uq_producto_tenant_sku;
ALTER TABLE producto ADD CONSTRAINT uq_producto_tenant_sku UNIQUE (tenant_id, sku);

INSERT INTO codigo_homologacion (tenant_id, producto_id, proveedor_id, codigo)
SELECT pr.tenant_id, pr.id, p.id, pr.codigo_tecnico
  FROM producto pr
  JOIN proveedor p ON p.tenant_id = pr.tenant_id AND p.nombre = 'Nutresa'
 WHERE pr.codigo_tecnico IS NOT NULL
ON CONFLICT (proveedor_id, producto_id) DO NOTHING;

-- 3.5 Plantilla de exportación de Nutresa (usa templates/catalogo.xlsx: archivo_base_url nulo)
INSERT INTO plantilla_exportacion (tenant_id, proveedor_id, nombre, modo, fila_encabezado,
                                   encabezado_codigo, encabezado_cantidad, encabezado_unidad)
SELECT p.tenant_id, p.id, 'Pedido Nutresa', 'RELLENAR_CATALOGO', 4,
       'Producto/SKU', 'Cantidad', 'Unidad de Medida'
  FROM proveedor p
 WHERE p.nombre = 'Nutresa'
ON CONFLICT (proveedor_id) DO NOTHING;

-- 3.6 Crédito por defecto (cupo $50.000, plazo 0, SOLO_ALERTA: A2 a A4) y esquema MARGEN
INSERT INTO politica_credito (tenant_id, comerciante_id, cupo, plazo_dias, politica_vencimiento)
SELECT d.id, NULL, 50000, 0, 'SOLO_ALERTA'
  FROM distribuidora d
ON CONFLICT ON CONSTRAINT uq_politica_credito_tenant_comerciante DO NOTHING;

INSERT INTO esquema_liquidacion (tenant_id, tipo)
SELECT d.id, 'MARGEN'
  FROM distribuidora d
ON CONFLICT (tenant_id) DO NOTHING;

-- 3.7 Planillas cerradas: esquema MARGEN y, donde no hay efectivo recibido, se toma el total
-- (hasta hoy todas se pagaban al liquidar: A1), así ningún comerciante amanece con deuda.
UPDATE planilla_comerciante
   SET esquema_aplicado = 'MARGEN'
 WHERE cerrada = TRUE AND esquema_aplicado IS NULL;

UPDATE planilla_comerciante
   SET efectivo_recibido = total_ganancia
 WHERE cerrada = TRUE AND efectivo_recibido IS NULL;

-- ---------------------------------------------------------------------------
-- 4. Restricciones NOT NULL, con los datos ya llenos
-- ---------------------------------------------------------------------------

DO $$
DECLARE
    sin_categoria BIGINT;
BEGIN
    SELECT COUNT(*) INTO sin_categoria FROM producto WHERE categoria_id IS NULL;
    IF sin_categoria > 0 THEN
        RAISE EXCEPTION 'V10: % producto(s) sin categoría válida (producto.categoria nula o fuera del enum); asignarles una antes de migrar', sin_categoria;
    END IF;
END $$;

ALTER TABLE producto         ALTER COLUMN categoria_id SET NOT NULL;
ALTER TABLE pedido_proveedor ALTER COLUMN proveedor_id SET NOT NULL;

-- La columna producto.categoria (texto) se conserva en esta migración porque la entidad actual
-- la mapea; categoria_id la reemplaza. Se retira su CHECK para poder crear categorías nuevas.
ALTER TABLE producto DROP CONSTRAINT IF EXISTS producto_categoria_check;

-- ---------------------------------------------------------------------------
-- 5. Índices de las columnas nuevas
-- ---------------------------------------------------------------------------
CREATE INDEX IF NOT EXISTS idx_producto_tenant_categoria ON producto (tenant_id, categoria_id);
CREATE INDEX IF NOT EXISTS idx_producto_tenant_proveedor ON producto (tenant_id, proveedor_id);
CREATE INDEX IF NOT EXISTS idx_pedido_proveedor_tenant_proveedor ON pedido_proveedor (tenant_id, proveedor_id);
CREATE INDEX IF NOT EXISTS idx_planilla_comerciante_tenant_limite
    ON planilla_comerciante (tenant_id, fecha_limite_pago);
