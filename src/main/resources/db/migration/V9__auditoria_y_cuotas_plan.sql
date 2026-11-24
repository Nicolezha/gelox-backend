-- ============================================================================
-- V9__auditoria_y_cuotas_plan.sql
-- RF70/RF71 — Planes de suscripción con cuotas, almacenamiento por distribuidora
-- y log de auditoría (FP-15 §2.14.1 y §2.14.3).
-- ============================================================================

-- 1. Planes de suscripción. Un máximo NULL significa "sin límite".
CREATE TABLE IF NOT EXISTS plan_suscripcion (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    nombre                VARCHAR(30)   NOT NULL UNIQUE,
    descripcion           TEXT,
    precio_mensual        NUMERIC(12,2) NOT NULL DEFAULT 0
                          CONSTRAINT ck_plan_precio_mensual CHECK (precio_mensual >= 0),
    max_usuarios          INTEGER CONSTRAINT ck_plan_max_usuarios       CHECK (max_usuarios IS NULL OR max_usuarios >= 0),
    max_productos         INTEGER CONSTRAINT ck_plan_max_productos      CHECK (max_productos IS NULL OR max_productos >= 0),
    max_comerciantes      INTEGER CONSTRAINT ck_plan_max_comerciantes   CHECK (max_comerciantes IS NULL OR max_comerciantes >= 0),
    max_almacenamiento_mb INTEGER CONSTRAINT ck_plan_max_almacenamiento CHECK (max_almacenamiento_mb IS NULL OR max_almacenamiento_mb >= 0),
    max_llamadas_ia_mes   INTEGER CONSTRAINT ck_plan_max_llamadas_ia    CHECK (max_llamadas_ia_mes IS NULL OR max_llamadas_ia_mes >= 0),
    activo                BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at            TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at            TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- §2.14.3 — un plan por cada texto de distribuidora.plan (hoy PILOTO y BASICO) más los del
-- formulario de alta (BASICO, ESTANDAR, PREMIUM), todos sin límites: nadie queda bloqueado.
INSERT INTO plan_suscripcion (nombre)
SELECT DISTINCT UPPER(TRIM(d.plan)) FROM distribuidora d
ON CONFLICT (nombre) DO NOTHING;

INSERT INTO plan_suscripcion (nombre, descripcion) VALUES
    ('BASICO',   'Plan de entrada'),
    ('ESTANDAR', 'Plan intermedio'),
    ('PREMIUM',  'Plan completo')
ON CONFLICT (nombre) DO NOTHING;

-- 2. distribuidora.plan_id. El texto distribuidora.plan se conserva por compatibilidad.
ALTER TABLE distribuidora ADD COLUMN IF NOT EXISTS plan_id UUID REFERENCES plan_suscripcion(id);

UPDATE distribuidora d
   SET plan_id = p.id
  FROM plan_suscripcion p
 WHERE d.plan_id IS NULL
   AND p.nombre = UPPER(TRIM(d.plan));

ALTER TABLE distribuidora ALTER COLUMN plan_id SET NOT NULL;
CREATE INDEX IF NOT EXISTS idx_distribuidora_plan ON distribuidora (plan_id);

-- 3. Archivos subidos a Supabase Storage, para medir el almacenamiento por distribuidora
-- (las carpetas del bucket son por tipo: productos, comerciantes, usuarios…, no por distribuidora).
CREATE TABLE IF NOT EXISTS archivo_almacenado (
    id         UUID PRIMARY KEY,
    tenant_id  UUID         NOT NULL REFERENCES distribuidora(id),
    carpeta    VARCHAR(120) NOT NULL,
    url        TEXT         NOT NULL,
    bytes      BIGINT       NOT NULL CONSTRAINT ck_archivo_bytes CHECK (bytes >= 0),
    created_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_archivo_almacenado_tenant ON archivo_almacenado (tenant_id);

-- 4. Log de auditoría (RF71). tenant_id es NULL para el Superadministrador;
-- usuario_id es NULL cuando no hay usuario autenticado (p. ej. token inválido).
CREATE TABLE IF NOT EXISTS auditoria (
    id         UUID PRIMARY KEY,
    fecha_hora TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ip         VARCHAR(45),
    tenant_id  UUID REFERENCES distribuidora(id),
    usuario_id UUID REFERENCES usuario(id),
    rol        VARCHAR(30),
    accion     VARCHAR(60) NOT NULL,
    entidad    VARCHAR(60),
    entidad_id UUID,
    detalle    TEXT        -- JSON en texto
);

CREATE INDEX IF NOT EXISTS idx_auditoria_tenant_fecha ON auditoria (tenant_id, fecha_hora DESC);
CREATE INDEX IF NOT EXISTS idx_auditoria_accion_fecha ON auditoria (accion, fecha_hora DESC);
